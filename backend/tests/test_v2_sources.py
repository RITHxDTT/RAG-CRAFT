from test_documents import pdf_bytes
from app.models import Document
from app.services.file_storage import storage_path
from uuid import UUID


def test_pdf_preview_and_unavailable_file(logged_in,db):
    c=logged_in
    bot=c.post('/api/chatbots',json={'name':'Sources'}).json()['id']
    content=pdf_bytes()
    doc=c.post(f'/api/chatbots/{bot}/documents',files={'file':('source.pdf',content,'application/pdf')}).json()['id']
    path=f'/api/chatbots/{bot}/documents/{doc}'
    assert c.get(path+'/availability').json()['available'] is True
    response=c.get(path+'/preview')
    assert response.content==content
    assert response.headers['content-type']=='application/pdf'
    assert 'inline' in response.headers['content-disposition']
    storage_path(db.get(Document,UUID(doc)).storage_key).unlink()
    assert c.get(path+'/availability').status_code==404
    assert c.get(path+'/preview').status_code==404
