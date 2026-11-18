# CI/CD, cobertura y SonarQube

El pipeline vive en [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) y se ejecuta en cada push a `main`, en todos los Pull Requests, manualmente y cada domingo.

| Job | Qué hace | Cuándo |
| --- | -------- | ------ |
| `Test (Java 25)` | Pruebas JUnit con Testcontainers (PostgreSQL y RabbitMQ), cobertura JaCoCo, Checkstyle, análisis SonarQube (solo si existe `SONAR_TOKEN`). | Siempre |
| `Performance (k6)` | Levanta Menu con Compose (perfil `local`), siembra un catálogo y ejecuta [`k6/catalog.js`](../k6/catalog.js) contra los RNF de rendimiento. | Siempre, tras `Test (Java 25)` |
| `Contract (OpenAPI)` | Descarga el contrato publicado en el `ref` de `gradle.properties`, comprueba su versión, lo compara con el servido con oasdiff (`diff --fail-on-diff`) y valida el servido con `redocly lint`. | Siempre |
| `Build and Push Docker Image` | Construye la imagen del [`Dockerfile`](../Dockerfile) y la publica en `ghcr.io/fmat-restaurant/menu-backend` con las etiquetas `latest` y el SHA del commit. | Solo push a `main`, si `test`, `performance` y `contract` pasan |

Reproducir la verificación localmente:

```bash
./gradlew checkstyleMain checkstyleTest test jacocoTestReport
```

`Test (Java 25)` y `Contract (OpenAPI)` deben ser checks requeridos en la protección de `main` (*Settings > Branches*); quien administre el repositorio los agrega ahí.

## Cobertura con JaCoCo

Se usa **JaCoCo 0.8.15** (fijado con `jacoco.toolVersion`; soporta Java 25). El plugin `jacoco` forma parte de Gradle, sin dependencias de producción adicionales. `test` finaliza con `jacocoTestReport`, incluso si alguna prueba falla.

```bash
./gradlew clean jacocoTestReport
```

- **XML para SonarQube:** `build/reports/jacoco/test/jacocoTestReport.xml`.
- **HTML para revisión local:** `build/reports/jacoco/test/html/index.html`.

En CI se comprueba que el XML exista y no esté vacío. Los reportes de pruebas, cobertura y Checkstyle se conservan 14 días en el artefacto `test-results-java-25`, incluso si falla un paso.

## SonarQube Cloud

La ruta del reporte ya está en [`sonar-project.properties`](../sonar-project.properties) (`sonar.coverage.jacoco.xmlReportPaths`); `aggregateXmlReportPaths` se deja vacío porque el proyecto tiene un solo módulo. Antes del scanner, CI ejecuta `./gradlew prepareSonarLibraries`, que copia los JAR de `runtimeClasspath` y `testRuntimeClasspath` a `build/sonar/libraries/{main,test}/` para que el scanner resuelva los tipos.

Si el log muestra `Sensor JaCoCo XML Report Importer` seguido de `Importing 1 report(s)`, el XML se importó. Las exclusiones de `sonar.coverage.exclusions` no alteran el HTML local de JaCoCo.

### Configuración necesaria en GitHub y SonarCloud

1. **Método de análisis**: en SonarCloud, *Administration > Analysis Method*, desactivar **Automatic Analysis**. El análisis automático no recibe artefactos de CI, no admite cobertura e ignora `sonar-project.properties`. ([referencia](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/automatic-analysis))
2. **Secret `SONAR_TOKEN`** (*Settings > Secrets and variables > Actions*): token de SonarCloud (*My Account > Security*) con permiso de análisis. Sin él, el paso `SonarQube Scan` se omite con un aviso. Los PR de forks no lo reciben.
3. **Proyecto**: ajustar `sonar.organization` y `sonar.projectKey` en `sonar-project.properties` si difieren.
4. **GHCR** (*Settings > Actions > General*): *Workflow permissions* en **Read and write permissions**.
5. **Protección de `main`**: exigir el status check `Test (Java 25)`.

## Pruebas de rendimiento con k6

[`k6/catalog.js`](../k6/catalog.js) verifica los [requisitos no funcionales](https://fmat-restaurant.github.io/Menu-Documentation/specification/non-functional-requirements/) de la consulta, búsqueda y filtrado del catálogo:

| Requisito | Escenario | Umbral (falla el job si no se cumple) |
| --------- | --------- | ------------------------------------- |
| RNF-REND-02 | `nominal`: 30 solicitudes/s con hasta 40 sesiones, 30 min (5 min en PRs) | p95 ≤ 200 ms, p99 ≤ 1 s, errores < 0.1 % |
| RNF-REND-03 | `burst`: 100 solicitudes/s con hasta 40 sesiones, 60 s, después del nominal | Menu sigue respondiendo al terminar; sin umbral de latencia |

`setup()` siembra 12 categorías, 150 entradas activas (una de cada diez sin categoría) y 2 ofertas por entrada a través de la API. La mezcla de operaciones (catálogo, ofertas de una entrada, composición, búsqueda por texto y filtro por categoría) se reparte por igual mientras OPEN-009 no fije la proporción. El resumen (`k6-summary.json`, artefacto `k6-summary`) incluye las solicitudes completadas, fallidas y descartadas (`dropped_iterations`) que pide RNF-REND-03.CA2.

Ejecutarlo localmente (sin instalar k6):

```bash
docker network create restaurant-network   # una sola vez
POSTGRES_PASSWORD=perf SPRING_PROFILES_ACTIVE=local INVENTORY_BASE_URL=http://localhost:8080 docker compose up -d --build
docker run --rm --network menu-backend_default -v "$PWD/k6:/k6" grafana/k6 run \
  -e BASE_URL=http://api:8080 -e NOMINAL_DURATION=2m /k6/catalog.js
```

`NOMINAL_DURATION`, `BURST_DURATION`, `CATEGORIES` y `ENTRIES` ajustan la duración y el tamaño del catálogo.
