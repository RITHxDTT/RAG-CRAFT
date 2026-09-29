from app.models import User, OrganizationMember
from app.core.security import create_token, COOKIE_NAME


def test_same_organization_does_not_grant_other_owners_resources(logged_in, db, admin):
    bot = logged_in.post('/api/chatbots', json={'name': 'Private'}).json()
    org_id = logged_in.get('/api/auth/me').json()['organization_id']
    from uuid import UUID
    user = User(email='second@example.com', password_hash='unused', role='USER')
    db.add(user)
    db.flush()
    db.add(OrganizationMember(user_id=user.id, organization_id=UUID(org_id), role='MEMBER'))
    db.commit()
    logged_in.cookies.clear()
    logged_in.cookies.set(COOKIE_NAME, create_token(user.id, UUID(org_id), user.token_version))
    assert logged_in.get('/api/chatbots').json() == []
    assert logged_in.get('/api/dashboard').json()['total_chatbots'] == 0
    assert logged_in.get('/api/knowledge/recent').json() == []
    for suffix in ['', '/documents', '/conversations']:
        assert logged_in.get(f"/api/chatbots/{bot['id']}{suffix}").status_code == 403
    assert logged_in.patch(f"/api/chatbots/{bot['id']}", json={'name':'Stolen'}).status_code == 403


def test_foreign_document_conversation_and_integration_rejected(logged_in,db):
    from conftest import PASSWORD
    from app.schemas.auth import AdminCreate
    from app.services.auth_service import create_admin
    c=logged_in
    bot=c.post('/api/chatbots',json={'name':'Private'}).json()['id']
    base=f'/api/chatbots/{bot}'
    doc=c.post(base+'/documents',files={'file':('policy.txt',b'Private information','text/plain')}).json()['id']
    conversation=c.post(base+'/ask',json={'question':'Hello'}).json()['conversation_id']
    integration=c.post(base+'/channels',json={'channel':'WEB_WIDGET'}).json()['id']
    create_admin(db,AdminCreate(email='outside@example.com',password=PASSWORD))
    c.post('/api/auth/login',json={'email':'outside@example.com','password':PASSWORD})
    for suffix in (f'/documents/{doc}',f'/documents/{doc}/download',f'/documents/{doc}/availability',f'/conversations/{conversation}'):
        assert c.get(base+suffix).status_code==404
    assert c.delete(base+'/conversations/'+conversation).status_code==404
    assert c.patch(base+'/channels/'+integration,json={'enabled':False}).status_code==404
