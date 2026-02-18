from sqlalchemy import Column, Integer, Text, Index, func
from sqlalchemy.ext.declarative import declarative_base
from pgvector.sqlalchemy import Vector

Base = declarative_base()

class RewardRuleVector(Base):
    __tablename__ = 'reward_rule_vectors'
    
    id = Column(Integer, primary_key=True)
    rule_id = Column(Integer, index=True) 
    card_id = Column(Integer, index=True)
    content_text = Column(Text)
    # Using 1024 dimensions for Nova 2 Multimodal Embeddings
    embedding = Column(Vector(1024))

    # Add an HNSW index for ultra-fast vector search
    __table_args__ = (
        Index(
            'reward_rule_vector_idx',
            embedding,
            postgresql_using='hnsw',
            postgresql_with={'m': 16, 'ef_construction': 64},
            postgresql_ops={'embedding': 'vector_cosine_ops'}
        ),
        Index(
            "reward_rule_vector_fts_idx",
            func.to_tsvector("english", content_text),
            postgresql_using="gin",
        ),
    )
