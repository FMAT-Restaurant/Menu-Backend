# Surgical editor task

## Only writable target

- `README.md`

No other file may be created, modified or deleted. `AGENTS.md` and `.gitignore` are protected. Do not touch `build.gradle`, `settings.gradle`, `gradle/wrapper/*`, `Dockerfile`, `.github/workflows/ci.yml` or `sonar-project.properties`, and do not bump any `version` (plan decision D2).

## Instructions

- In `README.md`, section "Tecnologías", replace line 36 exactly:
  - from: ``| Maven | Wrapper incluido (`mvnw`) | No es necesario instalar Maven por separado |``
  - to: ``| Gradle | 9.8.0 (wrapper incluido, `gradlew`) | No es necesario instalar Gradle por separado |``
- In `README.md`, replace line 38 exactly:
  - from: ``> **Importante:** todo el equipo debe usar la misma versión de Java. La versión del proyecto queda fijada en el `pom.xml`.``
  - to: ``> **Importante:** todo el equipo debe usar la misma versión de Java. La versión del proyecto queda fijada en el `build.gradle` (toolchain de Java 25, Eclipse Temurin).``

Source of authority: docs/stack.md, Backend table (`Gradle | 9.8.0`, `Eclipse Temurin | 25.0.4.1`, `Java | 25 LTS`).

## Mandatory behavior

1. Verify that `README.md` contains no remaining occurrence of `Maven`, `mvnw` or `pom.xml`.
2. Keep every other line of `README.md` byte-identical. This covers the user-edited Java row (`| Java | 25 (LTS) o superior | ... |`), `- **JDK 25 o superior** instalado ...`, the Spring Boot row, "Documentación relacionada" and the whole "CI/CD" section.
3. Do not add a web/HTTP starter or edit `EXPOSE 8080`. That is plan blocker WEB-STACK and belongs to the documentation repository.
4. Do not perform any tests or builds.
