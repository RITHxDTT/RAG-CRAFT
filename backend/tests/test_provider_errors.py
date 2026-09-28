import httpx
import pytest
from app.core.exceptions import AppError
from app.core.rate_limit import LoginLimiter
from app.rag import embedding_service, generator
from app.rag.vector_store import VectorStore
from app.schemas.chatbot import BotSettings


def mock_client(monkeypatch, handler):
    real = httpx.Client
    monkeypatch.setattr(httpx, 'Client', lambda **kwargs: real(transport=httpx.MockTransport(handler), **kwargs))


def test_embedding_http_error_and_malformed_vectors(monkeypatch):
    with monkeypatch.context() as patch:
        mock_client(patch, lambda request: httpx.Response(500, json={'error':'private details'}))
        with pytest.raises(AppError, match='Embedding generation failed'):
            embedding_service.embed(['text'])
    with monkeypatch.context() as patch:
        mock_client(patch, lambda request: httpx.Response(200, json={'embeddings':[[1, 2], [1]]}))
        with pytest.raises(AppError):
            embedding_service.embed(['first','second'])


def test_qdrant_failure_and_bad_response(monkeypatch):
    with monkeypatch.context() as patch:
        mock_client(patch, lambda request: httpx.Response(503))
        with pytest.raises(AppError, match='Vector storage is unavailable'):
            VectorStore().search('org','bot',[1.0],['doc'],5)
    with monkeypatch.context() as patch:
        mock_client(patch, lambda request: httpx.Response(200, json={'result':{'unexpected':True}}))
        with pytest.raises(AppError, match='invalid retrieval response'):
            VectorStore().search('org','bot',[1.0],['doc'],5)


def test_cloud_model_is_never_sent_private_context(monkeypatch):
    def handle(request):
        assert request.method == 'GET', 'Private document content must not be posted to a remote model'
        return httpx.Response(200, json={'models':[{'name':'llama3.2:3b','remote_host':'https://remote.invalid'}]})
    mock_client(monkeypatch, handle)
    with pytest.raises(AppError, match='not available locally'):
        generator.generate([{'role':'user','content':'private context'}], BotSettings())


def test_malformed_generation_is_not_returned(monkeypatch):
    def handle(request):
        return httpx.Response(200, json={'models':[{'name':'llama3.2:3b'}]} if request.method == 'GET'
                              else {'message':{'content':'not valid JSON'}})
    mock_client(monkeypatch, handle)
    with pytest.raises(AppError, match='valid answer'):
        generator.generate([], BotSettings())


def test_login_limiter_is_bounded_and_expires(monkeypatch):
    clock = [0]
    monkeypatch.setattr('app.core.rate_limit.monotonic', lambda: clock[0])
    limiter = LoginLimiter(maximum=2, window=60)
    limiter.check('one'); limiter.check('one')
    with pytest.raises(AppError) as error:
        limiter.check('one')
    assert error.value.status_code == 429
    clock[0] = 61
    limiter.check('one')
    for key in range(3000):
        limiter.check(str(key))
    assert len(limiter.attempts) == 2048
