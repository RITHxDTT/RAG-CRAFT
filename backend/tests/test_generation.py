from types import SimpleNamespace
from uuid import uuid4
import httpx
import pytest
from app.core.exceptions import AppError
from app.rag import generator, pipeline
from app.rag.prompt_builder import build_messages
from app.rag.retriever import RetrievedChunk
from app.schemas.rag import GeneratedAnswer, Source
from app.schemas.chatbot import BotSettings


def chunk():
    return RetrievedChunk(content='Annual leave is 18 days.', source=Source(chunk_id=uuid4(), document_id=uuid4(),
        document_name='policy.pdf', page_number=2, chunk_index=0, excerpt='Annual leave is 18 days.', score=0.9))


def bot():
    return SimpleNamespace(id=uuid4(), organization_id=uuid4(), settings=BotSettings())


def test_pipeline_citations_are_retrieved_not_invented(monkeypatch):
    source = chunk()
    monkeypatch.setattr(pipeline.retriever, 'retrieve', lambda *args: [source])
    monkeypatch.setattr(pipeline.generator, 'generate', lambda *args: GeneratedAnswer(answer='18 days.', has_answer=True, source_ids=[1, 999, 1]))
    result = pipeline.answer(None, bot(), 'How much leave?')
    assert result.sources == [source.source]
    assert result.answer == '18 days.'


def test_no_context_or_unsupported_answer_abstains(monkeypatch):
    monkeypatch.setattr(pipeline.retriever, 'retrieve', lambda *args: [])
    def unexpected(*args):
        raise AssertionError('Must not generate without knowledge')
    monkeypatch.setattr(pipeline.generator, 'generate', unexpected)
    assert pipeline.answer(None, bot(), 'Question').sources == []
    monkeypatch.setattr(pipeline.retriever, 'retrieve', lambda *args: [chunk()])
    for generated in [GeneratedAnswer(answer='Unknown', has_answer=False, source_ids=[1]),
                      GeneratedAnswer(answer='Invented', has_answer=True, source_ids=[999])]:
        monkeypatch.setattr(pipeline.generator, 'generate', lambda *args: generated)
        assert pipeline.answer(None, bot(), 'Question').answer == pipeline.INSUFFICIENT


def test_prompt_keeps_documents_as_data():
    messages = build_messages('Be concise.', [chunk()], 'How much leave?')
    assert messages[0]['role'] == 'system'
    assert 'untrusted data' in messages[0]['content']
    assert 'Annual leave is 18 days.' not in messages[0]['content']
    assert 'source_id' in messages[1]['content']


def test_generator_timeout_is_clean(monkeypatch):
    class Client:
        def __init__(self, **kwargs): pass
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def get(self, *args, **kwargs): raise httpx.ReadTimeout('private provider details')
    monkeypatch.setattr(generator.httpx, 'Client', Client)
    with pytest.raises(AppError, match='timed out') as error:
        generator.generate([], BotSettings())
    assert error.value.status_code == 504
    assert 'private' not in error.value.detail
