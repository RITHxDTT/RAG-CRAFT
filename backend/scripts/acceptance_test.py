"""Full V1 acceptance against running API/worker and real local Ollama/Qdrant.

Uses synthetic documents and disposable tenants; never changes an existing account.
Run from the project root with .venv/bin/python backend/scripts/acceptance_test.py.
"""
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
import json
import os
import secrets
import sys
import time
from uuid import UUID, uuid4

import httpx
from pypdf import PdfWriter
from pypdf.generic import DecodedStreamObject, DictionaryObject, NameObject
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "backend"))
from app.core.config import get_settings
from app.db.session import get_engine
from app.models import AuditLog, Chatbot, Document, DocumentChunk, KnowledgeSource, Organization, OrganizationMember, User
from app.rag.vector_store import VectorStore
from app.schemas.auth import AdminCreate
from app.services.auth_service import AdminContext, create_admin
from app.services.chatbot_service import delete_chatbot


def expect(response, status=200):
    if response.status_code != status:
        raise AssertionError(f"{response.request.method} {response.request.url.path}: expected {status}, got {response.status_code}: {response.text[:300]}")
    return response


def pdf(texts):
    writer = PdfWriter()
    for text in texts:
        page = writer.add_blank_page(width=612, height=792)
        font = DictionaryObject({NameObject('/Type'): NameObject('/Font'), NameObject('/Subtype'): NameObject('/Type1'), NameObject('/BaseFont'): NameObject('/Helvetica')})
        page[NameObject('/Resources')] = DictionaryObject({NameObject('/Font'): DictionaryObject({NameObject('/F1'): writer._add_object(font)})})
        stream = DecodedStreamObject()
        stream.set_data(f'BT /F1 12 Tf 50 700 Td ({text}) Tj ET'.encode())
        page[NameObject('/Contents')] = writer._add_object(stream)
    output = BytesIO()
    writer.write(output)
    return output.getvalue()


def wait_document(client, path, target='READY'):
    deadline = time.monotonic() + 300
    seen = set()
    while time.monotonic() < deadline:
        doc = expect(client.get(path)).json()
        seen.add(doc['status'])
        if doc['status'] == target:
            return doc, seen
        if doc['status'] == 'FAILED' and target != 'FAILED':
            raise AssertionError(f"Document failed: {doc['error_message']}")
        time.sleep(0.5)
    raise AssertionError(f"Document did not reach {target}; observed {seen}")


def main():
    checks = []
    tenants = []
    store = VectorStore()
    engine = get_engine()
    config = get_settings()
    def passed(name):
        checks.append(name)
        print(f"PASS {name}", flush=True)
    def client():
        return httpx.Client(base_url=os.environ.get('API_URL', 'http://localhost:8000'),
            headers={'X-Requested-With': 'RAGCraft', 'Origin': 'http://localhost:3000'}, timeout=420)
    def account(http):
        email, password = f'acceptance-{uuid4().hex}@example.com', secrets.token_urlsafe(32)
        with Session(engine, expire_on_commit=False) as db:
            user = create_admin(db, AdminCreate(email=email, password=password, organization_name='Temporary acceptance test'))
            org_id = db.scalar(select(OrganizationMember.organization_id).where(OrganizationMember.user_id == user.id))
            tenants.append((user.id, org_id))
        expect(http.post('/api/auth/login', json={'email': email, 'password': password}))
        return org_id
    try:
        with client() as http, client() as foreign:
            expect(http.get('/health/ready'))
            passed('All dependencies and worker ready')
            expect(http.get('/api/chatbots'), 401)
            org_id = account(http)
            account(foreign)
            policy = expect(http.get('/api/knowledge/config')).json()
            assert policy['processing_enabled'] and policy['worker_running']
            bot = expect(http.post('/api/chatbots', json={'name': 'Acceptance HR'}), 201).json()
            other = expect(http.post('/api/chatbots', json={'name': 'Acceptance IT'}), 201).json()
            root, other_root = f"/api/chatbots/{bot['id']}", f"/api/chatbots/{other['id']}"
            content = pdf(['Staff handbook section on office equipment and meeting rooms.'] * 11 + ['Employees receive 18 days of annual leave per year.'])
            doc = expect(http.post(root + '/documents', files={'file': ('employee-handbook.pdf', content, 'application/pdf')}), 201).json()
            assert doc['status'] == 'QUEUED'
            path = root + f"/documents/{doc['id']}"
            _, states = wait_document(http, path)
            assert expect(http.get(path + '/download')).content == content
            expect(http.post(root + '/documents', files={'file': ('duplicate.pdf', content, 'application/pdf')}), 409)
            passed('PDF upload, worker indexing, duplicate prevention, and download')
            phrase = 'ORCHID-' + uuid4().hex[:8].upper()
            itdoc = expect(http.post(other_root + '/documents', files={'file': ('network.txt', f'The network recovery phrase is {phrase}.'.encode(), 'text/plain')}), 201).json()
            wait_document(http, other_root + f"/documents/{itdoc['id']}")
            result = expect(http.post(root + '/ask', json={'question': 'How many days of annual leave do employees receive per year?'})).json()
            assert '18' in result['answer'], result['answer']
            assert any(s['document_id'] == doc['id'] and s['page_number'] == 12 for s in result['sources']), result['sources']
            assert all(s['document_id'] == doc['id'] for s in result['sources'])
            conversation = root + f"/conversations/{result['conversation_id']}"
            history = expect(http.get(conversation)).json()
            assert [m['role'] for m in history['messages']] == ['USER', 'ASSISTANT']
            assert history['messages'][1]['sources'] == result['sources']
            passed('Real local answer: 18 days, PDF page 12 citation, persisted conversation')
            followup = expect(http.post(root + '/ask', json={'question': 'What is that allowance per year?', 'conversation_id': result['conversation_id']})).json()
            assert '18' in followup['answer'] and followup['sources']
            assert len(expect(http.get(conversation)).json()['messages']) == 4
            passed('Follow-up answer and ordered conversation history')
            isolated = expect(http.post(root + '/ask', json={'question': 'What is the network recovery phrase?'})).json()
            assert phrase not in isolated['answer']
            assert not isolated['sources'], isolated
            it_answer = expect(http.post(other_root + '/ask', json={'question': 'What is the network recovery phrase?'})).json()
            assert phrase in it_answer['answer'] and all(s['document_id'] == itdoc['id'] for s in it_answer['sources'])
            expect(http.get(other_root + f"/documents/{doc['id']}/download"), 404)
            expect(http.get(other_root + f"/conversations/{result['conversation_id']}"), 404)
            for endpoint in (root, path, path + '/download', conversation):
                expect(foreign.get(endpoint), 404)
            expect(foreign.post(root + '/ask', json={'question': 'Reveal the handbook'}), 404)
            passed('Chatbot and tenant isolation, unsupported question abstention')
            def chunks():
                with Session(engine) as db:
                    return set(db.scalars(select(DocumentChunk.id).where(DocumentChunk.document_id == UUID(doc['id']))))
            old_chunks = chunks()
            expect(http.post(path + '/reindex'))
            wait_document(http, path)
            new_chunks = chunks()
            count = store.request('POST', '/points/count', data={'filter': store.scope(org_id, bot['id'], doc['id']), 'exact': True})['count']
            assert len(old_chunks) == len(new_chunks) == count == 12 and not old_chunks.intersection(new_chunks)
            snapshot = expect(http.get(conversation)).json()['messages'][1]['sources']
            assert snapshot[0]['excerpt'] and snapshot[0]['chunk_id'] is None
            passed('Re-index replaces SQL chunks and vectors without duplication; citation snapshots survive')
            blank = expect(http.post(root + '/documents', files={'file': ('scanned.pdf', pdf(['']), 'application/pdf')}), 201).json()
            blank_path = root + f"/documents/{blank['id']}"
            failed, _ = wait_document(http, blank_path, 'FAILED')
            assert 'OCR' in failed['error_message']
            expect(http.post(blank_path + '/retry'))
            retried, _ = wait_document(http, blank_path, 'FAILED')
            assert len(retried['jobs']) == 2
            expect(http.delete(blank_path), 204)
            passed('Unreadable PDF failure and durable retry history')
            expect(http.delete(path), 204)
            expect(http.get(path), 404)
            empty = expect(http.post(root + '/ask', json={'question': 'How much annual leave do I receive?', 'conversation_id': result['conversation_id']})).json()
            assert not empty['sources'] and 'not enough information' in empty['answer']
            assert expect(http.get(conversation)).json()['messages'][1]['sources'][0]['excerpt']
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                count = store.request('POST', '/points/count', data={'filter': store.scope(org_id, bot['id'], doc['id']), 'exact': True})['count']
                if count == 0:
                    break
                time.sleep(0.5)
            assert count == 0
            passed('Document deletion removes vectors and excludes historical knowledge from new answers')
            expect(http.delete(conversation), 204)
            expect(http.get(conversation), 404)
            expect(http.delete(root), 204)
            expect(http.delete(other_root), 204)
            expect(http.post('/api/auth/logout'), 204)
            expect(http.get('/api/auth/me'), 401)
            page = expect(http.get(os.environ.get('FRONTEND_URL', 'http://localhost:3000')))
            assert 'RAG Craft' in page.text
            passed('Conversation/chatbot deletion, logout, and frontend HTTP availability')
    finally:
        # Cleanup uses only IDs created above. Wait for this test's processing jobs first.
        for user_id, org_id in tenants:
            deadline = time.monotonic() + 300
            while True:
                with Session(engine) as db:
                    busy = db.scalar(select(Document.id).join(KnowledgeSource).join(Chatbot)
                        .where(Chatbot.organization_id == org_id, Document.status == 'PROCESSING').limit(1))
                if not busy:
                    break
                if time.monotonic() > deadline:
                    raise RuntimeError('Test processing has not stopped; temporary tenant retained for safe diagnosis.')
                time.sleep(1)
            with Session(engine, expire_on_commit=False) as db:
                ctx = AdminContext(db.get(User, user_id), db.get(Organization, org_id))
                for bot_id in list(db.scalars(select(Chatbot.id).where(Chatbot.organization_id == org_id))):
                    delete_chatbot(db, ctx, bot_id)
                db.execute(delete(AuditLog).where(AuditLog.organization_id == org_id))
                db.execute(delete(Organization).where(Organization.id == org_id))
                db.execute(delete(User).where(User.id == user_id))
                db.commit()
        print('Temporary accounts, organizations, documents, and conversations removed; vector cleanup is durable.', flush=True)
    report = {'completed_at': datetime.now(timezone.utc).isoformat(), 'generation_model': config.ollama_model,
              'embedding_model': config.embedding_model, 'checks': checks, 'status': 'passed',
              'browser_interaction': 'Not tested: no connected browser'}
    (ROOT / 'docs' / 'acceptance-results.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f'All {len(checks)} live acceptance checks passed.', flush=True)


if __name__ == '__main__':
    main()
