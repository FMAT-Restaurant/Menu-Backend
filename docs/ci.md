# CI/CD, cobertura y SonarQube

El pipeline vive en [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) y se ejecuta en cada push a `main`, en todos los Pull Requests, manualmente y cada domingo.

| Job | Qué hace | Cuándo |
| --- | -------- | ------ |
| `Test (Java 25)` | Pruebas JUnit con Testcontainers (PostgreSQL y RabbitMQ), cobertura JaCoCo, Checkstyle, análisis SonarQube (solo si existe `SONAR_TOKEN`). | Siempre |
| `Build and Push Docker Image` | Construye la imagen del [`Dockerfile`](../Dockerfile) y la publica en `ghcr.io/fmat-restaurant/menu-backend` con las etiquetas `latest` y el SHA del commit. | Solo push a `main` |

Reproducir la verificación localmente:

```bash
./gradlew checkstyleMain checkstyleTest test jacocoTestReport
```

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
