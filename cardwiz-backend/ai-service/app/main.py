from fastapi import FastAPI
from app.routes import document_routes, recommendation_routes
from py_eureka_client import eureka_client

app = FastAPI(title="CardWiz AI Service")

app.include_router(document_routes.router)
app.include_router(recommendation_routes.router)

@app.get("/health")
def health():
    return {"status": "AI Service Running"}

eureka_client.init(
    eureka_server="http://service-registry:8761/eureka",
    app_name="ai-service",
    instance_port=8000
)
