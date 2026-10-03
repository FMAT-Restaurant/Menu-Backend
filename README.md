<div align="center">

# Menu Backend
Backend del microservicio de **menú** del sistema de comandas de **FMAT Restaurant**.


</div>

## Propósito
 
Este servicio implementa el bounded context `Menu`, responsable de:
 
- Definir el **catálogo** de productos del restaurante.
- Gestionar los **productos vendibles** y sus **variantes**.
- Administrar **modificadores** (extras, ingredientes, opciones).
- Definir **combos**.
- Resolver la información del menú necesaria para el flujo de comandas.
La definición del dominio, los requisitos y las decisiones de arquitectura viven en el repositorio de documentación:
[FMAT-Restaurant/Menu-Documentation](https://github.com/FMAT-Restaurant/Menu-Documentation).
Esa documentación es la referencia para el diseño y la implementación de este backend.

## Documentación relacionada
 
| Recurso | Descripción |
| ------- | ----------- |
| [Menu-Documentation](https://fmat-restaurant.github.io/Menu-Documentation/) | Modelo de dominio, requisitos funcionales, reglas de negocio y auditorías |
| Frontend | [Frontend Repository](https://github.com/FMAT-Restaurant/Menu-Frontend) |

## Tecnologías
 
| Tecnología | Versión | Notas |
| ---------- | ------- | ----- |
| Java | 25 (LTS), Eclipse Temurin 25.0.4.1 | Fijado por el toolchain de `build.gradle` |
| Spring Boot | 4.1.1 | Basado en Spring Framework 7 |
| Gradle | 9.8.0 (wrapper incluido, `gradlew`) | No es necesario instalar Gradle por separado |
| PostgreSQL | 18.6 | Corre en un contenedor; no se instala a mano |
| Docker / Docker Compose | 29.8.1 / 5.5.1 | Entorno local y pruebas con Testcontainers |
 
> **Importante:** todo el equipo debe usar la misma versión de Java. La versión del proyecto queda fijada en el `build.gradle` (toolchain de Java 25, Eclipse Temurin).
 
## Requisitos previos
 
- **JDK 25** (Eclipse Temurin). Si no lo tienes, Gradle lo descarga automáticamente por el toolchain.
- **Git**.
- Un IDE con soporte para Java y Spring (IntelliJ IDEA, VS Code con Extension Pack for Java, Eclipse STS).
- **Docker Desktop** (Docker 29.8.1 / Compose 5.5.1) en ejecución. Se usa para levantar PostgreSQL y para las pruebas.

## Levantar el proyecto en local

### Opción 1: todo con Docker Compose

```bash
git clone https://github.com/FMAT-Restaurant/Menu-Backend.git
cd Menu-Backend
docker compose up --build
```

[`compose.yaml`](compose.yaml) construye la imagen del API con el [`Dockerfile`](Dockerfile) (Eclipse Temurin 25.0.4.1) y la levanta junto a PostgreSQL 18.6. El API espera a que PostgreSQL esté listo.

- La configuración es externa: el API recibe la conexión por variables de entorno (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). `application.properties` no tiene datos de conexión.
- Los valores por defecto (`menu` / `menu` / `menu`, puertos 8080 y 5432) se sobrescriben con variables o con un archivo `.env` (ignorado por git): `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `API_PORT`.
- Los datos se guardan en el volumen `postgres-data`. `docker compose down -v` los borra.
- Este archivo es solo para desarrollo local; no define la topología de despliegue.

### Opción 2: API desde el IDE o con Gradle

```bash
docker compose up -d postgres
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/menu
export SPRING_DATASOURCE_USERNAME=menu
export SPRING_DATASOURCE_PASSWORD=menu
./gradlew bootRun      # En Windows: gradlew.bat bootRun (con set en lugar de export)
```

En ambos casos:

- La API queda disponible en `http://localhost:8080`.
- El contrato OpenAPI (esqueleto, sin endpoints todavía) se sirve en `http://localhost:8080/openapi.yaml`; el archivo es [`src/main/resources/static/openapi.yaml`](src/main/resources/static/openapi.yaml).
- Ese archivo es una copia de [`docs/apis/menu/openapi.yaml`](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/apis/menu/openapi.yaml) del repositorio de documentación, que es la fuente de verdad (OpenAPI 3.2.1). Cualquier cambio al contrato se hace primero allá y luego se copia aquí. Las rutas y los servidores quedan pendientes de OPEN-010.

Otros comandos útiles:

| Comando | Qué hace |
| ------- | -------- |
| `./gradlew test` | Ejecuta las pruebas (requiere Docker: Testcontainers levanta PostgreSQL 18.6) |
| `./gradlew checkstyleMain checkstyleTest` | Revisa el estilo del código |
| `./gradlew bootJar` | Genera el `.jar` ejecutable en `build/libs/` |

## Decisiones pendientes

| Tema | Estado |
| ---- | ------ |
| Persistencia | Decidida: PostgreSQL 18.6 con Spring Data JPA. Todavía no hay entidades ni esquema. |
| Topología | Sin decidir. `compose.yaml` es solo el entorno local. RabbitMQ está en el stack, pero no se agrega hasta decidir la topología. |
| Límites transaccionales | Sin decidir. |
| AuthN/AuthZ | Sin decidir. No se incluye Spring Security y el contrato OpenAPI no declara ningún esquema de seguridad; esto **no** significa que la API sea pública o anónima. |

## Arquitectura

El código sigue una arquitectura por capas, cada una como paquete bajo `com.fmatrestaurant.menu`:

| Capa | Paquete | Responsabilidad |
| ---- | ------- | --------------- |
| API | `api` | Controladores REST, DTOs HTTP, manejo de errores |
| Aplicación | `application` | Casos de uso, orquestación, validaciones |
| Dominio | `domain` | Entidades JPA con las reglas de negocio del menú |
| Infraestructura | `infrastructure` | Repositorios Spring Data, mensajería (RabbitMQ), configuración |

Las pruebas viven en `src/test/java` y replican los mismos paquetes. Dependencias: `api → application → infrastructure`, y tanto `application` como `infrastructure` usan `domain`.

Detalle completo, reglas y ejemplos en [`docs/arch.md`](docs/arch.md).


## Flujo de trabajo y ramas
 
| Rama | Propósito |
| ---- | --------- |
| `main` | Producción. Protegida: sin push directo, solo cambios vía Pull Request |
| `feature/<nombre>` | Ramas de trabajo para nuevas funcionalidades |
| `fix/<nombre>` | Ramas de trabajo para correcciones |
 
Reglas del repositorio:
 
1. Está **prohibido el push directo a `main`**.
2. Todo cambio entra mediante **Pull Request**.
3. Cada Pull Request requiere **al menos una revisión** de una persona distinta a quien lo creó.
4. Las ramas de trabajo se integran directamente en `main` mediante Pull Request.
Flujo: `feature/xxx` → PR a `main`.

## Convención de commits
 
Se recomienda el formato [Conventional Commits](https://www.conventionalcommits.org/es/):
 
```
feat: agregar endpoint de combos
fix: corregir validación de variantes
docs: actualizar README
```

## CI/CD

El pipeline vive en [`.github/workflows/ci.yml`](.github/workflows/ci.yml) y se ejecuta en cada push y Pull Request hacia `main`, manualmente y cada domingo.

| Job | Qué hace | Cuándo |
| --- | -------- | ------ |
| `Test (Java 25)` | Checkstyle, pruebas JUnit con Testcontainers (PostgreSQL), cobertura JaCoCo, análisis SonarQube (opcional, solo si existe `SONAR_TOKEN`). | Siempre |
| `Build and Push Docker Image` | Construye la imagen del [`Dockerfile`](Dockerfile) (Temurin 25) y la publica en `ghcr.io/fmat-restaurant/menu-backend` con las etiquetas `latest` y el SHA del commit. | Solo push a `main` |

Reproducir la verificación localmente:

```bash
./gradlew checkstyleMain checkstyleTest test jacocoTestReport
```

Configuración necesaria en GitHub:

1. **Secrets** (*Settings > Secrets and variables > Actions*): `SONAR_TOKEN` (SonarCloud: *My Account > Security*). Es opcional: sin él, el análisis de SonarQube se omite con un aviso.
2. **Sonar**: ajustar `sonar.organization` y `sonar.projectKey` en [`sonar-project.properties`](sonar-project.properties) si difieren en SonarCloud.
3. **GHCR** (*Settings > Actions > General*): *Workflow permissions* en **Read and write permissions**.
4. **Protección de ramas** (`main`): exigir el status check `Test (Java 25)` antes de hacer merge.
