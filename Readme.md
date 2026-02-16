💳 CardWiz (RewardWiz)

The Multimodal Credit Card Reward Strategist

CardWiz is an AI-powered financial platform designed to solve the complexity of credit card reward systems. By leveraging the Amazon Nova 2 model family, CardWiz transforms messy PDF brochures and bank statements into actionable, real-time spending advice.

## 🏗 System Architecture

CardWiz uses a Polyglot Microservice architecture designed for the Amazon Nova Hackathon.

### 1. 🌐 Service Registry (Netflix Eureka)
The service discovery hub that enables microservices to find and communicate with each other.

- **Responsibilities**: Service discovery and health monitoring for the ecosystem
- **Port**: 8761
- **Technology**: Spring Boot, Netflix Eureka

### 2. 🧭 Spring Boot Orchestrator (Gateway + Security + State)
The API gateway and system brain for request routing, access control, and core state.

- **Responsibilities**: 
  - Gateway + JWT Authentication (Spring Security)
  - User/card management (store which cards a user owns)
  - Reward Knowledge Base persistence (vector IDs from Nova)
  - PostgreSQL persistence for user and reward metadata
  - Service discovery + typed calls to Python AI via Spring Cloud OpenFeign
- **Port**: 8080
- **Technology**: Java 17+, Spring Boot 3.x, Spring Security, Spring Cloud OpenFeign, PostgreSQL

### 3. 🧠 Python AI Microservice (Nova Engine)
The "Muscle" for all things AI. Built in Python to natively leverage the AWS Boto3 SDK for Amazon Nova.

- **Responsibilities**: 
  - Document analysis (Nova 2 Pro) for complex, multi-column statements
  - Reward RAG via Nova Multimodal Embeddings (vectorize card benefit PDFs)
  - Similarity search for reward recommendations
- **Port**: 8000
- **Technology**: Python 3.10+, FastAPI, Boto3 (AWS SDK)

## 🚀 Key Features

- **Multimodal Extraction**: Upload a picture or document (PDF) of a credit card offer or a monthly statement. The Python service uses Nova 2 Pro to extract complex cashback tables without manual data entry.

- **Intelligent Merchant Mapping**: Uses Nova Multimodal Embeddings to understand that a transaction at "AMZN MKTP" should be routed to the card with the best "Online Shopping" or "Amazon" rewards.

- **Deterministic + Probabilistic Hybrid**: Standard rules (like "5% on Flipkart") are handled by Java logic, while complex scenarios are handled by the AI reasoning agent.

- **Secure Authentication**: JWT-based authentication with Spring Security for secure user sessions.

- **Profile Management**: User profile management with S3 integration for profile image uploads.

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | React (Vite), Tailwind CSS, Axios _(Coming Soon)_ |
| **Backend (Orchestrator)** | Java 17+, Spring Boot 3.x, Spring Security (JWT), Spring Cloud OpenFeign, Eureka Client |
| **Backend (Service Registry)** | Spring Boot 3.x, Netflix Eureka Server |
| **Backend (AI Service)** | Python 3.10+, FastAPI, Boto3 (AWS SDK) |
| **AI Models** | Amazon Nova 2 Pro, Nova Multimodal Embeddings |
| **Database** | PostgreSQL (with pgvector) |
| **Cloud** | Amazon Bedrock, Amazon S3 |

## 📂 Project Structure

```
cardwiz/
├── cardwiz-backend/
│   ├── service-registry/      # Eureka Server (Port 8761)
│   │   ├── src/main/java/com/epoch/service_registry/
│   │   ├── Dockerfile
│   │   └── pom.xml
│   ├── orchestrator-service/  # Gateway + Security + State (Port 8080)
│   │   ├── src/main/java/com/epoch/orchestrator/
│   │   │   ├── controllers/   # REST API endpoints
│   │   │   ├── services/      # Business logic
│   │   │   ├── security/      # JWT & Auth logic
│   │   │   ├── models/        # JPA entities
│   │   │   ├── repositories/  # Data access layer
│   │   │   ├── dtos/          # Data transfer objects
│   │   │   └── config/        # Spring configuration
│   │   ├── Dockerfile
│   │   ├── .env
│   │   └── pom.xml
│   └── pom.xml                # Parent POM
├── ai-service/                # Python + FastAPI
├── frontend/                  # React + Tailwind (Coming Soon)
└── docker-compose.yml         # Local dev orchestration (Coming Soon)
```

## 🛠 Setup & Development

### Prerequisites

- **Java 17+** installed
- **Maven 3.6+** installed
- **PostgreSQL** database running
- **AWS Account** with Bedrock access (for AI features)
- **AWS S3 Bucket** for image uploads

### Environment Setup

1. **Configure Orchestrator Service Environment**
   
   Create a `.env` file in `cardwiz-backend/orchestrator-service/` with:
   ```env
   DB_URL=jdbc:postgresql://localhost:5432/cardwiz
   DB_USERNAME=your_db_username
   DB_PASSWORD=your_db_password
   JWT_SECRET=your_jwt_secret_key_here
   JWT_EXPIRATION=86400000
   AWS_ACCESS_KEY_ID=your_aws_access_key
   AWS_SECRET_ACCESS_KEY=your_aws_secret_key
   AWS_REGION=us-east-1
   S3_BUCKET_NAME=your_s3_bucket_name
   ```

2. **Database Setup**
   
   Create a PostgreSQL database:
   ```sql
   CREATE DATABASE cardwiz;
   ```

### Running the Services

#### Option 1: Run with Maven

1. **Start Service Registry** (must start first)
   ```bash
   cd cardwiz-backend/service-registry
   ./mvnw spring-boot:run
   ```
   Service will be available at: `http://localhost:8761`

2. **Start Orchestrator Service**
   ```bash
   cd cardwiz-backend/orchestrator-service
   ./mvnw spring-boot:run
   ```
   Service will be available at: `http://localhost:8080`

#### Option 2: Build and Run JARs

1. **Build all services**
   ```bash
   cd cardwiz-backend
   mvn clean package
   ```

2. **Run Service Registry**
   ```bash
   java -jar service-registry/target/service-registry-0.0.1-SNAPSHOT.jar
   ```

3. **Run Orchestrator Service**
   ```bash
   java -jar orchestrator-service/target/orchestrator-service-0.0.1-SNAPSHOT.jar
   ```

### API Endpoints

#### Authentication Endpoints
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/authenticate` - Login user

#### Orchestrator Endpoints (Requires JWT)
- `GET /api/v1/users/me` - Get current user profile
- `PUT /api/v1/users/me` - Update user profile
- `POST /api/v1/users/change-password` - Change password
- `POST /api/v1/users/upload-image` - Upload profile image

### Monitoring

- **Eureka Dashboard**: `http://localhost:8761`
- Check registered services and their health status

## 🚧 Roadmap

- [ ] AI Service with Amazon Nova integration
- [ ] Credit card management endpoints
- [ ] Transaction tracking and analysis
- [ ] Reward optimization recommendations
- [ ] Frontend React application
- [ ] Docker Compose setup for easy deployment

## 📝 License

This project is built for the Amazon Nova Hackathon.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
