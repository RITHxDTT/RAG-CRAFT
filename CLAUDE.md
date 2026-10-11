# RAG CRAFT — Claude Instructions

## 1. Project

RAG Craft is a platform for creating document-grounded AI chatbots.

Users can create multiple chatbots. Each chatbot has its own:

- Configuration
- Knowledge sources
- Documents
- Channels
- Conversations
- Analytics

Resources belong to their owner and must remain isolated between users.

Before making changes, inspect the existing implementation. Do not assume a feature is missing or complete.

---

## 2. Current Development Phase

The project is currently in the **V2 Frontend Demo phase**.

The purpose is to provide a functional demo that can be deployed to Vercel and used by the team to understand the complete UI and system workflow.

### Current Stack

- Next.js
- TypeScript
- Tailwind CSS
- localStorage
- IndexedDB when larger browser storage is required
- Vercel deployment

### Current Demo Accounts

ADMIN:

- Email: `admin@gmail.com`
- Password: `123`
- Role: `ADMIN`

USER:

- Email: `user@gmail.com`
- Password: `123`
- Role: `USER`

These credentials are intentionally hard-coded for the demo only.

### Data source modes

The frontend runs in one of two modes (`NEXT_PUBLIC_DATA_SOURCE`, see `docs/database-mode.md`):

- `local` (default): everything in the browser, as described in this file. The Vercel demo uses this.
- `api`: accounts, chatbots, catalog, documents and channels come from the Spring microservices (`services/`) and PostgreSQL `craftrag_db`
  (schemas in `database/craftrag_schema.sql`). This was explicitly requested; the "do not introduce PostgreSQL / backend" rules below apply to `local` mode.

Both modes sit behind the same service interfaces (`src/services/*.service.ts` pick `api/*.api.ts` or the local implementation), so UI components do not know which is active.

Users may also register accounts.

Registered accounts:

- Receive the `USER` role
- Are stored locally
- Can sign in after registration

Do not treat the current authentication implementation as production security.

---

## 3. Current Demo Architecture

Use:

```text
UI
 ↓
Service
 ↓
Repository / Storage
 ↓
localStorage / IndexedDB
```

Do NOT access `localStorage` throughout UI components.

Keep storage behind services/repositories so it can be replaced later.

Example:

```text
Chatbot UI
    ↓
ChatbotService
    ↓
ChatbotRepository
    ↓
localStorage
```

---

## 4. Current Demo Scope

The current application may include:

- Sign In
- Sign Up
- Forgot Password simulation
- Admin/User roles
- Profile management
- Chatbot CRUD
- Chatbot configuration
- Knowledge Base UI
- Mock document processing
- Mock chunking/indexing
- Playground
- Mock AI responses
- Mock citations
- Compare mode
- Public Share Link
- Guest Chat
- Web Widget
- Telegram configuration
- Analytics
- Admin management
- System status UI

When implementing a task, inspect the existing code first and only add/fix what is necessary.

---

## 5. Demo Data Rules

Normal demo data may be stored in `localStorage`.

Examples:

- Users
- Session
- Profiles
- Chatbots
- Settings
- Knowledge-source metadata
- Channels
- Conversations
- Analytics

Prefer IndexedDB for larger files or binary data.

Use namespaced storage keys, for example:

```text
ragcraft:v2:users
ragcraft:v2:session
ragcraft:v2:chatbots
ragcraft:v2:knowledge
ragcraft:v2:channels
ragcraft:v2:conversations
ragcraft:v2:analytics
```

Remember that browser storage is device/browser specific.

Do not implement cross-device synchronization during this phase.

---

## 6. Ownership

Even in demo mode, design data for future multi-tenancy.

Important records should include ownership where appropriate.

Example:

```json
{
  "id": "bot_001",
  "ownerId": "user_001",
  "name": "Company Assistant"
}
```

USER:

- Can access their own resources.

ADMIN:

- Can view/manage platform-level demo resources.

Do not mix data belonging to different users.

---

## 7. Mock RAG

Do NOT implement real AI infrastructure unless explicitly requested.

Current RAG behavior is simulated.

Example document flow:

```text
Upload
 ↓
Processing
 ↓
Chunking
 ↓
Indexing
 ↓
Ready
```

These states are for demonstrating the intended workflow.

Mock data may include:

- Chunk count
- Retrieved sources
- AI responses
- Citations
- Processing status

Do not claim mock behavior is real AI processing.

---

## 8. Channels

Current supported channel UI:

- Public Share Link
- Web Widget
- Telegram

Channel behavior may be simulated where backend infrastructure is not available.

### Public Link

Concept:

```text
/share/{chatbotSlug}/{token}
```

Guests can access published chatbots without administrative access.

### Web Widget

Support configuration such as:

- Appearance
- Position
- Welcome message
- Allowed domains
- Preview
- Generated embed code
- Enable/disable

### Telegram

Telegram connection is currently simulated unless explicitly implementing the real integration.

Never place real Telegram secrets in source code.

---

## 9. Future Production Architecture

The target architecture is different from the current demo.

Future:

```text
Next.js
   ↓
API Gateway / Ingress
   ↓
FastAPI Services
   ↓
├── PostgreSQL
├── Qdrant
└── Ollama / LLM
```

Production will eventually include:

- FastAPI
- PostgreSQL
- Qdrant
- Ollama
- Embedding model
- Real document processing
- Real RAG retrieval
- Real citations
- Secure authentication
- Real integrations
- Kubernetes infrastructure

Do NOT introduce these into the current demo unless explicitly requested.

Code the current frontend so migration to this architecture is straightforward.

---

## 10. Future RAG Flow

When real RAG is implemented, follow:

```text
Question
 ↓
Validate User / Chatbot
 ↓
Retrieve Relevant Chunks
 ↓
Build Context
 ↓
LLM
 ↓
Answer + Citations
```

Each chatbot must have an isolated knowledge base.

Retrieval must be scoped by chatbot/owner.

Never fabricate real citations.

---

## 11. Coding Rules

When modifying the project:

1. Inspect existing code first.
2. Follow existing architecture.
3. Reuse existing components/services.
4. Make focused changes.
5. Avoid unrelated refactoring.
6. Avoid unnecessary dependencies.
7. Use TypeScript types/interfaces.
8. Keep business logic outside UI components where practical.
9. Keep storage access behind repositories/services.
10. Handle loading, errors, and empty states.
11. Preserve responsive design.
12. Do not over-engineer simple demo requirements.

Do not rebuild working features unnecessarily.

---

## 12. Security

### Demo Phase

Fixed credentials and browser storage are intentionally allowed for the current demo.

They must NOT be considered production security.

### Production

Never:

- Store plaintext passwords
- Hard-code production credentials
- Commit API keys/tokens
- Expose database credentials
- Expose private documents
- Trust client-side authorization alone

Production secrets must use environment variables.

---

## 13. Claude Workflow

For significant tasks:

```text
Understand
 ↓
Inspect
 ↓
Plan
 ↓
Implement
 ↓
Test
 ↓
Verify
```

### Inspect

Before changing code:

- Read relevant existing files
- Check existing components
- Check existing services
- Check dependencies
- Identify reusable implementation

### Plan

For major changes, briefly explain:

- What will change
- Files affected
- New files if required
- Important architecture decisions

If explicitly asked to provide a plan first, do not implement until approved.

### Implement

- Make the smallest appropriate changes.
- Do not rewrite unrelated code.
- Follow the existing design system.

### Test

Run relevant:

- Type checks
- Lint
- Build
- Tests

Test the changed workflow.

### Verify

Before finishing:

- Confirm requested behavior works
- Check related functionality
- Review changed files
- Check for exposed secrets
- Summarize changes

---

## 14. Git

Before significant work:

```bash
git status
```

After changes:

```bash
git diff
```

Do not commit:

- `.env`
- Tokens
- API keys
- Passwords except intentional demo credentials
- Temporary files

Keep commits focused.

---

## 15. Detailed Requirements

Do NOT place every feature specification in this file.

`CLAUDE.md` contains stable project rules only.

When a task has a detailed specification, follow the user's current prompt or the relevant documentation in the repository.

Priority:

```text
Current User Requirement
        ↓
CLAUDE.md Project Rules
        ↓
Existing Project Architecture
```

If the current requirement conflicts with an old implementation, explain the conflict before making a major architectural change.

---

## 16. Most Important Rules

1. Current phase = **V2 Frontend Demo**.
2. Deployment target = **Vercel**.
3. Current persistence = **localStorage / IndexedDB**.
4. Fixed Admin/User accounts are intentionally allowed.
5. Do not require PostgreSQL for the current demo.
6. Do not require FastAPI for the current demo.
7. Do not require Qdrant or Ollama for the current demo.
8. Keep code ready for future backend migration.
9. Preserve user/chatbot ownership boundaries.
10. Inspect before changing code.
11. Do not unnecessarily rewrite working code.
12. Do not over-engineer the demo.