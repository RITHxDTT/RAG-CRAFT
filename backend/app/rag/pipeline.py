from app.rag import generator, retriever
from app.rag.prompt_builder import build_messages
from app.schemas.rag import RagAnswer

INSUFFICIENT = "There is not enough information in this chatbot's available knowledge base to answer that question."


def answer(db, bot, question: str, history: list[dict] | None = None) -> RagAnswer:
    history = history or []
    prior_questions = [entry['content'] for entry in history if entry['role'] == 'user'][-2:]
    search_question = '\n'.join(prior_questions + [question])[-4000:]
    chunks = retriever.retrieve(db, bot.organization_id, bot.id, search_question, bot.settings.top_k)
    if not chunks:
        return RagAnswer(answer=INSUFFICIENT, sources=[])
    generated = generator.generate(build_messages(bot.settings.system_instruction, chunks, question, history), bot.settings)
    source_ids = list(dict.fromkeys(generated.source_ids))
    sources = [chunks[index - 1].source for index in source_ids if 1 <= index <= len(chunks)]
    if not generated.has_answer or not sources:
        return RagAnswer(answer=INSUFFICIENT, sources=[])
    return RagAnswer(answer=generated.answer, sources=sources)
