# Agent Engineering Instructions — GamePulse

> **MANDATORY INSTRUCTIONS FOR ALL AI CODING AGENTS (Antigravity, Copilot, Cursor, Claude, etc.)**  
> **Source of Truth**: The local Git repository is the persistent source of truth. Chat context is transient.

---

## 1. Startup Protocol (Execute First on Every New Turn/Session)

Before answering or modifying files, follow this sequence:
1. **Read Brain Entry Point**: Open [`project-brain/PROJECT_BRAIN.md`](project-brain/PROJECT_BRAIN.md).
2. **Read Current State**: Open [`project-brain/CURRENT_STATE.md`](project-brain/CURRENT_STATE.md) for latest verified commits, PRs, and test evidence.
3. **Read Session Handoff**: Open [`project-brain/SESSION_HANDOFF.md`](project-brain/SESSION_HANDOFF.md) for the exact next task and instructions left by the preceding session.
4. **Inspect Local Git Reality**:
   ```bash
   git status
   git branch -v
   git log -n 5 --oneline
   ```
5. **Inspect Remote State**:
   ```bash
   gh pr list --state all
   gh run list -L 3
   ```
6. **Resume Work**: Proceed directly with the highest-priority incomplete task identified in `SESSION_HANDOFF.md`.

---

## 2. Core Engineering Rules of Engagement

1. **Verify Before Claiming**: Never claim that code compiles, tests pass, or a PR is created/merged without CLI verification output.
2. **Reuse Existing Code**: Never rewrite or delete working features. Inspect existing classes and repositories in `src/main/java/com/gamepulse/` before creating new ones.
3. **Package Namespace**: The root package is strictly `com.gamepulse.*`. Never introduce vendor-specific or third-party project prefixes.
4. **Preserve User Changes**: Never run `git reset --hard`, `git checkout .`, `git clean -fd`, or force-push without explicit user authorization.
5. **No Direct Commits to `main` for Features**: Every implementation milestone must use a dedicated feature branch (`feat/<name>`), pass local tests, and be submitted via GitHub PR following [`project-brain/GITHUB_WORKFLOW.md`](project-brain/GITHUB_WORKFLOW.md).
6. **Zero Fabrication**: Do not invent test counts, benchmark numbers, fake URLs, or mock CI statuses.
7. **Human Engineering Tone**: All git commit messages, PR descriptions, and code comments must read naturally as a professional senior software engineer (no bot/AI language).

---

## 3. Essential Verification Commands

```bash
# Full test suite execution (runs 38 automated tests)
./mvnw clean test          # Linux / macOS
.\mvnw.cmd clean test      # Windows PowerShell

# Run concurrency and idempotency integration tests only
./mvnw test -Dtest=ConcurrencyIntegrationTest,IdempotencyIntegrationTest

# Run smoke test & verify OpenAPI documentation generation
./mvnw test -Dtest=GamePulseApplicationTests

# Run local application with embedded PostgreSQL-compatible database (port 8080)
./mvnw spring-boot:run

# Verify Git status & commit log
git status
git log -n 5 --oneline
```

---

## 4. Maintenance Directive

After completing any implementation milestone or meaningful change:
1. Update [`project-brain/CURRENT_STATE.md`](project-brain/CURRENT_STATE.md) with latest commit and test results.
2. Update [`project-brain/IMPLEMENTATION_LEDGER.md`](project-brain/IMPLEMENTATION_LEDGER.md) with feature status.
3. Update [`project-brain/SESSION_HANDOFF.md`](project-brain/SESSION_HANDOFF.md) with exact instructions for the next agent.

