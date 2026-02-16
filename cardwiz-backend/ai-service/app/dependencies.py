from py_eureka_client import eureka_client
import logging
from app.config import settings

logger = logging.getLogger("uvicorn")

async def init_eureka():
    if not settings.EUREKA_ENABLED:
        logger.info("Eureka registration disabled by configuration.")
        return

    await eureka_client.init_async(
        eureka_server=settings.EUREKA_SERVER,
        app_name=settings.APP_NAME,
        instance_port=settings.INSTANCE_PORT
    )
    logger.info(f"Registered {settings.APP_NAME} with Eureka at {settings.EUREKA_SERVER}")

async def stop_eureka():
    if not settings.EUREKA_ENABLED:
        return

    await eureka_client.stop_async()
    logger.info(f"Deregistered {settings.APP_NAME} from Eureka")
