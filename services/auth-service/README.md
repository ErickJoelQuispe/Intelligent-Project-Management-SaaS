# auth-service

Hexagonal microservice responsible for account registration and authentication,
built with Java 21 and Spring Boot 3.5.

## Known technical debt (conscious, non-blocking)

The items below are **conscious, documented tradeoffs** — not defects. They are
non-blocking for the current release and are listed here so the team can
address them deliberately rather than stumble on them later.

### Keycloak admin client rebuilt per call

`KeycloakAdminAdapter.buildAdminClient()` creates a new `Keycloak` instance on
every outgoing call, which means a fresh token acquisition (client-credentials
flow) on each operation. For low-volume use this is acceptable, but at scale it
adds unnecessary latency and token-server pressure.

**Deferred because**: converting to a singleton/pooled bean requires proper
token-refresh logic and thread-safety analysis (the Keycloak admin client is not
documented as thread-safe). The work is non-trivial and carries concurrency risk.

### Third-party UUIDv7 dependency inside the domain layer

`Account`, `AccountRegisteredEvent`, and `SecurityEvent` import
`com.github.f4b6a3.uuid.UuidCreator` directly for UUIDv7 generation. This
introduces a third-party library coupling at the domain layer, which should
ideally contain no infrastructure or library dependencies.

**Accepted because**: the coupling is limited to ID generation and the library
is stable. If strict hexagonal purity is required in the future, this can be
abstracted behind a domain `IdGenerator` port with an infrastructure-layer
implementation.

> **Note**: the existing `ArchitectureTest` (ArchUnit) does not currently
> enforce that the domain layer is free of `com.github.f4b6a3` imports.
> Adding that rule to the arch test is a recommended follow-up.

### Circuit-breaker coverage gap for `KeycloakAdminAdapter`

`KeycloakCircuitBreakerTest` exercises the Resilience4j circuit-breaker at the
library level but does not spin up a Spring application context or use WireMock
to simulate a real Keycloak endpoint going down. A proper integration test
(`@SpringBootTest` + WireMock) would prove the circuit-breaker actually opens
and recovers under realistic HTTP failure scenarios.

**Deferred because**: a `TODO` is already present in `KeycloakCircuitBreakerTest`
tracking this gap. It is lower priority than end-to-end contract tests.
