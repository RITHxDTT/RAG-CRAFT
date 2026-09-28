from fastapi import APIRouter
from fastapi.responses import JSONResponse
from app.services.health_service import dependency_health

router = APIRouter(tags=["health"])

@router.get("/health")
def health():
    return {"status": "ok", "service": "rag-craft-api"}

@router.get("/health/ready")
async def ready():
    services = await dependency_health()
    healthy = all(value == "ok" for value in services.values())
    return JSONResponse(status_code=200 if healthy else 503, content={"status": "ok" if healthy else "degraded", "services": services})
