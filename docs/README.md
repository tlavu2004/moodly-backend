# Moodly Backend Documentation

## Start Here

- [Project README](../README.md): features, setup, public API overview, and everyday commands.
- [Backend Execution Modes](testing/backend-execution-modes.md): host versus container, shared infrastructure, ports, lifecycle, and orphan recovery.
- [Shared Infrastructure Verification](testing/shared-infrastructure-verification.md): current test and migration procedure.
- [Release and Production Deployment](deployment/release.md): verified image identity, Render configuration, promotion, and rollback.

## Architecture and API

- [Module Boundaries](architecture/moodly-architecture.md): package ownership and dependency rules.
- [Shared Development Infrastructure and Test Data Isolation](architecture/shared-infrastructure-isolation.md): reusable architecture and engine/framework alternatives.
- [Committed OpenAPI Contract](api/json/moodly-openapi.json): machine-readable API contract; compare it with code and contract tests before changing FE clients.
- [Client Integration](api/markdown/client-integration.md): habit and journal writes, avatar reset, and search highlights.
- [HTTP Rate Limiting](api/markdown/http-rate-limiting.md): quota behavior and validation.
- [Manual Backend Verification](blueprint/backend-verification-guide.md): hosted-service scenarios requiring dedicated test identities.

## Language and Sources of Truth

Use English for maintained technical documentation, consistent heading capitalization, and normal paragraphs without source-level hard wrapping. Keep command names, API fields, code identifiers, and intentionally localized example/user data unchanged. Do not translate values merely to make a sample look English.

Source code and the committed OpenAPI describe implemented behavior. Env templates document supported configuration without credentials. Current operational runbooks describe commands and lifecycle. Blueprints, migration reports, and old verification snapshots may describe historical milestones; their dated test counts and retired commands are not current acceptance evidence.

Habit `targetFrequency` currently supports `DAILY`. Weekly/monthly statistics periods do not imply additional habit frequencies. No new frequency is implemented by these infrastructure changes.

## Historical Implementation Records

- [Development Blueprint](blueprint/moodly-blueprint.md).
- [Java Tooling Migration](testing/java-tooling-migration.md).
- [Session Ownership Verification](testing/session-ownership-verification.md).
- [Interrupt Exit Handling](testing/interrupt-exit-status.md).

Keep useful evidence, but follow the current execution and verification guides when operating the application. Never commit private env files, tokens, local test-account details, or hosted credentials to documentation.
