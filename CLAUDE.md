# RAG CRAFT — Project Instructions

## 1. Project Overview

RAG CRAFT is a platform that allows users to create document-grounded
AI chatbots.

Each user can create and manage multiple chatbots. Each chatbot has its
own knowledge base, configuration, documents, integrations, and chat
sessions.

The system uses Retrieval-Augmented Generation (RAG) so chatbot answers
should be grounded in the documents belonging to that chatbot.

---

## 2. Main Technology Stack

### Frontend
- Next.js
- TypeScript
- Tailwind CSS

### Backend / AI Service
- Python
- FastAPI

### Database
- PostgreSQL

### Vector Storage
- pgvector or the vector database configured by the project

### AI
- Ollama
- Local LLM
- Local embedding model

### Infrastructure
- Docker
- Docker Compose

Do not introduce a new framework, database, or major dependency unless
there is a clear requirement for it.

---

## 3. Main System Features

RAG CRAFT includes:

- User authentication
- User-based chatbot ownership
- Multiple chatbots per user
- Knowledge base management
- Document upload and processing
- RAG document retrieval
- Source citations
- Chatbot configuration
- Admin chat/playground
- Public chatbot share links
- Telegram integration
- Web/iframe integration
- Analytics

Some features may not be implemented yet.

Always inspect the existing project before assuming a feature exists.

---

## 4. User-Based Multi-Tenancy

RAG CRAFT uses user-based ownership.

A user can own multiple:

- Chatbots
- Knowledge bases
- Documents
- Integrations
- Chat sessions

Core resources should be associated with their owner where appropriate.

Example:

user
  └── chatbot
        ├── configuration
        ├── knowledge base
        │     └── documents
        ├── integrations
        └── conversations

Never allow one user to access another user's private resources.

Ownership and authorization must be checked on the backend.

Do not rely only on frontend restrictions for security.

---

## 5. Knowledge Base Rules

Each chatbot should have an isolated knowledge base.

Supported document types may include:

- PDF
- DOCX
- TXT
- Markdown
- XLSX
- Web content

The general ingestion flow is:

Document
    ↓
Parse / Extract Text
    ↓
Clean Text
    ↓
Chunk
    ↓
Generate Embeddings
    ↓
Store Vectors
    ↓
Attach Metadata

Metadata should make it possible to identify:

- User
- Chatbot
- Document
- Source
- Chunk

Retrieval must not return documents belonging to another user's chatbot.

---

## 6. RAG Flow

The expected RAG flow is:

User Question
    ↓
Validate User / Chatbot Access
    ↓
Process Query
    ↓
Retrieve Relevant Chunks
    ↓
Build Context
    ↓
Send Context + Question to LLM
    ↓
Generate Answer
    ↓
Return Answer + Source Citations

The chatbot should answer using the retrieved knowledge whenever the
question depends on uploaded documents.

Do not fabricate citations.

A citation must point to an actual retrieved source.

---

## 7. Chatbot Configuration

Each chatbot may have its own configuration, including:

- Name
- Description
- System instructions
- Tone
- LLM/model
- Answer length
- Starter questions

Configuration belonging to one chatbot must not affect another chatbot.

---

## 8. Deployment Channels

A chatbot may be accessed through:

### Website / Admin Chat
Used by the chatbot owner to test the chatbot.

### Telegram
Telegram communicates with the backend through a bot integration,
normally using webhook-based communication.

### Web Embed
A chatbot may be embedded into another website using an iframe or
supported web integration.

### Public Share Link
The chatbot owner can generate a public link.

Example concept:

/share/{public_token}

A visitor with the link can chat with the published chatbot without
receiving administrative access to the chatbot configuration or
knowledge base.

Public endpoints must have appropriate security and rate limiting.

---

## 9. API Rules

When creating or modifying APIs:

- Use clear REST endpoints.
- Validate request data.
- Return appropriate HTTP status codes.
- Handle errors consistently.
- Keep business logic out of route/controller code when possible.
- Separate API, service, database, and AI/RAG responsibilities.
- Check authorization for protected resources.
- Never expose secrets in API responses.

---

## 10. Security Rules

Never:

- Hard-code passwords
- Hard-code API keys
- Commit secrets
- Expose database credentials
- Expose private document content without authorization
- Trust user-provided resource IDs without ownership validation

Use environment variables for secrets.

Example:

DATABASE_URL
SECRET_KEY
OLLAMA_BASE_URL
TELEGRAM_BOT_TOKEN

`.env` files containing real secrets should not be committed to Git.

---

## 11. Coding Rules

When modifying the project:

1. Inspect the existing code first.
2. Follow the existing architecture.
3. Keep changes small and focused.
4. Do not rewrite unrelated files.
5. Reuse existing functions and services where possible.
6. Avoid unnecessary dependencies.
7. Use meaningful variable and function names.
8. Add validation where necessary.
9. Handle expected errors.
10. Keep code understandable for students and team members.

Do not over-engineer simple requirements.

---

## 12. AI Assistant Workflow

For every significant task, follow this workflow:

Understand
    ↓
Inspect
    ↓
Plan
    ↓
Review
    ↓
Implement
    ↓
Test
    ↓
Debug
    ↓
Verify

### Step 1 — Understand

Restate the requirement and identify the expected result.

Do not start coding if the requirement is unclear.

### Step 2 — Inspect

Before modifying code:

- Read this CLAUDE.md
- Inspect the project structure
- Read relevant existing files
- Check existing dependencies
- Check existing database models/schema
- Check existing APIs/services
- Identify reusable code

Do not assume files or features exist.

### Step 3 — Plan

Before making a major change, explain:

- What needs to change
- Which files need to change
- Whether new files are required
- Database changes
- API changes
- Security implications
- How the change will be tested

Do not implement yet when explicitly asked for a plan.

### Step 4 — Review

Allow the developer to review the proposed approach before major
implementation.

### Step 5 — Implement

After approval:

- Make the smallest necessary changes
- Follow existing architecture
- Avoid unrelated refactoring

### Step 6 — Test

Run relevant tests and checks.

Examples:

- Unit tests
- API tests
- RAG retrieval tests
- Authorization tests
- Build checks

### Step 7 — Debug

If something fails:

1. Read the actual error.
2. Identify the likely cause.
3. Inspect the relevant code/configuration.
4. Apply a focused fix.
5. Run the test again.

Do not randomly modify multiple files to make an error disappear.

### Step 8 — Verify

Before considering a task complete:

- Confirm the requested feature works.
- Confirm existing important functionality still works.
- Review changed files.
- Check for accidental secret exposure.
- Summarize what changed.

---

## 13. Git Rules

Use Git to keep changes reviewable.

Before major work:

git status

After implementation:

git diff

Only commit files related to the task.

Use clear commit messages.

Examples:

feat: add document upload API

feat: add public chatbot share link

fix: enforce chatbot ownership

fix: prevent cross-user document retrieval

test: add RAG retrieval tests

docs: update project instructions

Do not commit:

- .env
- passwords
- API keys
- tokens
- temporary files
- generated files that should be ignored

---

## 14. Important Rule

AI-generated code must not be accepted blindly.

The developer remains responsible for:

- Reviewing changes
- Approving implementation
- Running tests
- Checking security
- Verifying the final behavior

The AI proposes and assists.

The developer decides.