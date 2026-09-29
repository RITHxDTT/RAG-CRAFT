from sqlalchemy import select
from app.core.exceptions import AppError
from app.models.catalog import PlatformModel, PromptTemplate
from app.repositories import catalog_repository as catalog


def save_model(db, data, model_id=None):
    if data.is_default and not data.enabled:
        raise AppError(422, 'The default model must be enabled.')
    model = db.get(PlatformModel, model_id) if model_id else PlatformModel()
    if model_id and not model:
        raise AppError(404, 'Model not found.')
    if data.is_default:
        catalog.clear_default(db)
    for key, value in data.model_dump().items():
        setattr(model, key, value)
    db.add(model)
    db.commit()
    return model


def save_prompt(db, data, prompt_id=None):
    prompt = db.get(PromptTemplate, prompt_id) if prompt_id else PromptTemplate()
    if prompt_id and not prompt:
        raise AppError(404, 'Prompt template not found.')
    for key, value in data.model_dump().items():
        setattr(prompt, key, value)
    db.add(prompt)
    db.commit()
    return prompt


def configure(db, settings, *, creating=False):
    values = settings.model_dump()
    model = db.get(PlatformModel, settings.model_id) if settings.model_id else None
    if not model and settings.model_id is None:
        if 'model_name' in settings.model_fields_set:
            model = db.scalar(select(PlatformModel).where(PlatformModel.model_identifier == settings.model_name))
        else:
            model = catalog.default_model(db)
    if not model or not model.enabled:
        raise AppError(422, 'Select an enabled model from the model catalog.')
    values.update(model_id=model.id, model_name=model.model_identifier)
    if settings.prompt_template_id:
        prompt = db.get(PromptTemplate, settings.prompt_template_id)
        if not prompt or not prompt.enabled:
            raise AppError(422, 'Select an enabled prompt template.')
    return values


def resolved_bot(db, bot):
    from app.models.catalog import PlatformModel, PromptTemplate
    from app.core.exceptions import AppError
    from types import SimpleNamespace
    model = db.get(PlatformModel, bot.settings.model_id) if bot.settings.model_id else None
    if not model or not model.enabled:
        raise AppError(409, "This chatbot's model is disabled. Select an enabled model.")
    prompt = db.get(PromptTemplate, bot.settings.prompt_template_id) if bot.settings.prompt_template_id else None
    instruction = bot.settings.custom_instruction or (prompt.prompt if prompt else bot.settings.system_instruction)
    instruction += f"\nTone: {bot.settings.tone}. Answer length: {bot.settings.answer_length}."
    generation_settings = SimpleNamespace(model_name=model.model_identifier, temperature=bot.settings.temperature, answer_length=bot.settings.answer_length)
    return SimpleNamespace(id=bot.id, organization_id=bot.organization_id, settings=SimpleNamespace(**vars(generation_settings), system_instruction=instruction, top_k=bot.settings.top_k))
