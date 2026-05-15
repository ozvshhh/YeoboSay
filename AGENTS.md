# YeoboSay Agent Guide

## Project Overview

YeoboSay is an AI-powered voice conversation service designed for elderly users.

The service focuses on:

- AI phone conversation
- Emotional companionship
- Voice interaction
- Accessibility for senior citizens

This repository uses a monorepo structure.

---

## Directory Structure

- `backend/` — NestJS backend
- `android/` — Android client
- `contract/` — API contracts and shared specifications
- `docs/` — project documentation
- `.github/workflows/` — GitHub Actions CI workflows

---

## Global Agent Rules

Before starting any task:

1. Read this `AGENTS.md` file.
2. Understand the requested task scope.
3. Modify only the directories relevant to the task.
4. Keep changes small and focused.
5. Do not implement unrelated features.
6. Do not commit generated files, secrets, or local-only files.

After implementation:

1. Run the required validation commands.
2. Ensure all relevant CI checks can pass.
3. Keep the PR scope minimal.
4. Explain what changed and how it was tested.

---

## Monorepo Scope Rules

Backend and Android work must be managed separately.

### Backend Tasks

Backend tasks should only modify:

- `backend/`
- `.github/workflows/backend-ci.yml`
- backend-related documentation in `docs/`
- `contract/` only if the API contract changes

Do not modify `android/` during backend-only tasks.

### Android Tasks

Android tasks should only modify:

- `android/`
- `.github/workflows/android-ci.yml`
- android-related documentation in `docs/`
- `contract/` only if the API contract changes

Do not modify `backend/` during Android-only tasks.

### Contract Tasks

Contract tasks should only modify:

- `contract/`
- contract-related documentation in `docs/`
- related CI files if needed

If a task requires changes across backend, Android, and contract, clearly mention that in the PR description.

---

## Backend Stack

The backend uses:

- NestJS
- TypeScript
- Prisma
- PostgreSQL
- npm
- GitHub Actions

---

## Backend Architecture Rules

- Use NestJS module/controller/service structure.
- Keep controllers thin.
- Put business logic inside services.
- Use DTOs for request validation.
- Use `class-validator` and `class-transformer` for validation.
- Use `PrismaService` for database access.
- Never access Prisma directly from controllers.
- Use async/await.
- Write TypeScript strict-friendly code.
- Prefer readable code over clever code.
- Avoid unnecessary abstraction in early-stage development.

---

## Backend Coding Rules

- Do not implement unrelated features in the same PR.
- Keep commits focused and atomic.
- Follow the existing project structure.
- Reuse common utilities when possible.
- Do not remove existing tests unless there is a clear reason.
- Do not bypass failing tests, lint errors, build errors, or Prisma validation errors.
- Do not disable CI checks to force merge a PR.

---

## Local Database Rules

Use Docker Compose for local PostgreSQL when database work is required.

- Do not require developers to install PostgreSQL directly on their machine.
- Local database settings should follow `backend/.env.example`.
- Do not commit `.env`.
- Do not commit real database credentials.
- Use Prisma migrations for schema changes.

Recommended local database flow:

```bash
cd backend
docker compose up -d
npx prisma migrate dev
```

---

## Prisma Rules

- Prefer Prisma ORM APIs.
- Do not generate raw SQL unless explicitly requested.
- Keep Prisma schema normalized.
- Use Prisma migrations for schema changes.
- Do not manually edit generated Prisma client files.
- Do not commit generated Prisma client output.
- Do not access Prisma directly from controllers.

---

## Swagger Rules

- Public backend API endpoints should be documented with Swagger decorators when practical.
- Request and response DTOs should be documented when practical.
- Swagger should remain available at `/api-docs`.

---

## API Design Rules

- Use REST-style endpoint naming.
- Return consistent JSON responses.
- Use proper HTTP status codes.
- Validate all request bodies.
- Keep API responses predictable and typed.
- Do not expose internal implementation details in API responses.

---

## Testing Rules

- Add tests for new endpoints when practical.
- Do not remove existing tests without a clear reason.
- Ensure all relevant tests pass before completing work.
- Prefer focused tests over broad, brittle tests.

---

## Required Backend Commands

Before completing backend work, run:

```bash
cd backend

npm run build
npm run test
npm run lint
npx prisma validate
```

All commands must pass before opening a PR.

---

## CI Rules

All pull requests must pass the relevant GitHub Actions checks.

Backend PRs must pass Backend CI.

Backend CI checks include:

- Install dependencies
- Prisma validation
- Lint
- Test
- Build

Android PRs must pass Android CI.

Contract PRs must pass Contract CI.

Do not bypass:

- failing tests
- failing builds
- lint errors
- Prisma validation errors
- contract validation errors

Never disable CI checks to force merge a PR.

---

## Files Not To Commit

Never commit:

- `.env`
- `.env.*`
- `node_modules/`
- `dist/`
- `coverage/`
- `generated/`
- `backend/generated/`
- local credentials
- API keys
- secrets
- private certificates
- machine-specific config files

Allowed exception:

- `.env.example` may be committed.

---

## Branch Rules

Never commit directly to `main`.

Always create a feature branch.

Allowed branch prefixes:

- `feat/*`
- `fix/*`
- `chore/*`
- `test/*`
- `docs/*`

Examples:

- `feat/backend-foundation`
- `feat/android-call-screen`
- `fix/backend-prisma-config`
- `chore/update-ci`
- `docs/update-agent-guide`

Always open a Pull Request after implementation.

---

## PR Rules

Each PR should:

- solve one focused task
- keep changes scoped
- pass CI
- avoid unrelated refactors
- avoid mixing backend and Android changes unless explicitly required

PR titles should use clear scope labels when possible.

Examples:

- `[BE] Add health endpoint`
- `[BE] Add PrismaService`
- `[Android] Add call screen layout`
- `[Contract] Add health API contract`
- `[Docs] Update setup guide`

PR descriptions should explain:

- what changed
- why it changed
- how it was tested
- whether contract changes were made

---

## Commit Style

Use conventional commits.

Examples:

- `feat: add health endpoint`
- `feat: add prisma module`
- `feat: setup swagger`
- `fix: resolve prisma validation issue`
- `test: add health endpoint test`
- `chore: update backend ci`
- `docs: update agent guide`

---

## Current Backend Foundation Goals

The current backend foundation milestone should focus only on:

- ConfigModule setup
- Global ValidationPipe
- Swagger setup at `/api-docs`
- Health endpoint at `GET /health`
- PrismaModule
- PrismaService
- Docker Compose setup for local PostgreSQL if database connection work is required
- `.env.example` for local backend configuration

Expected health endpoint:

```http
GET /health
```

Expected response shape:

```json
{
  "ok": true,
  "service": "YeoboSay Backend",
  "timestamp": "ISO timestamp string"
}
```

---

## Do Not Implement Yet

Do not implement the following unless explicitly requested:

- authentication
- JWT
- user registration
- login
- OpenAI integration
- STT/TTS
- voice call logic
- WebSocket call sessions
- production deployment
- payment features
- notification features

---

## Suggested Backend Foundation Acceptance Criteria

For backend foundation tasks, the implementation is complete only if:

```bash
cd backend

npm run build
npm run test
npm run lint
npx prisma validate
```

all pass.

Manual checks:

- `GET http://localhost:3000/health`
- `GET http://localhost:3000/api-docs`

---

## Agent Completion Checklist

Before finishing a task, verify:

- The task stayed within scope.
- No unrelated directories were modified.
- No secrets or generated files were committed.
- Required commands passed.
- CI compatibility was preserved.
- The PR title and description are clear.
- The implementation follows this guide.