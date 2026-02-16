ai-service/
│
├── app/
│   ├── main.py
│   ├── config.py
│   ├── dependencies.py
│   │
│   ├── routes/
│   │   ├── document_routes.py
│   │   └── recommendation_routes.py
│   │
│   ├── services/
│   │   ├── document_service.py
│   │   ├── embedding_service.py
│   │   └── recommendation_service.py
│   │
│   ├── schemas/
│   │   ├── document_schema.py
│   │   └── recommendation_schema.py
│   │
│   └── clients/
│       └── bedrock_client.py
│
├── requirements.txt
├── Dockerfile
└── .env
