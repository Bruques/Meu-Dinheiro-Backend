# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build (skipping tests)
./mvnw clean package -DskipTests

# Run locally (uses dev profile with H2 in-memory DB)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=MeudinheiroApplicationTests

# Build Docker image
docker build -t meudinheiro .
```

For local development, set the required env vars before running:
```
GEMINI_API_KEY=...
WHATSAPP_API_PHONE_ID=...
WHATSAPP_API_TOKEN=...
WHATSAPP_APP_SECRET=...
WHATSAPP_VERIFY_TOKEN=...
```
The `dev` profile overrides the datasource to use H2 in-memory, so `SPRING_DATASOURCE_*` vars are not needed locally.

## Architecture

**Spring Boot 4 / Java 17** backend deployed on Render.com (Docker). Database is PostgreSQL on Neon in production; H2 in-memory for local dev.

### Authentication flow
- Firebase Authentication issues JWTs. Spring Security is configured as an OAuth2 resource server that validates tokens against the Firebase issuer URI (`securetoken.google.com/meu-dinheiro-app-b75e2`).
- All `/api/expenses/**` and `/api/users/**` endpoints require a valid Firebase JWT (`@AuthenticationPrincipal Jwt jwt`). The Firebase UID is extracted via `jwt.getSubject()` and used as the row-level ownership key for expenses.
- `/api/whatsapp/**` and `/api/expenses/ping` are public (no token required).

### WhatsApp integration
The WhatsApp webhook (`WhatsappController`) handles two flows:
1. **Account linking** — unlinked numbers send a 6-digit code generated via `AppUserService.iniciarVinculo()`. The code is validated against `AppUser.verificationCode` in the DB.
2. **Expense recording** — linked users send text or audio messages. The controller calls `MeuDinheiroService` to parse them via Gemini AI and saves the resulting `Expense` entities.

Webhook authenticity is verified with HMAC-SHA256 (`X-Hub-Signature-256` header) using `WHATSAPP_APP_SECRET`.

### AI expense parsing (`MeuDinheiroService`)
Calls Gemini 2.5 Flash Lite (`gemini-2.5-flash-lite`) to extract structured expense data from free-form text or audio (base64-encoded OGG). Returns a `List<ExpenseDto>`. The prompt constrains categories to the user's `customCategories` list to prevent hallucinated category names.

### Cash flow date logic
`MeuDinheiroService.calcularFluxoDeCaixa()` computes `dataCobranca` (the actual charge date). For credit card expenses, it applies the user's `diaFechamentoFatura` / `diaVencimentoFatura` to determine whether the charge falls in the current or next billing cycle. Non-credit payments use the purchase date as-is.

### Rate limiting
`RateLimiterService` uses Bucket4j in-memory buckets keyed by user ID:
- AI endpoints (text/audio extraction): 3 requests/minute
- Manual expense save: 20 requests/minute

### Data model
- `AppUser` — PK is `firebaseUid` (String). Holds `whatsappNumber`, `verificationCode`, `customCategories` (stored in `user_categories` join table), and billing cycle days (`diaFechamentoFatura`, `diaVencimentoFatura`).
- `Expense` — owns `userId` (Firebase UID) as a plain string FK. Key fields: `date` (purchase date), `dataCobranca` (cash-flow date), `paymentType`, `category`.

### Profiles
- `application.properties` — production config; reads env vars for DB and external API keys.
- `application-dev.properties` — local H2 override; activates with `-Dspring-boot.run.profiles=dev`.
- `application-prod.properties` — exists but currently empty (production falls back to base properties).

### Deployment
Docker (multi-stage build: Maven → JRE Alpine). JVM heap is capped at 256 MB (`-Xmx256m`) to fit Render's free tier. `GET /api/expenses/ping` hits the DB to prevent Render and Neon from going idle.

### Frontend
The Angular frontend lives in a separate repository and is deployed on Vercel (`meu-dinheiro-web-app.vercel.app`). CORS is explicitly allowed for `localhost:4200` and that Vercel URL.
