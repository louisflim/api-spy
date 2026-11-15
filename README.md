# API Spy

**A 2D top-down spy-themed educational game for learning REST API concepts through interactive missions.**

API Spy is an undergraduate Information Technology capstone project developed at **Cebu Institute of Technology–University (CIT-U)**. Players take the role of an agent who explores security facilities, accesses simulated API terminals, constructs HTTP requests, and unlocks mission objectives. The project combines introductory API education with retro-style puzzle and action gameplay.

> **Development status:** Work in progress. Module 1 Recruit Levels 1–6 are implemented according to the latest development report. The remaining missions, higher ranks, bosses, complete rewards, and research reporting are still in development. Not every feature has undergone a full end-to-end browser playtest.

## Features

- **Training Academy (Module 0):** Ungraded introductory lessons about API concepts and using the game.
- **Mission preparation:** Mission-specific lessons and briefings before independent API challenges.
- **API terminals:** Construct fictional HTTP requests using methods, paths, parameters, headers, and bodies where applicable; receive simulated server responses. Requests do **not** target arbitrary external URLs.
- **Learning feedback:** Terminal Verification Progress (TVP), concept-aware CONTROL feedback, and adaptive practice opportunities.
- **Practice Mode:** Optional, ungraded API drills without research KPI contributions.
- **2D gameplay:** Phaser-based top-down movement, interactive security terminals, doors, authored room footprints, seeded object placement, and tactical minimaps.
- **Combat foundation:** Rookie Detective, three-heart health, a reusable Security Patrol Guard, and a starter ranged weapon.
- **Progress and replay:** Persisted mission completion, prerequisites, escalating mandatory API-device requirements, physical-exit gameplay, and replay runs that retain career progress.
- **Accounts:** Custom Spring Security authentication with PostgreSQL-backed persistence and protected frontend routes.

## Learning structure

| Content | Status |
| --- | --- |
| Module 0 — Training Academy | Implemented; ungraded orientation |
| Module 1 — Recruit Levels 1–6 | Implemented |
| Module 1 — Recruit Level 7 | Planned |
| Module 1 — Field Agent / Special Agent ranks | Planned |
| Module 2 — JSON, headers, responses | Planned |
| Module 3 — Authentication and linked API workflows | Planned |

The intended curriculum includes **3 modules × 7 levels × 3 difficulty ranks = 63 mission configurations**. This is the planned scope, **not** a claim that all 63 are currently playable.

## Tech stack

| Layer | Technology |
| --- | --- |
| Frontend | React, TypeScript, Vite, React Router |
| Game engine | Phaser / Arcade Physics |
| Backend | Java 21, Spring Boot, Maven |
| Security | Spring Security, custom account authentication |
| Persistence | Supabase-hosted PostgreSQL, Spring Data JPA, Flyway |
| Tests | Java test suite and frontend room/combat self-tests |

**Supabase is used for PostgreSQL hosting only, not for Supabase Auth.** The Java backend handles authentication and validation.

## Repository structure

```text
api-spy/
├── backend/                 # Spring Boot REST API, auth, persistence, migrations, tests
│   ├── src/
│   ├── .env.example          # Example configuration; no real credentials
│   ├── mvnw.cmd
│   └── run-with-env.ps1
├── frontend/                # React frontend and Phaser gameplay
│   ├── public/assets/       # Game artwork and spritesheets
│   ├── src/
│   └── package.json
└── README.md
```

## Running locally (Windows / PowerShell)

**Prerequisites:** Java 21, Node.js and npm compatible with the project's current Vite version, and access to a PostgreSQL database. Maven wrapper scripts are included.

1. **Configure the backend:**

   ```powershell
   cd backend
   Copy-Item .env.example .env
   ```

   Edit `backend/.env` and supply your own `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Use the connection values from your own database provider; never commit real credentials.

2. **Start the backend** in its terminal:

   ```powershell
   .\run-with-env.ps1
   ```

   The Java API normally runs at `http://localhost:8080`. Flyway manages database migrations.

3. **Start the frontend** in another terminal from the repository root:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

   Open `http://localhost:5173`. Vite proxies `/api` requests to the local backend.

4. **Run verification commands** (in the relevant directories):

   ```powershell
   # backend/
   .\mvnw.cmd test
   ```

   ```powershell
   # frontend/
   npm run build
   npm run lint
   ```

## Gameplay and research integrity

API Spy is designed to keep **independent graded mission attempts** separate from ungraded practice and mission replay. The research KPIs are:

- **SCR** — Scenario Completion Rate
- **RCA** — Request Construction Accuracy
- **HLCR** — Hard-Level Completion Rate

The precise eligibility rules and denominators should follow the approved study methodology. Player-facing TVP is **not** a substitute for these research measures. Researcher dashboards, finalized KPI aggregation, and authorized exports are planned work.

## In development

- Complete Module 1 Level 7 and the two higher difficulty ranks.
- Build out Modules 2 and 3.
- Extend reusable enemies and weapons; add unique module-ending bosses.
- Implement full rewards, inventory, cosmetics, and account-management screens.
- Complete end-to-end browser testing, KPI reporting, and production deployment.

## Security and contributor notes

- **Never commit** `.env`, `.env.local`, passwords, tokens, or private database details.
- Use the provided `.env.example` as a template and configure your own credentials.
- Keep simulated API challenges fictional; do not send player-entered request URLs to external services.
- Avoid changing immutable research first-attempt evidence when replaying missions.

## Project status

**Academic capstone project — actively under development.** Features and documentation may evolve during implementation and testing.
