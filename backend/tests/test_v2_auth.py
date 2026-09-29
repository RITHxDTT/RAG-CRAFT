from app.models import User
from sqlalchemy import select
from app.core.config import get_settings
from app.services import password_service

DATA = dict(full_name='New User', email='new@example.com', password='safe-password-123', confirm_password='safe-password-123')


def test_registration_profile_and_role(client):
    assert client.post('/api/auth/register', json={**DATA, 'role':'ADMIN'}).status_code == 422
    response = client.post('/api/auth/register', json=DATA)
    assert response.status_code == 201
    assert response.json()['role'] == 'USER'
    assert client.get('/api/auth/me').json()['full_name'] == 'New User'
    assert client.patch('/api/auth/me', json={'full_name':'Updated'}).json()['full_name'] == 'Updated'
    assert client.post('/api/auth/register', json=DATA).status_code == 409
    assert client.post('/api/chatbots', json={'name':'My bot'}).status_code == 201
    assert client.post('/api/auth/logout').status_code == 204
    assert client.post('/api/auth/login', json={k:DATA[k] for k in ('email','password')}).status_code == 200


def test_reset_is_single_use_and_revokes_session(client, db, monkeypatch):
    client.post('/api/auth/register', json=DATA)
    sent = []
    monkeypatch.setattr(get_settings(), 'smtp_host', 'test')
    monkeypatch.setattr(password_service, 'send_reset', lambda email, token: sent.append(token))
    assert client.post('/api/auth/forgot-password', json={'email':DATA['email']}).status_code == 200
    payload = dict(token=sent[0], password='new-password-1234', confirm_password='new-password-1234')
    assert client.post('/api/auth/reset-password', json=payload).status_code == 204
    assert client.get('/api/auth/me').status_code == 401
    assert client.post('/api/auth/reset-password', json=payload).status_code == 400
    user = db.scalar(select(User).where(User.email == DATA['email']))
    user.is_active = False
    db.commit()
    assert client.post('/api/auth/login', json={'email':DATA['email'], 'password':payload['password']}).status_code == 401


def test_password_whitespace_preserved(client):
    data={**DATA,'password':'  safe-password-123  ','confirm_password':'  safe-password-123  '}
    assert client.post('/api/auth/register',json=data).status_code==201
    client.post('/api/auth/logout')
    assert client.post('/api/auth/login',json={'email':data['email'],'password':data['password']}).status_code==200
    assert client.post('/api/auth/login',json={'email':data['email'],'password':data['password'].strip()}).status_code==401
