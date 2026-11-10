<div style="text-align: center;">

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
[FMAT-Restaurant/Menu-Documentation](https://fmat-restaurant.github.io/Menu-Documentation/).
Esa documentación es la referencia para el diseño y la implementación de este backend.

## Documentación relacionada

La eliminación de recetas (`DELETE /api/v1/menu/recipes/{recipeId}`, REQ-MENU-REC-005) exige
`If-Match` y rechaza con `409` cualquier referencia desde una opción de una oferta existente,
sin filtrar por estado ni revisión. La baja retira la receta de la biblioteca; conserva sus
revisiones publicadas y sus ingredientes. En bases existentes, ejecutar
[`docs/sql/recipe-deletion.sql`](docs/sql/recipe-deletion.sql) antes de iniciar esta versión.
Las bases nuevas crean la columna mediante los mapeos JPA.

Los `POST` de categorías, entradas, ofertas, recetas e imágenes y `PATCH /api/v1/menu/offers/{offerId}`
exigen `Idempotency-Key` (UUID sin comillas; si falta o no es válido, `400 INVALID_IDEMPOTENCY_KEY`).
Durante 24 horas desde el primer éxito, repetir la misma solicitud con la misma clave devuelve el estado,
cuerpo y `ETag` originales sin volver a ejecutarla; otra solicitud con esa clave da
`409 IDEMPOTENCY_KEY_REUSED`, y una en curso, `409 IDEMPOTENCY_IN_PROGRESS` con `Retry-After`. Los
registros viven en la tabla `idempotency_record`, que crean los mapeos JPA; una tarea horaria borra los
vencidos.
 
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
# Copiar .env.example a .env y definir POSTGRES_PASSWORD antes de arrancar.
docker compose up --build
```

[`compose.yaml`](compose.yaml) construye la imagen del API con el [`Dockerfile`](Dockerfile) (Eclipse Temurin 25.0.4.1) y la levanta junto a PostgreSQL 18.6 y RabbitMQ 4.3.6. El API espera a que ambos estén listos.

- La configuración es externa: el API recibe la conexión por variables de entorno (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). `application.properties` no tiene datos de conexión.
- Copiar `.env.example` a `.env` (`Copy-Item .env.example .env` en PowerShell, solo si aún no existe) y definir `POSTGRES_PASSWORD`; Compose rechaza una contraseña vacía o ausente. La base y el usuario predeterminados son `menu`, con puertos 8080 y 5432. Se configuran mediante variables o el archivo `.env` (ignorado por git): `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`, `RABBITMQ_PORT`, `RABBITMQ_URL`, `API_PORT`, `SPRING_PROFILES_ACTIVE`, `INVENTORY_BASE_URL`, `INVENTORY_TOKEN`, `INVENTORY_TIMEOUT` e `INVENTORY_AVAILABILITY_ROUTING_KEY`. Cada una está explicada en `.env.example`.
- El API se une a la red externa `restaurant-network`, que compartes con los demás servicios: crearla una vez con `docker network create restaurant-network`; sin ella `docker compose up` falla.
- Compose lee `.env` y pasa los valores declarados en `environment` a cada contenedor. Spring no importa ese archivo ni necesita tenerlo dentro de la imagen. Para ejecutar fuera de Docker, usar el perfil `local` descrito abajo.
- `POSTGRES_PASSWORD` solo inicializa la contraseña al crear una base nueva. Si ya existe el volumen, cambiar esta variable no modifica la contraseña guardada; debe actualizarse con `\password` desde `psql` y coincidir con la configuración de la API.
- Los datos se guardan en el volumen `postgres-data`. `docker compose down -v` los borra.
- Este archivo es solo para desarrollo local; no define la topología de despliegue.

### Opción 2: API local con el perfil `local` (sin Docker)

Requiere JDK 25, PostgreSQL local y una base existente con permisos para el usuario configurado. RabbitMQ debe estar instalado y configurado localmente si se usa mensajería; la conexión es perezosa.

1. Desde la raíz del proyecto, copiar `application-local.properties.example` a `src/main/resources/application-local.properties` (solo si aún no existe):

   ```powershell
   Copy-Item application-local.properties.example application-local.properties
   ```

2. Completar la URL, el usuario y la contraseña de PostgreSQL en `src/main/resources/application-local.properties`. Ajustar RabbitMQ e Inventario según el entorno. La plantilla usa el inventario de prueba del propio backend; se puede apuntar al servicio externo en su lugar.
3. Iniciar desde la raíz con el perfil local:

   ```powershell
   .\gradlew.bat bootRun --args="--spring.profiles.active=local"
   ```

Spring combina `src/main/resources/application.properties` con el archivo externo `src/main/resources/application-local.properties` al activar `local`. El archivo personal está excluido de Git y del contexto de Docker; su plantilla sí se versiona. No se copia al JAR ni a la imagen.

`.env` se reserva para Compose. `POSTGRES_URL` y `POSTGRES_USERNAME` del antiguo flujo local no son utilizados por Spring ni por este Compose: la conexión local se define en `src/main/resources/application-local.properties`; Compose usa `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD`. No activar `local` al ejecutar la API en Docker.

Las variables `SPRING_DATASOURCE_*` o `SPRING_RABBITMQ_*` heredadas pueden prevalecer sobre el archivo local; quitarlas del entorno si se quiere usar los valores del perfil. Hibernate `update` se configura solo para desarrollo (en el archivo local y en Compose); no es una estrategia de migraciones para producción.

En ambos casos:

- La API queda disponible en `http://localhost:8080`.
- El contrato OpenAPI (esqueleto, sin endpoints todavía) se sirve en `http://localhost:8080/openapi.yaml`; el archivo es [`src/main/resources/static/openapi.yaml`](src/main/resources/static/openapi.yaml).
- Ese archivo es una copia de [`docs/apis/menu/openapi.yaml`](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/apis/menu/openapi.yaml) del repositorio de documentación, que es la fuente de verdad (OpenAPI 3.2.1). Cualquier cambio al contrato se hace primero allá y luego se copia aquí. Las rutas y los servidores quedan pendientes de OPEN-010.

### Versionado de la API

Todas las rutas de Menu viven bajo `/api/{version}` (hoy `/api/v1`). Usa el versionado nativo de Spring Framework 7: la versión es el segundo segmento del path, es obligatoria bajo `/api` y una versión que ningún controlador declare (`/api/v2/...`) responde `400` con el sobre de error.

- Los controladores se anotan con `@MenuApi("/menu/...")` y declaran rutas relativas; no repiten el prefijo.
- Prefijo y versión se definen una sola vez en `ApiVersioning` (`PREFIX`, `CURRENT`); `ApiVersioningConfiguration` los conecta a Spring MVC. Los filtros de servlet (Idempotency-Key, ETag) toman la ruta de `ApiVersioning.BASE`.
- Para servir otra versión: crear una anotación equivalente a `@MenuApi` con `version = "v2"` en los controladores nuevos; la versión se detecta sola.
- El mock de Inventario (perfil `local`) simula otro servicio y queda fuera del prefijo.

### CORS y respuestas 404

Los errores 404 mantienen el formato `{ "error": { "message": "..." } }`.
Cuando falta una entidad, el mensaje identifica el tipo y el ID; para revisiones de recetas
también identifica la revisión. Las rutas inexistentes y los UUID inválidos devuelven un
404 JSON con `The requested resource was not found.`.

CORS permite por defecto `http://localhost` y `https://localhost`, con o sin puerto,
y rechaza los demás orígenes salvo que se configuren explícitamente. Para agregar uno o varios
frontends, usar estas propiedades (por ejemplo en `src/main/resources/application-local.properties`):

```properties
menu.cors.allowed-origins=https://menu.example.com,https://admin.example.com
menu.cors.allow-localhost=true
```

También se pueden definir `MENU_CORS_ALLOWED_ORIGINS` y `MENU_CORS_ALLOW_LOCALHOST` como
variables de entorno. Compose las transmite desde `.env`. La lista debe contener orígenes
HTTP(S) exactos separados por comas, sin rutas, barra final ni comodines; una configuración
inválida impide el arranque. Una lista vacía permite únicamente localhost si está habilitado.
En producción, establecer `MENU_CORS_ALLOW_LOCALHOST=false` y declarar los orígenes autorizados.

La política global cubre errores y recursos estáticos. Permite `GET`, `HEAD`, `POST`, `PATCH`,
`DELETE` y `OPTIONS`; acepta `Accept`, `Content-Type`, `Authorization`, `If-Match`,
`If-None-Match` e `Idempotency-Key`; expone `ETag`, `Location` y `Retry-After` al navegador. Los preflight se cachean una hora.
No habilita credenciales de cookies cross-origin; el token explícito en `Authorization` sí está
permitido. CORS no sustituye la autenticación ni restringe clientes sin cabecera `Origin`.

### Conexión con RabbitMQ

Menu usa Spring AMQP (`spring-boot-starter-amqp`), cuya versión gestiona Spring Boot.

**Publicación.** Cada vez que se crea, actualiza o elimina una *fuente* (una receta de la biblioteca o una opción con un artículo de Inventario), Menu publica un evento en el exchange `menu.topic` (topic, durable) **después de confirmar la transacción**; si la solicitud se rechaza o se revierte no se publica nada. La publicación es asíncrona y nunca afecta a la respuesta HTTP: el cambio ya está confirmado.

Manejo de errores (no hay outbox, así que un evento que no se entrega se pierde; lo que se garantiza es que **nunca se pierde en silencio**):

| Situación | Qué hace Menu |
| --------- | ------------- |
| Fallo de conexión o envío | Reintenta con espera creciente (3 intentos, `spring.rabbitmq.template.retry.*`; conexión con timeout de 5 s). Si se agotan, registra un `ERROR` con `eventId`, `type`, `sourceId` y `routingKey`, los datos para volver a anunciar la fuente a mano |
| El broker rechaza el mensaje (nack) o no confirma en 10 s | `ERROR` con los mismos campos y la causa (usa *publisher confirms*) |
| Ninguna cola recibe el mensaje (nadie enlazado a `menu.source.*`) | `WARN` `not routed to any queue` (*mandatory* + *returns*) |
| Apagado de la aplicación mientras se publica | `ERROR` con los mismos campos; nunca rompe una solicitud ya confirmada |

| Routing key | Cuándo | Payload |
| ----------- | ------ | ------- |
| `menu.source.created` | Receta creada, u opción de Inventario nueva | `{sourceId, name, items: [{id, quantity}]}` |
| `menu.source.updated` | Receta con nueva revisión, u opción de Inventario cuyo artículo o cantidad cambió | `{sourceId, name, items: [{id, quantity}]}` |
| `menu.source.deleted` | Receta eliminada; opción de Inventario retirada, o eliminada junto con su oferta o entrada | `{sourceId}` |

`sourceId` es el id de la receta o el id de la opción; `name` es `null` en opciones de Inventario. Cada mensaje lleva `messageId` (id del evento), `correlationId` (`sourceId`) y la cabecera `X-Event-Type`.

**Consumo.** Menu declara la cola `menu.inventory.availability.queue` (con DLQ `menu.inventory.availability.dlq` en `menu.dlx`) y la enlaza a `inventory.topic` con la routing key de `INVENTORY_AVAILABILITY_ROUTING_KEY` (por defecto `inventory.recipe.availability-changed`). Por ahora solo registra los mensajes recibidos. Ante un fallo del listener (o un mensaje ilegible) hace 3 intentos (`spring.rabbitmq.listener.simple.retry.*`) y, si fallan, republica el mensaje en `menu.dlx` → DLQ con la causa en la cabecera `x-exception-message`; nunca se reencola. Nada consume la DLQ: revisarla es manual.

- La conexión se configura con una sola URL, `amqp(s)://usuario:clave@host[:puerto][/vhost]`, que Spring lee como `spring.rabbitmq.addresses` (`SPRING_RABBITMQ_ADDRESSES`). En Compose viene de `RABBITMQ_URL`; en local, del archivo `application-local.properties`. `application.properties` no contiene datos de conexión ni credenciales; solo la routing key de disponibilidad (`INVENTORY_AVAILABILITY_ROUTING_KEY`). Si no se define, Spring usa sus valores por defecto (`localhost:5672`, virtual host `/`, `guest`/`guest`).
- `compose.yaml` levanta RabbitMQ 4.3.6 con credenciales solo para desarrollo local (`menu` / `menu`, virtual host `/`, puerto 5672). Se sobrescriben con `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` y `RABBITMQ_PORT`, o con un archivo `.env` (ignorado por git); el API usa ese broker salvo que se defina `RABBITMQ_URL` para uno externo. Los secretos reales de otros entornos no se versionan.
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

### Artículos de Inventario

El controlador `InventoryMockController` expone un inventario fijo dentro de este backend **solo cuando el perfil `local` está activo** (`--spring.profiles.active=local`). Con cualquier otro perfil no existe y Menu no expone rutas de lista ni de detalle de artículos: las consultas se delegan al servicio de Inventario configurado en `INVENTORY_BASE_URL`.

- `GET /api/v1/items` (también `/items`): lista plana de 10 artículos hardcoded con UUID válidos, sin paginación ni envoltorio. Acepta `q` (búsqueda por nombre sin distinguir mayúsculas).
- `GET /items/{id}` (también bajo `/api/v1`) es compatible con la consulta por ID de Inventario: devuelve el objeto plano `{id, name, unit}`, o `404` con el sobre de error `{error: {message}}` si el ID no existe. Los IDs son strings opacos y se comparan exactamente. Ejemplo: `/api/v1/items/2c21767f-0f9a-4e9f-adc7-2730556c1801`.

Para usarlo desde el cliente de este mismo backend, asignar `INVENTORY_BASE_URL=http://localhost:8080` (ajustando el puerto si corresponde; la base es la raíz del servicio, sin `/api/v1`). El mock no necesita token, no usa base de datos ni llama al servicio externo. No se modifica el `.env` automáticamente.

`InventoryClient.find` hace una sola consulta a `GET {base-url}/items/{id}` y consume el objeto plano `{id, name, unit}` (sin envoltorio `data`), validando que el ID recibido coincida exactamente con el solicitado. Es la ruta de servicio a servicio; el OpenAPI publicado de Menu describe otra, la de búsqueda que usa el frontend. Devuelve vacío únicamente cuando Inventario confirma la inexistencia con un `404` que trae su sobre de error `{error: {message}}`; un `404` sin ese sobre (una URL base equivocada o un proxy), un `204`, cualquier otro error HTTP, un timeout o una respuesta incompleta se propagan como fallo de Inventario.

| Resultado de la consulta | Respuesta de Menu en ofertas y recetas |
| ------------------------ | -------------------------------------- |
| Artículo encontrado | La operación continúa |
| Inventario confirma que no existe (`404` con sobre de error) | `422`, sin cambios |
| Timeout, indisponibilidad, `5xx`, `401`/`403`/`429` o respuesta inválida | `502`, sin cambios |

Ni un `422` ni un `502` dejan cambios parciales, y tras recuperarse Inventario la misma solicitud (con la misma `Idempotency-Key` en las operaciones que la exigen) puede reintentarse. El contrato publicado define UUID; el cliente conserva compatibilidad con identificadores opacos como strings.

Las recetas y las opciones de inventario de las ofertas reciben `inventoryItemId` como string opaco (por ejemplo, `INV-beef-001` o `000042`), sin exigir formato UUID. Menu confirma el artículo y su unidad mediante el cliente HTTP de Inventario y conserva el identificador exacto, el nombre y la unidad. Los identificadores propios de Menu siguen siendo UUID.

Las columnas `component_ingredient.inventory_item_id` y `slot_option.inventory_item_id` son de tipo PostgreSQL `text`. En una base existente, detener el backend y ejecutar [inventory-item-id-to-text.sql](docs/sql/inventory-item-id-to-text.sql) antes de iniciar esta versión. El script convierte los UUID existentes a su representación textual y conserva los valores nulos de las opciones de receta. Las bases nuevas se crean con el tipo correcto mediante Hibernate.

El contrato servido en `src/main/resources/static/openapi.yaml` incluye este cambio; debe sincronizarse con la fuente de documentación al integrar esta versión.

## Decisiones pendientes

| Tema | Estado |
| ---- | ------ |
| Persistencia | Decidida: PostgreSQL 18.6 con Spring Data JPA. Sin migraciones: el esquema lo genera Hibernate en local. |
| Topología | Menu publica en `menu.topic` (`menu.source.created\|updated\|deleted`) y consume su cola de disponibilidad enlazada a `inventory.topic`; ver [Conexión con RabbitMQ](#conexión-con-rabbitmq). `compose.yaml` es solo el entorno local. |
| Límites transaccionales | Sin decidir. |
| AuthN/AuthZ | Sin decidir. No se incluye Spring Security, aunque el contrato OpenAPI 7.0.0 declara `bearerAuth` global; esto **no** significa que la API sea pública o anónima. Mientras tanto, las claves de idempotencia usan un sujeto fijo. |

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
