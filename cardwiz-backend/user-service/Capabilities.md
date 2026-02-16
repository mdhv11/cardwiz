🛡️ Core Orchestrator Service (Spring Boot)

The Core Service is the heart of CardWiz. It acts as the secure gateway, managing user identity, financial data, and orchestrating complex AI workflows with the Python service.
📂 Directory Structure
🏗️ Key Responsibilities
1. Security & Identity (Existing)

   JWT Authentication: Stateless authentication using Bearer tokens.

   Role-Based Access: Secures endpoints (e.g., only ADMIN can add global card types).

   Password Hashing: Uses BCrypt for secure storage.

2. Financial Domain Management

   Card Registry: Stores which cards a user owns (UserCard entity).

   Transaction Logging: Records user spending to build a history for better AI recommendations later.

   CRUD Operations: Create, Read, Update, Delete for cards and transactions.

3. 🧠 AI Orchestration (The "Smart" Part)

This is the new capability. The Core Service does not run AI models itself; it prepares data and delegates to Python.

    Document Ingestion:

        Receives raw PDF/Images from Frontend (MultipartFile).

        Uploads raw bytes to a specific S3 folder (documents/{userId}/...).

        Creates a DocumentMetadata record to track processing status.

    AI Delegation:

        Uses AiServiceClient to call http://ai-service/analyze.

        Passes the S3 Key (not the file itself) to Python to save bandwidth.

    Recommendation Bridging:

        Receives a generic "What card should I use?" request.

        Injects the user's specific Active Card IDs into the request (Context Injection).

        Forwards the enriched request to Python.

4. Infrastructure Integration

   Service Discovery: Registers with Netflix Eureka as USER-SERVICE.

   Load Balancing: Uses Spring Cloud LoadBalancer to find AI-SERVICE instances dynamically.

   Object Storage: Manages presigned URLs for private S3 content.

🔌 API Routes needed for AI

These are the new endpoints you must implement in CardController.java to support the hackathon features.
🛠️ Required Configuration (application.yml)

Make sure your environment variables cover the new requirements.
🚀 Critical Implementation Notes

    @LoadBalanced is Mandatory:
    In your WebConfig.java, you must annotate the RestClient.Builder bean with @LoadBalanced. Without this, the call to http://ai-service will fail because Java doesn't know how to resolve that hostname.

    DTO Mirroring:
    The Java classes in dtos/ai/ must perfectly match the Pydantic models in Python's schemas/. If you change a field name in Python (e.g., reward_rate to rate), you must change it in Java too.

    S3 Folder Strategy:

        Profile Pics -> profile-images/{userId}/... (Compressed/Resized)

        Documents -> documents/{userId}/... (Raw/High-Res for OCR)

        Ensure ImageUploadService respects this distinction.