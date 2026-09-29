from test_v2_auth import DATA
from conftest import PASSWORD

def test_admin_aggregate_and_user_controls(logged_in):
    c=logged_in
    user=c.post('/api/auth/register',json=DATA).json()
    bot=c.post('/api/chatbots',json={'name':'Owned'}).json()
    c.post(f"/api/chatbots/{bot['id']}/ask",json={'question':'Hi'})
    usage=c.get('/api/analytics').json()
    assert usage['total_messages']==2 and usage['messages_by_channel']['PLAYGROUND']==2
    assert c.get('/api/admin/users').status_code==403
    c.post('/api/auth/login',json={'email':'admin@example.com','password':PASSWORD})
    assert c.get('/api/analytics').json()['total_chatbots']==0
    assert c.get('/api/admin/analytics').json()['total_chatbots']==1
    response=c.get('/api/admin/users?search=new')
    assert len(response.json())==1 and response.json()[0]['chatbot_count']==1
    assert 'password' not in response.text
    assert c.patch('/api/admin/users/'+user['id'],json={'is_active':False}).status_code==200
    assert c.post('/api/auth/login',json={'email':DATA['email'],'password':DATA['password']}).status_code==401
