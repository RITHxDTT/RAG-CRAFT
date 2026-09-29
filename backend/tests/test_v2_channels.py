import pytest
from app.services import chat_service
from app.schemas.rag import RagAnswer
from test_v2_auth import DATA

@pytest.mark.parametrize('channel,kind',[('PUBLIC_LINK','share'),('WEB_WIDGET','widget')])
def test_public_channel_lifecycle_and_guest_isolation(logged_in,monkeypatch,channel,kind):
    c=logged_in
    bot=c.post('/api/chatbots',json={'name':'Published','starter_questions':['Help?']}).json()
    base=f"/api/chatbots/{bot['id']}/channels"
    row=c.post(base,json={'channel':channel}).json()
    path=f"/api/public/{kind}/{row['public_id']}"
    seen=[]
    def answer(db,bot,question,history):
        seen.append(str(bot.id)); return RagAnswer(answer='Grounded answer',sources=[])
    monkeypatch.setattr(chat_service.pipeline,'answer',answer)
    assert c.get(path).json()['starter_questions']==['Help?']
    first=c.post(path+'/ask',json={'question':'Hello'}).json()
    assert seen==[bot['id']]
    assert c.post(path+'/ask',json={'question':'Steal','conversation_id':first['conversation_id']}).status_code==404
    assert c.post(path+'/ask',json={'question':'Continue','conversation_id':first['conversation_id'],'session_token':first['session_token']}).status_code==200
    assert c.get(f"/api/chatbots/{bot['id']}/conversations").json()[0]['channel']==channel
    assert c.post(f"/api/chatbots/{bot['id']}/ask",json={'question':'Hijack','conversation_id':first['conversation_id']}).status_code==403
    c.patch(base+'/'+row['id'],json={'enabled':False})
    assert c.get(path).status_code==404
    c.patch(base+'/'+row['id'],json={'enabled':True})
    replacement=c.post(base+'/'+row['id']+'/regenerate').json()
    assert replacement['public_id']!=row['public_id']
    assert c.get(path).status_code==404
    assert c.get(f"/api/public/{kind}/{replacement['public_id']}").status_code==200
    assert c.get(f'/api/public/{kind}/invalid').status_code==404
    c.post('/api/auth/register',json=DATA)
    assert c.patch(base+'/'+row['id'],json={'enabled':False}).status_code in (403,404)


def test_disabled_owner_and_chatbot_revoke_public_access(logged_in,db,admin):
    c=logged_in
    bot=c.post('/api/chatbots',json={'name':'Published'}).json()
    base=f"/api/chatbots/{bot['id']}"
    row=c.post(base+'/channels',json={'channel':'PUBLIC_LINK'}).json()
    path='/api/public/share/'+row['public_id']
    assert c.get(path).status_code==200
    c.patch(base,json={'status':'INACTIVE'})
    assert c.post(path+'/ask',json={'question':'Hello'}).status_code==404
    c.patch(base,json={'status':'ACTIVE'})
    admin.is_active=False
    db.commit()
    assert c.get(path).status_code==404


def test_guest_conversation_cannot_cross_channel_or_chatbot(logged_in):
    c=logged_in
    bot=c.post('/api/chatbots',json={'name':'One'}).json()['id']
    other=c.post('/api/chatbots',json={'name':'Two'}).json()['id']
    share=c.post(f'/api/chatbots/{bot}/channels',json={'channel':'PUBLIC_LINK'}).json()
    widget=c.post(f'/api/chatbots/{bot}/channels',json={'channel':'WEB_WIDGET'}).json()
    other_share=c.post(f'/api/chatbots/{other}/channels',json={'channel':'PUBLIC_LINK'}).json()
    answer=c.post('/api/public/share/'+share['public_id']+'/ask',json={'question':'Hi'}).json()
    data={'question':'Continue','conversation_id':answer['conversation_id'],'session_token':answer['session_token']}
    for path in ('/api/public/widget/'+widget['public_id'], '/api/public/share/'+other_share['public_id']):
        assert c.post(path+'/ask',json=data).status_code==404
