# Codex Handoff: YeoboSay Backend Foundation

## Project

YeoboSay is an AI-powered voice conversation service for elderly users.

The long-term goal is to build a workflow where AI coding agents can implement code while the human maintainer reviews tests, CI results, and pull requests.

## Repository Structure

This is a monorepo.

- `backend/`: NestJS backend
- `android/`: Android client
- `contract/`: API contracts
- `docs/`: documentation

## Current Backend Stack

- NestJS
- TypeScript
- Prisma
- PostgreSQL
- npm
- GitHub Actions CI

## Current Status

Backend initialization is complete.

Completed:
- GitHub repository initialized
- Backend CI configured
- NestJS backend initialized
- Prisma initialized
- Basic build/test/lint commands available
- `AGENTS.md` added for agent rules
- Backend and Android work should be separated in PRs

Backend CI should run on:
- `backend/**`
- `.github/workflows/backend-ci.yml`

## Important Rules

Read `AGENTS.md` first and follow it strictly.

Do not modify:
- `android/`
- `contract/`
- unrelated docs

unless explicitly requested.

Do not commit:
- `.env`
- `node_modules/`
- `dist/`
- `coverage/`
- `generated/`
- `backend/generated/`

## Current Objective

Implement backend foundation only.

Required implementation:
1. Add global `ConfigModule` support using `@nestjs/config`.
2. Add global `ValidationPipe` in `src/main.ts`.
3. Add Swagger setup in `src/main.ts`.
   - Swagger path: `/api-docs`
   - Title: `YeoboSay API`
   - Description: `AI-powered voice conversation service for elderly users`
   - Version: `0.1.0`
4. Add health endpoint:
   - `GET /health`
   - Response:
     ```json
     {
       "ok": true,
       "service": "YeoboSay Backend",
       "timestamp": "<ISO timestamp>"
     }
     ```
5. Add `PrismaModule`.
6. Add `PrismaService`.
   - Extend `PrismaClient`.
   - Connect on module init.
   - Disconnect on module destroy.
7. Ensure generated Prisma client output is ignored and not committed.
8. Keep scope limited to backend foundation.

## Do Not Implement Yet

Do not implement:
- authentication
- JWT
- users module
- conversations module
- OpenAI integration
- STT/TTS
- voice call logic
- WebSocket call sessions
- production deployment

## Acceptance Criteria

The following commands must pass:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

Manual local checks:

```txt
GET http://localhost:3000/health
Swagger http://localhost:3000/api-docs
```

Expected `/health` response:

```json
{
  "ok": true,
  "service": "YeoboSay Backend",
  "timestamp": "ISO timestamp string"
}
```

## Suggested Branch

Use:

```bash
feat/backend-foundation
```

## Suggested Commit Message

```bash
feat: add backend foundation
```

## Suggested PR Title

```txt
[BE] Add backend foundation
```

## PR Scope

This PR should only include:
- ConfigModule setup
- ValidationPipe setup
- Swagger setup
- Health endpoint
- PrismaModule
- PrismaService
- generated Prisma ignore cleanup if needed