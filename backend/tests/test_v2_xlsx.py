from io import BytesIO
from openpyxl import Workbook
from app.rag.loaders import extract
from app.rag.chunker import chunk_pages

def test_xlsx_upload_and_metadata(logged_in, tmp_path):
    wb=Workbook(); wb.active.title='Leave'; wb.active.append(['Policy','Days']); wb.active.append(['Annual',18])
    content=BytesIO();wb.save(content)
    path=tmp_path/'policy.xlsx';path.write_bytes(content.getvalue())
    chunks=chunk_pages(extract(path,'XLSX'))
    assert chunks[1].sheet_name=='Leave' and chunks[1].row_number==2
    assert '18' in chunks[1].content
    bot=logged_in.post('/api/chatbots',json={'name':'HR'}).json()
    response=logged_in.post(f"/api/chatbots/{bot['id']}/documents",files={'file':('policy.xlsx',content.getvalue(),'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')})
    assert response.status_code==201
    assert response.json()['file_type']=='XLSX'
