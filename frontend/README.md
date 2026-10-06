# RAG Craft V2 frontend demo

Next.js, TypeScript, Tailwind, and the existing Lucide/Recharts UI. No backend, database, AI server, email service, or Telegram API is required.

## Run locally

```sh
cd frontend
npm ci
npm run dev
```

Open http://localhost:3000. If dependencies are already installed, start with `npm run dev`.

Fixed demo accounts:

| Role | Email | Password |
| --- | --- | --- |
| Admin | admin@gmail.com | 123 |
| User | user@gmail.com | 123 |

Registered users receive USER access and sign in after registration. Fixed account emails cannot be changed, deleted, or disabled. Their original password `123` remains available even after a demo password update.

## Demo walkthrough

1. Sign in as User, or register an account and sign in.
2. Create a chatbot; configure its model label, instructions, style, welcome/fallback, citations, and starter questions.
3. Upload PDF, DOCX, TXT, Markdown, or XLSX files, or add a website URL. Watch simulated processing; reload to confirm recovery. Files can be replaced, reprocessed, inspected, and deleted.
4. Test in the Playground, including Draft chatbots. Select a temporary model or compare two model labels. Responses and citations are illustrative; uploaded file contents are not used to generate answers.
5. Activate the chatbot. Configure Channels: public links with optional passwords/expiry, widget appearance/domain lists and live preview, or simulated Telegram connections using a placeholder token.
6. Open the generated public link in another tab in the same browser. Regenerating it invalidates the old token. Guest conversation state is kept in the current page and cleared by New Chat.
7. View Analytics for counts derived from local events. Each exchange adds two messages. The refreshed dashboard includes seven chart views, a 7/14/30-day activity selector, and an explicit Sample preview switch. Sample figures are never stored or mixed into account data.
8. Sign in as Admin to inspect all local resources, filter/manage registered users, open monitored chatbot details, and inspect honest operations statuses.
9. Edit your profile or use Settings → Reset Demo Data to restore samples and return to sign in.

## Browser persistence and limitations

Data uses namespaced `ragcraft:v2:*` localStorage keys through UI → service → repository → storage. Uploaded original files use IndexedDB (`ragcraft:v2:files`). Profile pictures are limited to 500 KB and stored with profile data. Processing uses saved timestamps, so it resumes after refresh without a worker.

Browser storage is specific to the browser profile **and origin**. A localhost profile and a Vercel URL have separate data. Another device or browser cannot see a chatbot/link you created. A teammate can open the deployed app, sign in with fixed credentials, and run the complete workflow using their own local records. Public links work for records present in that same browser/origin. Cross-device publishing requires the future backend.

The widget snippet demonstrates embedding and includes appearance settings. It does not provide production publishing or enforce allowed domains; third-party browser storage restrictions can prevent an embedded frame from seeing local records. Use the live preview and same-origin guest route for the demo. Telegram tokens are never retained or sent to Telegram; use placeholder values. QR display is omitted because no QR library was installed.

Passwords and link passwords are intentionally stored in plain text for this demo. Client-side permissions are not production security. No real document extraction, chunking, indexing, retrieval, generation, crawling, or email delivery occurs. Seed sources contain metadata only; uploaded originals can be opened from IndexedDB.

## Verification

```sh
npm run lint
npx tsc --noEmit --incremental false
npm run test:demo
npm run build
```

The smoke script exercises actual services with isolated browser/file adapters, covering auth, profile persistence, user isolation, admin guards, chatbot lifecycle, upload/processing recovery, website sources, chat/compare, analytics, guest passwords/expiry, token invalidation, account deletion, and namespaced reset. It does not touch your browser's saved data. IndexedDB and visual browser behavior require a browser check.

## Vercel

Import the repository and set **Root Directory** to `frontend`. Use the Next.js preset and the default build command `npm run build`. No backend environment variables are needed. Do not copy backend `.env` secrets into Vercel. `output: "standalone"` also supports the existing Dockerfile; it is not a static HTML export.

## Future integration

Replace repositories/services with HTTP implementations while preserving their typed contracts. Ownership, chatbot/source/channel relationships, IDs, and creation/update timestamps are explicit. Existing snake_case fields are retained for compatibility with the original UI; ownership uses `ownerId`.
