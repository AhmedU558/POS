# ADR-012: The Docker Remote API version is pinned for Testcontainers

**Status:** Accepted — Phase 0
**Date:** 2026-08-25

## Context

The backend test suite could not run. Every `@SpringBootTest` failed while creating the `flyway`
bean with `Could not find a valid Docker environment`, even though `docker version`,
`docker context ls` and `docker run hello-world` all succeeded from the same shell.

Wire-level logging showed what was actually happening. Testcontainers connected to the named pipe
successfully and received a real response from the daemon:

```
opening connection {}->npipe://localhost:2375
http-outgoing-0 << "HTTP/1.1 400 Bad Request"
http-outgoing-0 << "Server: Docker/29.7.2 (linux)"
```

The daemon answered, and rejected the request. Docker Engine 29.7.2 reports
`API version: 1.55 (minimum version 1.40)`; the API version docker-java negotiates by default
falls below that floor, so `/info` is refused before Testcontainers can probe further. The generic
"could not find a Docker environment" message masks this: the environment was found, the handshake
was rejected.

Two hypotheses were tested and eliminated:

- Setting `DOCKER_HOST` to the Docker Desktop Linux engine pipe — same 400.
- Upgrading Testcontainers to 1.21.3 (docker-java 3.4.2) — same 400.

Pinning the API version resolved it on the version of Testcontainers that Spring Boot 3.2.3 already
manages, with no dependency change.

## Decision

`backend/pom.xml` sets a `docker.api.version` property, passed to the test JVM by the Surefire
plugin as the `api.version` system property that Testcontainers reads:

```xml
<docker.api.version>1.44</docker.api.version>
```

1.44 corresponds to Docker Engine 25.0 and sits comfortably above the 1.40 floor of current
engines. Override it for an older daemon:

```
mvn verify -Ddocker.api.version=1.41
```

## Consequences

- No dependency was upgraded. Testcontainers stays at the version Spring Boot 3.2.3 manages,
  honouring the constraint that dependencies change only with documentation-based justification.
- The fix travels with the repository, so it works on every developer machine and in CI rather
  than depending on a per-machine `~/.testcontainers.properties`.
- The pin is a floor, not a ceiling: a newer daemon still serves API 1.44 requests.
- If a future engine raises its minimum above 1.44, this property is the single place to change.
