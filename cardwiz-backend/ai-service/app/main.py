from fastapi import FastAPI
from contextlib import asynccontextmanager
from app.config import settings
from app.db import init_db
from app.dependencies import init_eureka, stop_eureka
from app.routes import document_routes, recommendation_routes, embedding_routes
from app.consumers.kafka_ingest_consumer import KafkaIngestConsumer

kafka_consumer = KafkaIngestConsumer()

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Ensure vector table/index metadata exists
    init_db()
    # Startup: Register with Eureka
    await init_eureka()
    await kafka_consumer.start()
    yield
    # Shutdown: Cleanly deregister
    await kafka_consumer.stop()
    await stop_eureka()

app = FastAPI(
    title="CardWiz AI Service",
    description="Intelligence layer for multimodal reward extraction",
    lifespan=lifespan
)

# Include Routers
app.include_router(document_routes.router, prefix="/ai/v1/documents", tags=["Documents"])
app.include_router(recommendation_routes.router, prefix="/ai/v1/recommend", tags=["Recommendations"])
app.include_router(embedding_routes.router, prefix="/ai/v1/embeddings", tags=["Embeddings"])

@app.get("/health")
async def health_check():
    return {"status": "UP", "service": settings.APP_NAME}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.INSTANCE_PORT,
        reload=False,
    )
