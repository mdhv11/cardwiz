from py_eureka_client import eureka_client
import logging
from app.config import settings

logger = logging.getLogger("uvicorn")

async def init_eureka():
    if not settings.EUREKA_ENABLED:
        logger.info("Eureka registration disabled by configuration.")
        return

    init_kwargs = {
        "eureka_server": settings.EUREKA_SERVER,
        "app_name": settings.APP_NAME,
        "instance_port": settings.INSTANCE_PORT,
    }
    if settings.EUREKA_INSTANCE_HOST:
        init_kwargs["instance_host"] = settings.EUREKA_INSTANCE_HOST

    await eureka_client.init_async(
        **init_kwargs
    )
    logger.info(
        "Registered %s with Eureka at %s (instance_host=%s, port=%s)",
        settings.APP_NAME,
        settings.EUREKA_SERVER,
        settings.EUREKA_INSTANCE_HOST or "auto",
        settings.INSTANCE_PORT,
    )

async def stop_eureka():
    if not settings.EUREKA_ENABLED:
        return

    await eureka_client.stop_async()
    logger.info(f"Deregistered {settings.APP_NAME} from Eureka")
