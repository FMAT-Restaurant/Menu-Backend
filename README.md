# Menu Backend

[![CI](https://github.com/FMAT-Restaurant/Menu-Backend/actions/workflows/ci.yml/badge.svg)](https://github.com/FMAT-Restaurant/Menu-Backend/actions/workflows/ci.yml)

Microservicio de **menú** del sistema de comandas de **FMAT Restaurant**. Implementa el bounded context `Menu`: catálogo, ofertas y sus opciones, recetas y modificadores que consume el flujo de comandas.

El dominio, los requisitos y las decisiones de arquitectura viven en [Menu-Documentation](https://fmat-restaurant.github.io/Menu-Documentation/). El cliente web está en [Menu-Frontend](https://github.com/FMAT-Restaurant/Menu-Frontend).

**Stack:** Java 25 (Temurin) · Spring Boot 4.1 · PostgreSQL 18.6 · RabbitMQ 4.3.6 · Gradle 9.8 (wrapper incluido).

## Requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) en ejecución (Compose v2).
- JDK 25 solo si vas a compilar o ejecutar las pruebas fuera de Docker; el toolchain de Gradle lo descarga si falta.

## Levantar con Docker Compose

```bash
git clone https://github.com/FMAT-Restaurant/Menu-Backend.git
cd Menu-Backend

cp .env.example .env                        # PowerShell: Copy-Item .env.example .env
# Editar .env y definir POSTGRES_PASSWORD (obligatoria)

docker network create restaurant-network    # solo la primera vez
docker compose up --build
```

Esto levanta el API, PostgreSQL y RabbitMQ; el API espera a que ambos estén sanos.

- API: <http://localhost:8080>
- Contrato OpenAPI: <http://localhost:8080/openapi.yaml>

Para detenerlo: `docker compose down` (agregar `-v` borra también los datos de PostgreSQL).

### Variables de entorno

Todas están documentadas en [`.env.example`](.env.example); la única obligatoria es `POSTGRES_PASSWORD`. Lo que no es evidente:

- **`restaurant-network`** es una red externa compartida con los demás microservicios. Si no existe, `docker compose up` falla.
- **`POSTGRES_PASSWORD` solo aplica al crear el volumen.** Si cambias la contraseña con un volumen existente, cámbiala también en PostgreSQL (`\password` en `psql`) o borra el volumen con `docker compose down -v`.
- **No uses `SPRING_PROFILES_ACTIVE=local` en Docker.** Ese perfil es para ejecutar fuera de contenedores (ver abajo).
- **Inventario:** por defecto el API busca el servicio en `http://host.docker.internal:8081`. Si no lo tienes corriendo, las operaciones que validan artículos responden `502`.

`.env` está en `.gitignore`: no subas credenciales reales. `compose.yaml` es solo para desarrollo local.

## Ejecutar sin Docker (perfil `local`)

Requiere JDK 25 y PostgreSQL local (RabbitMQ es opcional: la conexión es perezosa).

```powershell
Copy-Item src/main/resources/application-local.properties.example src/main/resources/application-local.properties
# Completar la conexión a PostgreSQL (y RabbitMQ si aplica) en ese archivo
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

El perfil `local` incluye un mock de Inventario dentro del mismo backend. `application-local.properties` está excluido de Git y de la imagen. Variables `SPRING_DATASOURCE_*` o `SPRING_RABBITMQ_*` en tu entorno tienen prioridad sobre ese archivo.

## Comandos útiles

| Comando | Qué hace |
| ------- | -------- |
| `./gradlew test` | Pruebas + reporte JaCoCo (requiere Docker: usa Testcontainers) |
| `./gradlew checkstyleMain checkstyleTest` | Revisión de estilo |
| `./gradlew bootJar` | Genera el JAR en `build/libs/` |

En Windows, usar `.\gradlew.bat`.

## Documentación

| Documento | Contenido |
| --------- | --------- |
| [`docs/arch.md`](docs/arch.md) | Arquitectura por capas y regla de dependencias |
| [`docs/api.md`](docs/api.md) | Versionado, idempotencia, CORS, errores, integración con Inventario y migraciones manuales |
| [`docs/messaging.md`](docs/messaging.md) | Eventos publicados y consumidos en RabbitMQ |
| [`docs/ci.md`](docs/ci.md) | Pipeline, cobertura JaCoCo y SonarQube |
| [`postman/README.md`](postman/README.md) | Simulación de Inventario para probar el cliente HTTP |

> **Bases existentes:** no hay herramienta de migraciones. Antes de actualizar, revisa los scripts de [`docs/sql/`](docs/sql/) listados en [`docs/api.md`](docs/api.md#migraciones-manuales).

## Contribuir

- `main` está protegida: todo cambio entra por Pull Request desde `feature/<nombre>` o `fix/<nombre>`, con al menos una revisión de otra persona y el check `Test (Java 25)` en verde.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/es/) (`feat:`, `fix:`, `docs:`…).

## Licencia

[MIT](LICENSE)
