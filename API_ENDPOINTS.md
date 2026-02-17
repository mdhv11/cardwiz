# CardWiz API Endpoints

This document lists all currently defined HTTP endpoints in `user-service` and `ai-service`.

## user-service (Spring Boot)

Base domains depend on deployment setup. Paths below are service-relative.

### Auth (`/api/v1/auth`)

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Register a new user | No |
| POST | `/api/v1/auth/authenticate` | Authenticate user and get token | No |

#### Example: `POST /api/v1/auth/register`

Request:
```json
{
	"firstName": "Madhav",
	"lastName": "Sharma",
	"email": "madhav@example.com",
	"password": "StrongPass@123"
}
```

Response (200):
```json
{
	"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
	"userId": "1",
	"email": "madhav@example.com",
	"user": {
		"id": "1",
		"email": "madhav@example.com",
		"firstName": "Madhav",
		"lastName": "Sharma",
		"profileImageUrl": null
	}
}
```

#### Example: `POST /api/v1/auth/authenticate`

Request:
```json
{
	"email": "madhav@example.com",
	"password": "StrongPass@123"
}
```

Response (200):
```json
{
	"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
	"userId": "1",
	"email": "madhav@example.com",
	"user": {
		"id": "1",
		"email": "madhav@example.com",
		"firstName": "Madhav",
		"lastName": "Sharma",
		"profileImageUrl": "https://cdn.example.com/profile-1.jpg"
	}
}
```

### Users (`/api/v1/users`)

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| GET | `/api/v1/users/me` | Get current user profile | Yes |
| PUT | `/api/v1/users/me` | Update current user profile | Yes |
| POST | `/api/v1/users/change-password` | Change current user password | Yes |
| POST | `/api/v1/users/upload-image` | Upload profile image (`multipart/form-data`) | Yes |
| GET | `/api/v1/users/me/profile-image` | Get profile image URL | Yes |
| DELETE | `/api/v1/users/me` | Delete current user account | Yes |

#### Example: `GET /api/v1/users/me`

Response (200):
```json
{
	"id": "1",
	"email": "madhav@example.com",
	"firstName": "Madhav",
	"lastName": "Sharma",
	"profileImageUrl": "https://cdn.example.com/profile-1.jpg"
}
```

#### Example: `PUT /api/v1/users/me`

Request:
```json
{
	"firstName": "Madhav",
	"lastName": "S"
}
```

Response (200):
```json
{
	"id": "1",
	"email": "madhav@example.com",
	"firstName": "Madhav",
	"lastName": "S",
	"profileImageUrl": "https://cdn.example.com/profile-1.jpg"
}
```

#### Example: `POST /api/v1/users/change-password`

Request:
```json
{
	"currentPassword": "StrongPass@123",
	"newPassword": "NewStrongPass@456",
	"confirmPassword": "NewStrongPass@456"
}
```

Response (200):
```json
{}
```

#### Example: `POST /api/v1/users/upload-image`

Request: `multipart/form-data`
- `file`: binary image file (`.jpg`, `.png`, etc.)

Response (200):
```json
{
	"id": "1",
	"email": "madhav@example.com",
	"firstName": "Madhav",
	"lastName": "S",
	"profileImageUrl": "https://cdn.example.com/profile-1-updated.jpg"
}
```

#### Example: `GET /api/v1/users/me/profile-image`

Response (200):
```json
"https://cdn.example.com/profile-1-updated.jpg"
```

#### Example: `DELETE /api/v1/users/me`

Response (204):
```json
{}
```

### Cards (`/api/v1/cards`)

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| GET | `/api/v1/cards` | List user cards | Yes |
| GET | `/api/v1/cards/{cardId}` | Get card by ID | Yes |
| POST | `/api/v1/cards` | Create card | Yes |
| PUT | `/api/v1/cards/{cardId}` | Update card | Yes |
| DELETE | `/api/v1/cards/{cardId}` | Delete card | Yes |
| POST | `/api/v1/cards/documents/analyze` | Upload + analyze document (`multipart/form-data`) | Yes |
| POST | `/api/v1/cards/recommendations` | Get card recommendation for a transaction context | Yes |

#### Example: `GET /api/v1/cards`

Response (200):
```json
[
	{
		"id": 10,
		"cardName": "HDFC Millennia",
		"issuer": "HDFC",
		"network": "VISA",
		"lastFourDigits": "1234",
		"active": true
	}
]
```

#### Example: `GET /api/v1/cards/{cardId}`

Response (200):
```json
{
	"id": 10,
	"cardName": "HDFC Millennia",
	"issuer": "HDFC",
	"network": "VISA",
	"lastFourDigits": "1234",
	"active": true
}
```

#### Example: `POST /api/v1/cards`

Request:
```json
{
	"cardName": "HDFC Millennia",
	"issuer": "HDFC",
	"network": "VISA",
	"lastFourDigits": "1234",
	"active": true
}
```

Response (200):
```json
{
	"id": 10,
	"cardName": "HDFC Millennia",
	"issuer": "HDFC",
	"network": "VISA",
	"lastFourDigits": "1234",
	"active": true
}
```

#### Example: `PUT /api/v1/cards/{cardId}`

Request:
```json
{
	"cardName": "HDFC Millennia Updated",
	"issuer": "HDFC",
	"network": "VISA",
	"lastFourDigits": "1234",
	"active": true
}
```

Response (200):
```json
{
	"id": 10,
	"cardName": "HDFC Millennia Updated",
	"issuer": "HDFC",
	"network": "VISA",
	"lastFourDigits": "1234",
	"active": true
}
```

#### Example: `DELETE /api/v1/cards/{cardId}`

Response (204):
```json
{}
```

#### Example: `POST /api/v1/cards/documents/analyze`

Request: `multipart/form-data`
- `file`: statement/rules document (PDF/image)
- `documentType`: `STATEMENT` (default) or custom string

Response (200):
```json
{
	"documentId": 101,
	"s3Key": "users/1/docs/statement-jan.pdf",
	"documentType": "STATEMENT",
	"status": "COMPLETED",
	"aiSummary": "5% cashback on dining up to INR 1000/month.",
	"analysis": {
		"documentMetadata": {
			"docId": 101,
			"sourceS3": "users/1/docs/statement-jan.pdf",
			"modelUsed": "amazon.nova-2-pro-v1:0"
		},
		"extractedRules": [
			{
				"cardName": "HDFC Millennia",
				"category": "Dining",
				"rewardRate": 5.0,
				"rewardType": "CASHBACK",
				"conditions": "Up to INR 1000 per month"
			}
		],
		"aiSummary": "5% cashback on dining up to INR 1000/month."
	}
}
```

#### Example: `POST /api/v1/cards/recommendations`

Request:
```json
{
	"merchantName": "Starbucks",
	"category": "Coffee",
	"transactionAmount": 450.0
}
```

Response (200):
```json
{
	"bestOption": {
		"cardId": 10,
		"cardName": "HDFC Millennia",
		"estimatedReward": "5% Cashback",
		"reasoning": "Best dining/coffee reward for this amount.",
		"confidenceScore": 0.92
	},
	"alternatives": [
		{
			"cardId": 12,
			"cardName": "Axis ACE",
			"estimatedReward": "2% Cashback",
			"reasoning": "Lower but broad merchant coverage.",
			"confidenceScore": 0.74
		}
	],
	"semanticContext": "Matched with coffee and dining reward rules"
}
```

### Transactions (`/api/v1/transactions`)

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| GET | `/api/v1/transactions` | List user transactions | Yes |
| GET | `/api/v1/transactions/{transactionId}` | Get transaction by ID | Yes |
| POST | `/api/v1/transactions` | Create transaction | Yes |
| PUT | `/api/v1/transactions/{transactionId}` | Update transaction | Yes |
| DELETE | `/api/v1/transactions/{transactionId}` | Delete transaction | Yes |

#### Example: `GET /api/v1/transactions`

Response (200):
```json
[
	{
		"id": 501,
		"amount": 1200.50,
		"merchant": "Amazon",
		"category": "Shopping",
		"transactionDate": "2026-02-15",
		"suggestedCardId": 10,
		"actualCardId": 12
	}
]
```

#### Example: `GET /api/v1/transactions/{transactionId}`

Response (200):
```json
{
	"id": 501,
	"amount": 1200.50,
	"merchant": "Amazon",
	"category": "Shopping",
	"transactionDate": "2026-02-15",
	"suggestedCardId": 10,
	"actualCardId": 12
}
```

#### Example: `POST /api/v1/transactions`

Request:
```json
{
	"amount": 1200.50,
	"merchant": "Amazon",
	"category": "Shopping",
	"transactionDate": "2026-02-15",
	"suggestedCardId": 10,
	"actualCardId": 12
}
```

Response (200):
```json
{
	"id": 501,
	"amount": 1200.50,
	"merchant": "Amazon",
	"category": "Shopping",
	"transactionDate": "2026-02-15",
	"suggestedCardId": 10,
	"actualCardId": 12
}
```

#### Example: `PUT /api/v1/transactions/{transactionId}`

Request:
```json
{
	"amount": 999.00,
	"merchant": "Amazon",
	"category": "Shopping",
	"transactionDate": "2026-02-15",
	"suggestedCardId": 10,
	"actualCardId": 10
}
```

Response (200):
```json
{
	"id": 501,
	"amount": 999.00,
	"merchant": "Amazon",
	"category": "Shopping",
	"transactionDate": "2026-02-15",
	"suggestedCardId": 10,
	"actualCardId": 10
}
```

#### Example: `DELETE /api/v1/transactions/{transactionId}`

Response (204):
```json
{}
```

---

## ai-service (FastAPI)

Base paths are service-relative.

| Method | Endpoint | Description |
|---|---|---|
| GET | `/health` | Health check |
| POST | `/ai/v1/documents/analyze` | Analyze document from S3 metadata |
| POST | `/ai/v1/recommend/rank` | Rank/recommend best card |
| POST | `/ai/v1/embeddings/sync` | Sync/update embedding for rule text |

#### Example: `GET /health`

Response (200):
```json
{
	"status": "UP",
	"service": "ai-service"
}
```

#### Example: `POST /ai/v1/documents/analyze`

Request:
```json
{
	"docId": 101,
	"s3Key": "users/1/docs/statement-jan.pdf",
	"bucket": "cardwiz-profile-images"
}
```

Response (200):
```json
{
	"documentMetadata": {
		"docId": 101,
		"sourceS3": "users/1/docs/statement-jan.pdf",
		"modelUsed": "amazon.nova-2-pro-v1:0"
	},
	"extractedRules": [
		{
			"cardName": "HDFC Millennia",
			"category": "Dining",
			"rewardRate": 5.0,
			"rewardType": "CASHBACK",
			"conditions": "Up to INR 1000 per month"
		}
	],
	"aiSummary": "5% cashback on dining up to INR 1000/month."
}
```

#### Example: `POST /ai/v1/recommend/rank`

Request:
```json
{
	"userId": 1,
	"merchantName": "Starbucks",
	"category": "Coffee",
	"transactionAmount": 450.0,
	"availableCardIds": [10, 12]
}
```

Response (200):
```json
{
	"bestOption": {
		"cardId": 10,
		"cardName": "HDFC Millennia",
		"estimatedReward": "5% Cashback",
		"reasoning": "Highest expected return for coffee transactions.",
		"confidenceScore": 0.92
	},
	"alternatives": [
		{
			"cardId": 12,
			"cardName": "Axis ACE",
			"estimatedReward": "2% Cashback",
			"reasoning": "Good fallback option.",
			"confidenceScore": 0.74
		}
	],
	"semanticContext": "Coffee + dining category reward rules"
}
```

#### Example: `POST /ai/v1/embeddings/sync`

Request:
```json
{
	"ruleId": 901,
	"cardId": 10,
	"contentText": "5% cashback on dining up to INR 1000 per month"
}
```

Response (200):
```json
{
	"status": "synced",
	"ruleId": 901
}
```

---

## Source References

- `user-service/src/main/java/com/cardwiz/userservice/controllers/AuthController.java`
- `user-service/src/main/java/com/cardwiz/userservice/controllers/UserController.java`
- `user-service/src/main/java/com/cardwiz/userservice/controllers/CardController.java`
- `user-service/src/main/java/com/cardwiz/userservice/controllers/TransactionController.java`
- `cardwiz-backend/ai-service/app/main.py`
- `cardwiz-backend/ai-service/app/routes/document_routes.py`
- `cardwiz-backend/ai-service/app/routes/recommendation_routes.py`
- `cardwiz-backend/ai-service/app/routes/embedding_routes.py`
