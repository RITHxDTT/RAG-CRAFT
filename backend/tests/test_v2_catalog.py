from test_v2_auth import DATA

def test_model_prompt_selection_and_admin_boundary(logged_in):
    client=logged_in
    model=client.post('/api/admin/models', json={'name':'Custom', 'model_identifier':'custom:local', 'is_default':True}).json()
    prompt=client.post('/api/admin/prompt-templates', json={'name':'HR', 'prompt':'Answer HR questions.'}).json()
    bot=client.post('/api/chatbots', json={'name':'HR', 'settings':{'model_id':model['id'], 'prompt_template_id':prompt['id'], 'tone':'FRIENDLY'}})
    assert bot.status_code == 201
    assert bot.json()['settings']['model_name']=='custom:local'
    assert sum(m['is_default'] for m in client.get('/api/admin/models').json())==1
    client.patch('/api/admin/models/'+model['id'], json={'name':'Custom', 'model_identifier':'custom:local', 'enabled':False})
    assert client.post('/api/chatbots', json={'name':'Disabled', 'settings':{'model_id':model['id']}}).status_code==422
    client.post('/api/auth/register', json=DATA)
    assert client.get('/api/admin/models').status_code==403
    assert client.post('/api/admin/prompt-templates', json={'name':'No','prompt':'No'}).status_code==403
    assert all(m['enabled'] for m in client.get('/api/models').json())


def test_starter_questions_crud(logged_in):
    bot=logged_in.post('/api/chatbots', json={'name':'Starter', 'starter_questions':['First?', 'Second?']}).json()
    changed=logged_in.patch('/api/chatbots/'+bot['id'], json={'starter_questions':['Second?', 'Edited?']})
    assert changed.json()['starter_questions']==['Second?', 'Edited?']
    assert logged_in.patch('/api/chatbots/'+bot['id'], json={'starter_questions':[]}).json()['starter_questions']==[]
    assert logged_in.patch('/api/chatbots/'+bot['id'], json={'starter_questions':['x']*11}).status_code==422


def test_partial_settings_update_preserves_model_and_custom_instruction(logged_in):
    model=logged_in.post('/api/admin/models',json={'name':'Other','model_identifier':'other:local'}).json()
    bot=logged_in.post('/api/chatbots',json={'name':'Persistent','settings':{'model_id':model['id'],'custom_instruction':'Be helpful.','tone':'FRIENDLY'}}).json()
    changed=logged_in.patch('/api/chatbots/'+bot['id'],json={'settings':{'top_k':3}}).json()
    assert changed['settings']['model_id']==model['id']
    assert changed['settings']['custom_instruction']=='Be helpful.'
    assert changed['settings']['tone']=='FRIENDLY'
