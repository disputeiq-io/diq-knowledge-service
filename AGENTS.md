# diq-knowledge-service

- **Role**: Rulebook, policy, and precedent ingestion plus hybrid RAG retrieval API
- **Port**: 8084
- **Backing Store**: PostgreSQL + pgvector (RLS)
- **Publishes**: knowledge.source.ingested.v1
- **Consumes**: knowledge.source.uploaded.v1, dispute.case.decided.v1
- **Verification**: `.\gradlew.bat check`

Follow `.agents/skills/disputeiq-engineering/SKILL.md`.