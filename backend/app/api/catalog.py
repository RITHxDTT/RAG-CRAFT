from uuid import UUID
from fastapi import APIRouter, Depends
from app.api.dependencies import Admin, Authenticated, DB, require_csrf
from app.schemas.catalog import ModelInput, ModelView, PromptInput, PromptView
from app.repositories import catalog_repository as catalog
from app.services import catalog_service as service
router = APIRouter(prefix='/api', tags=['catalog'], dependencies=[Depends(require_csrf)])

@router.get('/models', response_model=list[ModelView])
def models(db: DB, user: Authenticated):
    return catalog.list_models(db, True)

@router.get('/admin/models', response_model=list[ModelView])
def admin_models(db: DB, admin: Admin):
    return catalog.list_models(db)

@router.post('/admin/models', response_model=ModelView, status_code=201)
def create_model(data: ModelInput, db: DB, admin: Admin):
    return service.save_model(db, data)

@router.patch('/admin/models/{model_id}', response_model=ModelView)
def update_model(model_id: UUID, data: ModelInput, db: DB, admin: Admin):
    return service.save_model(db, data, model_id)

@router.get('/prompt-templates', response_model=list[PromptView])
def prompts(db: DB, user: Authenticated):
    return catalog.list_prompts(db, True)

@router.get('/admin/prompt-templates', response_model=list[PromptView])
def admin_prompts(db: DB, admin: Admin):
    return catalog.list_prompts(db)

@router.post('/admin/prompt-templates', response_model=PromptView, status_code=201)
def create_prompt(data: PromptInput, db: DB, admin: Admin):
    return service.save_prompt(db, data)

@router.patch('/admin/prompt-templates/{prompt_id}', response_model=PromptView)
def update_prompt(prompt_id: UUID, data: PromptInput, db: DB, admin: Admin):
    return service.save_prompt(db, data, prompt_id)
