# MeuDinheiro — Backend

Spring Boot API powering the [MeuDinheiro iOS app](https://github.com/Bruques/MeuDinheiroiOS)
and its [Angular web client](https://github.com/Bruques/Meu-Dinheiro-Web-App): expense tracking
with a WhatsApp bot front-end, powered by AI expense parsing.

## What it does

- REST API for expenses and users, secured with Firebase-issued JWTs (Spring Security as an
  OAuth2 resource server).
- **WhatsApp integration**: a webhook links a phone number to an account via a 6-digit code,
  then accepts free-form text or voice messages and turns them into structured expenses using
  **Gemini 2.5 Flash Lite**. Webhook payloads are verified with HMAC-SHA256.
- Cash-flow date resolution: for credit card purchases, the actual charge date is computed from
  the user's billing cycle (closing/due day), not just the purchase date.
- Rate limiting per user (Bucket4j) on both the AI-parsing and manual-save endpoints, to keep
  AI API costs bounded.

## Tech stack

Java 17 · Spring Boot 4 · Spring Security (OAuth2 resource server) · PostgreSQL (Neon) / H2 for
local dev · Docker · Gemini API · Meta WhatsApp Cloud API

## Running locally

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Uses an in-memory H2 database in the `dev` profile. Required env vars for AI/WhatsApp features:

```
GEMINI_API_KEY=...
WHATSAPP_API_PHONE_ID=...
WHATSAPP_API_TOKEN=...
WHATSAPP_APP_SECRET=...
WHATSAPP_VERIFY_TOKEN=...
```

## Tests

```bash
./mvnw test
```

Controller and service layers are covered by unit tests (Mockito).

## Deployment

Docker multi-stage build (Maven → JRE Alpine), deployed on Render.com with a Neon Postgres
database. JVM heap capped at 256MB to fit the free tier.
