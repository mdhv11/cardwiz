# AI-Service Technical Specification

The AI-Service is the specialized intelligence layer of CardWiz.  
It handles unstructured data (images/PDFs) and converts it into structured, searchable financial logic.

## Core Responsibilities

- Multimodal parsing: uses Nova 2 Pro to read credit card statements and reward brochures.
- Semantic search: converts natural language queries (for example, `"I'm at a gas station"`) into vectors.
- Recommendation reasoning: explains why a specific card is the best choice based on retrieved rules.
- Vector management: maintains a pgvector HNSW index in PostgreSQL for fast similarity search.

## API Endpoints

### Endpoint List

- `POST /ai/v1/documents/analyze`
- `POST /ai/v1/recommend/rank`
- `POST /ai/v1/embeddings/sync`
- `GET /health`

### Route Matrix

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/ai/v1/documents/analyze` | Parse an S3-hosted statement/brochure with Nova 2 Pro. | `AnalyzeDocumentRequest` | `NovaAnalysisResponse` |
| `POST` | `/ai/v1/recommend/rank` | Rank eligible cards for a merchant/category context. | `RecommendationRequest` | `RecommendationResponse` |
| `POST` | `/ai/v1/embeddings/sync` | Re-index a reward rule into the vector store. | `EmbeddingSyncRequest` | `EmbeddingSyncResponse` |
| `GET` | `/health` | Service health endpoint. | None | `{"status":"UP","service":"ai-service"}` |

## Service Layer

### DocumentService

- Fetches object bytes from S3.
- Detects file type and converts PDF first page to PNG when needed.
- Sends multimodal prompt to Nova 2 Pro (`us.amazon.nova-2-pro-v1:0`).
- Parses response JSON and retries with JSON-repair prompt if malformed.

### EmbeddingService

- Sanitizes input text (normalization + stopword removal).
- Calls Nova embeddings model (`amazon.nova-2-multimodal-embeddings-v1:0`) for 1024-d vectors.
- Upserts vectors into `reward_rule_vectors` (pgvector-backed).

### RecommendationService

- Receives eligible card IDs from core service.
- Runs top-k vector similarity search for merchant/category context.
- Intersects search results with eligible card IDs.
- Uses Nova 2 Pro to produce a human-readable recommendation rationale.

## Internal Data Flow

1. Core service uploads PDF to S3 and calls AI-Service.
2. AI-Service extracts structured rules with Nova.
3. AI-Service creates embeddings and stores vectors in PostgreSQL (`reward_rule_vectors`).
4. For recommendation queries, AI-Service embeds the query context, retrieves nearest rules, intersects with eligible cards, and returns ranked recommendation output.

## Project Structure

```text
ai-service/
├── app/
│   ├── main.py
│   ├── config.py
│   ├── dependencies.py
│   ├── db.py
│   ├── routes/
│   │   ├── document_routes.py
│   │   ├── recommendation_routes.py
│   │   └── embedding_routes.py
│   ├── services/
│   │   ├── document_service.py
│   │   ├── embedding_service.py
│   │   └── recommendation_service.py
│   ├── schemas/
│   │   ├── document_schema.py
│   │   └── recommendation_schema.py
│   ├── models/
│   │   └── vector_models.py
│   └── clients/
│       └── bedrock_client.py
├── requirements.txt
├── Dockerfile
└── .env
```

## Configuration Notes

- PDF conversion:
  - PDFs are converted to PNG (first page) via `pdf2image` before Bedrock submission.
  - Poppler binaries are required in runtime/container.
- Optional Nova reasoning:
  - `NOVA_ENABLE_REASONING=true`
  - `NOVA_REASONING_BUDGET_TOKENS=1024`
- Vector search:
  - `DATABASE_URL=postgresql+psycopg://<user>:<password>@<host>:5432/<db>`
  - `VECTOR_TOP_K=5`
