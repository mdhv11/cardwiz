# CardWiz

AI-powered credit card rewards optimizer with a React web app, Spring Boot API gateway/orchestrator, and a FastAPI AI service.

## What the Web App Can Do

### Authentication and session
- Register and sign in with JWT auth.
- Protected app routes via auth guard (`/dashboard`, `/cards`, `/advisor`, `/profile`).
- Persistent session token in browser local storage.

### Dashboard
- Personalized welcome snapshot.
- Quick view of user cards.
- Recent transaction validations with status chips:
  - `MATCHED` (used best card)
  - `MISSED` (better card available)
  - `NOT_SET`
- Add validation flow from dashboard (`merchant`, `amount`, `category`, `currency`, optional `actualCardId`, date).

### Cards (wallet)
- Add and list cards (`cardName`, `issuer`, `network`, `lastFourDigits`, `active`).
- Card knowledge readiness state per card:
  - `Knowledge Ready`
  - `AI Analyzing...`
  - `Analysis Failed`
  - `No Docs Indexed`
- Upload card T&C/statement documents per card for async AI ingestion.
- Polling for ingestion status updates.
- Card comparison flow:
  - Select multiple active cards
  - Enter merchant/category/amount/currency context
  - View winner + comparison table + missing-data warning

### Smart Advisor
- Chat-style recommendation interface.
- Context-aware clarification prompts when merchant/amount are missing.
- Currency-aware recommendations (`INR`, `USD`, `EUR`, `GBP`, `AED`, `SGD`).
- Persisted advisor history (load, append, clear).
- Upload-based AI workflows:
  - `PDF`: statement missed-savings analysis
  - `Image/PDF`: document analysis and reward-rule extraction preview
- File guardrails in UI:
  - Allowed: `pdf`, `jpg`, `jpeg`, `png`, `webp`
  - Max size: `20 MB`

### Profile
- View and update first/last name.
- View stored email and avatar URL if present.

## AI and Recommendation Capabilities

- Multimodal document parsing (Bedrock Nova) for reward rules.
- Rule extraction normalization:
  - Cashback percentages
  - Points-based rules with effective percentage derivation
- Embedding sync + vector search (`pgvector`) for rule retrieval.
- Hybrid recommendation routing:
  - Deterministic fast-path
  - LLM reranking (Nova Lite)
  - Agentic tool-calling path for complex/high-spend queries
- Statement missed-savings analysis across actual-vs-optimal card choice.

## Architecture

- `client/`: React + Vite + MUI + Redux Toolkit
- `cardwiz-backend/service-registry/`: Eureka server (`:8761`)
- `cardwiz-backend/user-service/`: Spring Boot API + auth + domain + gateway (`:8080`)
- `cardwiz-backend/ai-service/`: FastAPI AI service (`:8000`)
- `cardwiz-backend/docker-compose.infra.yml`: local infra (Postgres+pgvector, Redis, MinIO, Kafka, Zookeeper, pgAdmin)

## Tech Stack

- Frontend: React 18, Vite, MUI, Redux Toolkit, Axios
- Backend: Java 21, Spring Boot 3, Spring Security JWT, Spring Cloud Gateway MVC, Eureka, JPA
- AI Service: FastAPI, Boto3 (Amazon Bedrock), pgvector, Redis, aiokafka
- Data/Infra: PostgreSQL (pgvector), Redis, MinIO (S3-compatible), Kafka

## Key API Surface (used by web app)

### Auth
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/authenticate`

### Users
- `GET /api/v1/users/me`
- `PUT /api/v1/users/me`
- `POST /api/v1/users/change-password`
- `DELETE /api/v1/users/me`

### Cards
- `GET /api/v1/cards`
- `POST /api/v1/cards`
- `PUT /api/v1/cards/{cardId}`
- `DELETE /api/v1/cards/{cardId}`
- `POST /api/v1/cards/recommendations`
- `GET /api/v1/cards/knowledge-coverage`
- `POST /api/v1/cards/{cardId}/documents/analyze`
- `GET /api/v1/cards/documents/{documentId}/status`
- `POST /api/v1/cards/documents/analyze`
- `POST /api/v1/cards/statement-missed-savings`

### Transactions and validation
- `GET /api/v1/transactions`
- `POST /api/v1/transactions`
- `PUT /api/v1/transactions/{transactionId}`
- `DELETE /api/v1/transactions/{transactionId}`
- `POST /api/v1/transactions/validate`

### Advisor history
- `GET /api/v1/advisor/history`
- `POST /api/v1/advisor/history`
- `DELETE /api/v1/advisor/history`

### AI service
- `GET /health`
- `POST /ai/v1/documents/analyze`
- `POST /ai/v1/recommend/rank`
- `POST /ai/v1/recommend/statement-missed-savings`
- `POST /ai/v1/embeddings/sync`
- `POST /ai/v1/embeddings/coverage`

## Local Development

### 1) Prerequisites
- Node.js 18+
- Java 21+
- Maven 3.9+
- Python 3.10+
- Docker + Docker Compose
- AWS Bedrock access (for real AI model calls)

### 2) Start infra
```bash
cd cardwiz-backend
docker compose -f docker-compose.infra.yml up -d
```

### 3) Start service registry
```bash
cd cardwiz-backend/service-registry
./mvnw spring-boot:run
```

### 4) Start user-service
```bash
cd cardwiz-backend/user-service
./mvnw spring-boot:run
```

### 5) Start ai-service
```bash
cd cardwiz-backend/ai-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 6) Start frontend
```bash
cd client
npm install
npm run dev
```

Vite proxies `/api/*` to `http://localhost:8080`.

## Environment Notes

- `user-service` reads config from:
  - `cardwiz-backend/user-service/.env`
  - defaults in `application.properties`
- `ai-service` reads config from:
  - `cardwiz-backend/ai-service/.env`
  - defaults in `app/config.py`

Important vars you will likely set:
- Bedrock/AWS credentials and region
- S3/MinIO endpoint + keys + bucket
- DB and Redis connection values
- Kafka toggle and brokers (if using async ingestion)
- AI callback secret parity between services

## Current Status

This README reflects implemented functionality in the current codebase (frontend routes, controllers, and AI service flows), replacing older roadmap-only or placeholder sections.
