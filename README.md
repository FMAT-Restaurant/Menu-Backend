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
| JaCoCo | 0.8.15 | Agente y generador de reportes fijados con `jacoco.toolVersion`; plugin `jacoco` incluido en Gradle |
| PostgreSQL | 18.6 | Corre en un contenedor; no se instala a mano |
| RabbitMQ / Spring AMQP | 4.3.6 / 4.1.1 | RabbitMQ corre en un contenedor; Spring Boot gestiona la versión de Spring AMQP |
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

[`compose.yaml`](compose.yaml) construye la imagen del API con el [`Dockerfile`](Dockerfile) (Eclipse Temurin 25.0.4.1) y la levanta junto a PostgreSQL 18.6 y RabbitMQ 4.3.6. El API espera a que ambos estén listos.

- La configuración es externa: el API recibe la conexión por variables de entorno (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). `application.properties` no tiene datos de conexión.
- Los valores por defecto (`menu` / `menu` / `menu`, puertos 8080 y 5432) se sobrescriben con variables o con un archivo `.env` (ignorado por git): `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`, `RABBITMQ_PORT`, `API_PORT`.
- Los datos se guardan en el volumen `postgres-data`. `docker compose down -v` los borra.
- Este archivo es solo para desarrollo local; no define la topología de despliegue.

### Opción 2: API desde el IDE o con Gradle

```bash
docker compose up -d postgres rabbitmq
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/menu
export SPRING_DATASOURCE_USERNAME=menu
export SPRING_DATASOURCE_PASSWORD=menu
export SPRING_RABBITMQ_HOST=localhost
export SPRING_RABBITMQ_PORT=5672
export SPRING_RABBITMQ_VIRTUALHOST=/
export SPRING_RABBITMQ_USERNAME=menu
export SPRING_RABBITMQ_PASSWORD=menu
./gradlew bootRun      # En Windows: gradlew.bat bootRun (con set en lugar de export)
```

En ambos casos:

- La API queda disponible en `http://localhost:8080`.
- El contrato OpenAPI (esqueleto, sin endpoints todavía) se sirve en `http://localhost:8080/openapi.yaml`; el archivo es [`src/main/resources/static/openapi.yaml`](src/main/resources/static/openapi.yaml).
- Ese archivo es una copia de [`docs/apis/menu/openapi.yaml`](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/apis/menu/openapi.yaml) del repositorio de documentación, que es la fuente de verdad (OpenAPI 3.2.1). Cualquier cambio al contrato se hace primero allá y luego se copia aquí. Las rutas y los servidores quedan pendientes de OPEN-010.

### Conexión con RabbitMQ

La conectividad base usa Spring AMQP (`spring-boot-starter-amqp`), cuya versión gestiona Spring Boot. Esta tarea solo configura y comprueba la conexión: **no** hay productores, consumidores, exchanges, colas ni contratos de mensajes (siguen sin decidir).

- La conexión se configura por variables de entorno de Spring Boot: `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_VIRTUALHOST`, `SPRING_RABBITMQ_USERNAME` y `SPRING_RABBITMQ_PASSWORD`. `application.properties` no contiene datos de conexión ni credenciales. Si no se definen, Spring usa sus valores por defecto (`localhost:5672`, virtual host `/`).
- `compose.yaml` levanta RabbitMQ 4.3.6 con credenciales solo para desarrollo local (`menu` / `menu`, virtual host `/`, puerto 5672). Se sobrescriben con `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` y `RABBITMQ_PORT`, o con un archivo `.env` (ignorado por git). Los secretos reales de otros entornos no se versionan.
- La conexión se establece de forma perezosa: el API arranca aunque el broker no esté disponible, y la conexión se abre cuando algo la usa.

Para levantar RabbitMQ y comprobar que está listo:

```bash
docker compose up -d rabbitmq
docker compose ps rabbitmq                                  # el estado debe ser "healthy"
docker compose exec rabbitmq rabbitmq-diagnostics -q ping   # responde "Ping succeeded"
```

La prueba de integración `RabbitMqConnectionTest` valida con Testcontainers que el backend se conecta a RabbitMQ (requiere Docker).

Otros comandos útiles:

| Comando | Qué hace |
| ------- | -------- |
| `./gradlew test` | Ejecuta las pruebas y genera cobertura JaCoCo XML/HTML (requiere Docker: Testcontainers levanta PostgreSQL 18.6 y RabbitMQ 4.3.6) |
| `./gradlew jacocoTestReport` | Ejecuta las pruebas si es necesario y genera los reportes de cobertura |
| `./gradlew checkstyleMain checkstyleTest` | Revisa el estilo del código |
| `./gradlew bootJar` | Genera el `.jar` ejecutable en `build/libs/` |

## Decisiones pendientes

| Tema | Estado |
| ---- | ------ |
| Persistencia | Decidida: PostgreSQL 18.6 con Spring Data JPA. Todavía no hay entidades ni esquema. |
| Topología | Sin decidir. `compose.yaml` es solo el entorno local. La conectividad base con RabbitMQ ya está configurada, pero no hay exchanges, colas, bindings ni contratos de mensajes hasta decidir la topología. |
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

El pipeline vive en [`.github/workflows/ci.yml`](.github/workflows/ci.yml) y se ejecuta en cada push a `main`, en todos los Pull Requests, manualmente y cada domingo.

| Job | Qué hace | Cuándo |
| --- | -------- | ------ |
| `Test (Java 25)` | Pruebas JUnit con Testcontainers (PostgreSQL y RabbitMQ), cobertura JaCoCo, Checkstyle, análisis SonarQube (opcional, solo si existe `SONAR_TOKEN`). | Siempre |
| `Build and Push Docker Image` | Construye la imagen del [`Dockerfile`](Dockerfile) (Temurin 25) y la publica en `ghcr.io/fmat-restaurant/menu-backend` con las etiquetas `latest` y el SHA del commit. | Solo push a `main` |

Reproducir la verificación localmente:

```bash
./gradlew checkstyleMain checkstyleTest test jacocoTestReport
```

### Cobertura JaCoCo y SonarQube

Se utiliza **JaCoCo 0.8.15**, release estable publicada el **4 de junio de 2026**, con cuatro meses de antigüedad al configurar esta integración. Es la versión estable actual y contiene correcciones y mejoras de filtrado del bytecode generado por Java 24–26. El soporte oficial de Java 25 existe desde 0.8.14 y se mantiene en 0.8.15; no se usa la versión snapshot 0.8.16. Referencia: [historial oficial de JaCoCo](https://www.jacoco.org/jacoco/trunk/doc/changes.html).

El plugin `jacoco` forma parte de Gradle 9.8.0; no requiere otro plugin externo ni dependencias de producción. `toolVersion` fija tanto el agente como las herramientas de reporte en 0.8.15. Instrumenta la JVM de la tarea `test`, que conserva `useJUnitPlatform()`: la cobertura incluye las pruebas actuales de Spring Boot/JUnit y Testcontainers, mientras PostgreSQL y RabbitMQ siguen ejecutándose en Docker.

Para generar cobertura desde cero (en Windows, usar `./gradlew.bat`):

```bash
./gradlew clean jacocoTestReport
```

Reportes generados:

- **XML para SonarQube:** `build/reports/jacoco/test/jacocoTestReport.xml`.
- **HTML para revisión local:** `build/reports/jacoco/test/html/index.html`.

`test` finaliza con `jacocoTestReport`, incluso si alguna prueba falla y se generan datos de ejecución; a su vez, solicitar el reporte ejecuta primero las pruebas si es necesario. Una falla que impida iniciar la JVM o generar datos de cobertura no puede producir un reporte válido.

En CI, cada PR ejecuta las pruebas y genera el reporte antes de Checkstyle y del análisis Sonar. Se comprueba que el XML exista y no esté vacío. Los reportes de pruebas, cobertura y Checkstyle se conservan por 14 días en el artefacto `test-results-java-25`, incluso si falla un paso. La generación de cobertura no depende de `SONAR_TOKEN`.

En SonarQube, pegar esta ruta en **Paths to JaCoCo XML coverage report files**, clave **`sonar.coverage.jacoco.xmlReportPaths`** (el segundo campo de la captura):

```text
build/reports/jacoco/test/jacocoTestReport.xml
```

La misma ruta ya está definida en [`sonar-project.properties`](sonar-project.properties). El campo `sonar.coverage.jacoco.aggregateXmlReportPaths` se deja vacío: este proyecto tiene un solo módulo y genera un reporte normal, no agregado. Sonar importa el XML generado durante CI; configurar la ruta en su interfaz no genera cobertura por sí solo. Referencia: [cobertura Java en SonarQube](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/test-coverage/java-test-coverage).

Antes del scanner, CI ejecuta `./gradlew prepareSonarLibraries`. Esta tarea usa `Sync` de Gradle para copiar los JAR de `runtimeClasspath` y `testRuntimeClasspath` a `build/sonar/libraries/main/` y `build/sonar/libraries/test/`. Las propiedades `sonar.java.libraries` y `sonar.java.test.libraries` apuntan a esos directorios: el scanner CLI puede resolver los tipos de Spring, JUnit y Testcontainers sin depender de la ubicación de la caché de Gradle. No agrega dependencias ni cambia sus versiones. Para lanzar el scanner localmente, ejecutar esta tarea después de compilar y generar cobertura.

Si el log muestra `Sensor JaCoCo XML Report Importer` seguido de `Importing 1 report(s)`, el XML se encontró y se importó. La ausencia de una métrica de cobertura puede deberse a que no hay líneas elegibles: los PR evalúan código nuevo y este PR de configuración no modifica código Java ejecutable. Además, excluir `MenuBackendApplication.java` mediante `sonar.coverage.exclusions` elimina actualmente la única clase ejecutable del cálculo; los archivos `package-info.java` solo documentan los paquetes. No hay un porcentaje útil hasta incorporar código elegible. Las exclusiones de Sonar no alteran el HTML local de JaCoCo.

Configuración necesaria en GitHub:

1. **Método de análisis en SonarQube Cloud**: en el proyecto, abrir *Administration > Analysis Method* y desactivar **Automatic Analysis**. Para importar cobertura se requiere el análisis desde CI: el análisis automático ocurre en un entorno independiente, no recibe los artefactos de GitHub Actions, no admite cobertura e ignora `sonar-project.properties`. No se deben ejecutar ambos métodos simultáneamente. Referencia: [análisis automático de SonarQube Cloud](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/automatic-analysis).
2. **Secrets** (*Settings > Secrets and variables > Actions*): generar un token en SonarCloud (*My Account > Security*) con permiso de análisis del proyecto y guardarlo como `SONAR_TOKEN`. Es necesario para enviar el análisis y la cobertura desde CI. Sin él, las pruebas y los reportes se generan, pero el paso `SonarQube Scan` se omite con un aviso. Los PR de forks no reciben este secreto.
3. **Sonar**: ajustar `sonar.organization` y `sonar.projectKey` en [`sonar-project.properties`](sonar-project.properties) si difieren en SonarCloud. `sonar.host.url` apunta explícitamente a `https://sonarcloud.io`. En el análisis desde CI, las propiedades del scanner prevalecen sobre la configuración equivalente de la interfaz; los argumentos `-D` del scanner prevalecen sobre el archivo. Esto no cambia el método de análisis: **Automatic Analysis** debe desactivarse en SonarCloud.
4. **GHCR** (*Settings > Actions > General*): *Workflow permissions* en **Read and write permissions**.
5. **Protección de ramas** (`main`): exigir el status check `Test (Java 25)` antes de hacer merge.

Para verificar la integración, revisar el mismo job `Test (Java 25)`: `Test with JUnit and generate coverage` debe terminar correctamente y luego `SonarQube Scan` debe ejecutarse, sin aparecer el aviso de omisión por falta de token. El log del scanner debe mostrar la importación de `build/reports/jacoco/test/jacocoTestReport.xml`. Si un check de Sonar termina antes de que CI genere el XML, comprobar el método de análisis del proyecto o la existencia de otro workflow de análisis: ese check no está consumiendo el reporte de este job.
