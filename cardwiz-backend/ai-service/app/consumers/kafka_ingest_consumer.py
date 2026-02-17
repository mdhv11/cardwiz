import asyncio
import json
import logging

from aiokafka import AIOKafkaConsumer

from app.config import settings
from app.services.ingestion_service import IngestionService

logger = logging.getLogger("uvicorn")


class KafkaIngestConsumer:
    def __init__(self):
        self._consumer: AIOKafkaConsumer | None = None
        self._task: asyncio.Task | None = None
        self._service = IngestionService()

    async def start(self):
        if not settings.KAFKA_ENABLED:
            logger.info("Kafka ingest consumer disabled.")
            return

        self._consumer = AIOKafkaConsumer(
            settings.KAFKA_DOCUMENT_INGEST_TOPIC,
            bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
            group_id=settings.KAFKA_CONSUMER_GROUP,
            auto_offset_reset=settings.KAFKA_AUTO_OFFSET_RESET,
            enable_auto_commit=True,
            max_poll_interval_ms=settings.KAFKA_MAX_POLL_INTERVAL_MS,
            session_timeout_ms=settings.KAFKA_SESSION_TIMEOUT_MS,
            heartbeat_interval_ms=settings.KAFKA_HEARTBEAT_INTERVAL_MS,
            value_deserializer=lambda value: json.loads(value.decode("utf-8")),
        )
        await self._consumer.start()
        self._task = asyncio.create_task(self._consume_loop())
        logger.info(
            "Kafka ingest consumer started topic=%s bootstrap=%s group=%s",
            settings.KAFKA_DOCUMENT_INGEST_TOPIC,
            settings.KAFKA_BOOTSTRAP_SERVERS,
            settings.KAFKA_CONSUMER_GROUP,
        )

    async def stop(self):
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
            self._task = None

        if self._consumer:
            await self._consumer.stop()
            self._consumer = None
            logger.info("Kafka ingest consumer stopped.")

    async def _consume_loop(self):
        if not self._consumer:
            return

        try:
            async for message in self._consumer:
                payload = message.value
                if not isinstance(payload, dict):
                    logger.warning("Skipping non-dict ingest payload: %s", payload)
                    continue
                await self._service.process_event(payload)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            logger.error("Kafka consume loop crashed: %s", exc)
