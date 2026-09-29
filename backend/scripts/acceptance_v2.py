"""V2 live RAG acceptance in a disposable database, collection, and upload folder.

Runs real PostgreSQL, Qdrant, and Ollama; Telegram transport is mocked separately
by test_v2_telegram.py. Does not modify the configured application database.
"""
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
from uuid import uuid4
from datetime import datetime, timezone
import httpx
from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url
from sqlalchemy.orm import Session

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'backend'))
from app.core.config import get_settings


def main():
    config=get_settings()
    source=make_url(config.database_url)
    name='ragcraft_v2_test_'+uuid4().hex
    collection='ragcraft_v2_test_'+uuid4().hex
    control=create_engine(source,isolation_level='AUTOCOMMIT')
    created=False
    engine=None
    checks=[]
    def passed(label):
        checks.append(label)
        print('PASS '+label,flush=True)
    def expect(response,status=200):
        assert response.status_code==status, f'{response.request.method}: expected {status}, received {response.status_code}'
        return response.json() if response.content else None
    try:
        with control.connect() as connection:
            connection.execute(text(f'CREATE DATABASE "{name}"'))
            created=True
        url=source.set(database=name).render_as_string(hide_password=False)
        subprocess.run([sys.executable,'-m','alembic','-c','backend/alembic.ini','upgrade','head'],cwd=ROOT,env={**os.environ,'DATABASE_URL':url},check=True)
        engine=create_engine(url)
        config.qdrant_collection=collection
        from fastapi.testclient import TestClient
        from app.main import app
        from app.db.session import get_db
        from app.schemas.auth import AdminCreate
        from app.services.auth_service import create_admin
        from app.services.ingestion_service import claim_next,process_claimed
        def database():
            with Session(engine,expire_on_commit=False) as db:
                try:yield db
                except Exception:
                    db.rollback()
                    raise
        app.dependency_overrides[get_db]=database
        with tempfile.TemporaryDirectory(prefix='ragcraft-v2-') as storage:
            config.storage_dir=Path(storage)
            password=secrets.token_urlsafe(24)
            with Session(engine,expire_on_commit=False) as db:
                create_admin(db,AdminCreate(email='admin@example.com',password=password))
            headers={'X-Requested-With':'RAGCraft','Origin':'http://localhost:3000'}
            with TestClient(app,headers=headers) as owner,TestClient(app,headers=headers) as guest:
                expect(owner.post('/api/auth/login',json={'email':'admin@example.com','password':password}))
                model=expect(owner.get('/api/admin/models'))[0]
                prompt=expect(owner.post('/api/admin/prompt-templates',json={'name':'HR','prompt':'Answer company HR questions using the supplied knowledge.'}),201)
                expect(owner.post('/api/auth/register',json={'email':'customer@example.com','full_name':'Acceptance Customer','password':password,'confirm_password':password}),201)
                bot=expect(owner.post('/api/chatbots',json={'name':'HR Assistant','settings':{'model_id':model['id'],'prompt_template_id':prompt['id']},'starter_questions':['How many annual leave days do employees receive?']}),201)
                base='/api/chatbots/'+bot['id']
                passed('Admin catalog and prompt; user registration and owned chatbot')
                content=b'Company HR policy. All full-time employees receive 18 days of paid annual leave per year. Annual leave must be requested through the HR portal. Working hours are 9 AM to 5 PM, Monday to Friday.'
                document=expect(owner.post(base+'/documents',files={'file':('hr-policy.txt',content,'text/plain')}),201)
                with Session(engine,expire_on_commit=False) as db:
                    item=claim_next(db)
                    assert item and process_claimed(db,item), 'Real document ingestion failed'
                assert expect(owner.get(base+'/documents/'+document['id']))['status']=='READY'
                passed('Real Ollama embeddings and Qdrant indexing; document READY')
                question='How many paid annual leave days do full-time employees receive per year?'
                response=expect(owner.post(base+'/ask',json={'question':question}))
                assert response['sources'] and '18' in response['answer'], 'Expected cited HR answer'
                passed('Playground returns a real grounded answer and citation')
                links={}
                for channel,kind in [('PUBLIC_LINK','share'),('WEB_WIDGET','widget')]:
                    row=expect(owner.post(base+'/channels',json={'channel':channel}),201)
                    path='/api/public/'+kind+'/'+row['public_id']
                    links[channel]=(row,path)
                    assert expect(guest.get(path))['name']=='HR Assistant'
                    answer=expect(guest.post(path+'/ask',json={'question':question}))
                    assert answer['sources'] and '18' in answer['answer']
                    assert all(s['document_id'] is None for s in answer['sources'])
                    expect(guest.post(path+'/ask',json={'question':'Continue','conversation_id':answer['conversation_id']}),404)
                    passed(channel+' uses the same real RAG and rejects another guest session')
                rows=expect(owner.get(base+'/conversations'))
                assert {r['channel'] for r in rows}=={'PLAYGROUND','PUBLIC_LINK','WEB_WIDGET'}
                usage=expect(owner.get('/api/analytics'))
                assert usage['total_conversations']==3 and usage['total_messages']==6
                passed('Conversation channels and SQL usage aggregates')
                empty=expect(owner.post('/api/chatbots',json={'name':'No HR knowledge'}),201)
                answer=expect(owner.post('/api/chatbots/'+empty['id']+'/ask',json={'question':question}))
                assert not answer['sources'] and 'not enough information' in answer['answer']
                expect(guest.post('/api/auth/register',json={'email':'other@example.com','full_name':'Other Customer','password':password,'confirm_password':password}),201)
                for suffix in ('','/documents','/conversations','/channels'):
                    expect(guest.get(base+suffix),404)
                passed('Cross-chatbot retrieval and cross-user resource isolation')
                row,path=links['PUBLIC_LINK']
                expect(owner.post(base+'/channels/'+row['id']+'/regenerate'))
                expect(guest.get(path),404)
                widget,path=links['WEB_WIDGET']
                expect(owner.patch(base+'/channels/'+widget['id'],json={'enabled':False}))
                expect(guest.get(path),404)
                passed('Public token revocation and widget disabling')
        result={'checked_at':datetime.now(timezone.utc).isoformat(),'checks':checks,'telegram':'mocked tests only; live credentials and public HTTPS required','browser':'not exercised by this script'}
        (ROOT/'docs/v2-acceptance-results.json').write_text(json.dumps(result,indent=2)+'\n')
    finally:
        if engine:engine.dispose()
        if created:
            with control.connect() as connection:
                connection.execute(text(f'DROP DATABASE "{name}" WITH (FORCE)'))
        control.dispose()
        try:
            httpx.delete(config.qdrant_url.rstrip('/')+'/collections/'+collection,timeout=15).raise_for_status()
        except httpx.HTTPError:
            print('Disposable Qdrant collection cleanup could not be confirmed.',flush=True)

if __name__=='__main__':main()
