# Agents

## Backend Integration Workflow

### Scope Authority

The user's request is the authority for what the run is allowed to accomplish.

Repository context may be read to understand the request. Reading a file does not make that file part of the task.

Do not convert discovered repository problems into work unless fixing that exact problem is directly required for the requested outcome.

### Source Authority

This repository implements only the backend of the `Menu` microservice. Domain, requirements, HTTP interface and technology decisions are owned by the documentation repository [FMAT-Restaurant/Menu-Documentation](https://github.com/FMAT-Restaurant/Menu-Documentation), not by this repository.

| Source | Authority over |
| ------ | -------------- |
| [ERS](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/ers/README.md) | Functional requirements (`REQ-MENU-*`), business rules, invariants, non-functional criteria and open questions (`OPEN-*`). |
| [Domain model](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/other/md/domain-model.md) | Entities, relationships and domain semantics. |
| [API contract](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/contracts/api-contract.md) and [OpenAPI](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/contracts/api/openapi.yaml) | HTTP routes, methods, representations and response codes. |
| [Stack](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/stack.md) | Backend technologies and versions (Java 25 / Eclipse Temurin, Spring Boot 4.1.1, Gradle 9.8.0, etc.). |

Agents implement these sources; they never redefine them. A request that needs an undecided or contradictory decision in those sources is a blocker, not an invitation to decide.

### Configuration

Don't forget to update the version in any configuration file (for example `version` in `build.gradle`) or within a document that includes a configuration section. Do this only if that file has been modified (or, in the case of a specific document, if the file or files referenced by that configuration have been modified).

Dependencies and their versions must follow the documented stack. Do not add, remove or upgrade dependencies, plugins or the Gradle wrapper unless the request requires it.

### Antigravity agent

When this workflow is run in Antigravity, use the project agents declared in `.agents/agents/`:

- `analyst`: read-only scope analysis and `IntegrationPlan` production;
- `editor`: one invocation per approved writable target;
- `auditor`: read-only candidate and scope audit.

### Codex agent mapping

When this workflow is run in Codex, use the project agents declared in `.codex/agents/`:

- `analyst`: read-only scope analysis and `IntegrationPlan` production;
- `editor`: one invocation per approved writable target;
- `auditor`: read-only candidate and scope audit.
