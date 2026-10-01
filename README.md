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
| [Menu-Documentation](https://github.com/FMAT-Restaurant/Menu-Documentation) | Modelo de dominio, requisitos funcionales, reglas de negocio y auditorías |
| Especificación consolidada (`output/ers/spec.md`) | Alcance, responsabilidades e invariantes del servicio `Menu` |
| Frontend | [Frontend Repository](https://github.com/FMAT-Restaurant/Menu-Frontend) |

## Tecnologías
 
| Tecnología | Versión | Notas |
| ---------- | ------- | ----- |
| Java | 25 (LTS) o superior | Spring Boot 4.1 exige Java 17 como mínimo y es compatible hasta Java 26 |
| Spring Boot | 4.1.1 | Basado en Spring Framework 7 |
| Gradle | 9.8.0 (wrapper incluido, `gradlew`) | No es necesario instalar Gradle por separado |
 
> **Importante:** todo el equipo debe usar la misma versión de Java. La versión del proyecto queda fijada en el `build.gradle` (toolchain de Java 25, Eclipse Temurin).
 
## Requisitos previos
 
- **JDK 25 o superior** instalado (`java -version` para verificarlo).
- **Git**.
- Un IDE con soporte para Java y Spring (IntelliJ IDEA, VS Code con Extension Pack for Java, Eclipse STS).
- **Docker** en ejecución (Docker Desktop, OrbStack, Colima…) para levantar PostgreSQL, MongoDB y RabbitMQ con Testcontainers.

## Levantar el proyecto en local

```bash
git clone https://github.com/FMAT-Restaurant/Menu-Backend.git
cd Menu-Backend
./gradlew bootTestRun      # En Windows: gradlew.bat bootTestRun
```

`bootTestRun` arranca la aplicación con [`TestMenuBackendApplication`](src/test/java/com/fmatrestaurant/menu/TestMenuBackendApplication.java), que levanta automáticamente PostgreSQL, MongoDB y RabbitMQ en contenedores y conecta la aplicación a ellos. No hay que instalar ni configurar ninguna base de datos.

- La API queda disponible en `http://localhost:8080`.
- Spring Security está activo: el usuario es `user` y la contraseña se imprime en la consola al arrancar (`Using generated security password: ...`).
- Los contenedores se detienen al cerrar la aplicación (`Ctrl+C`); los datos no se conservan entre ejecuciones.

> `./gradlew bootRun` todavía no funciona porque `application.properties` no tiene configuradas las conexiones a las bases de datos. Usa `bootTestRun`.

Otros comandos útiles:

| Comando | Qué hace |
| ------- | -------- |
| `./gradlew test` | Ejecuta las pruebas (requiere Docker) |
| `./gradlew checkstyleMain checkstyleTest` | Revisa el estilo del código |
| `./gradlew bootJar` | Genera el `.jar` ejecutable en `build/libs/` |

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
| `dev` | Pre-producción. Integración de las funcionalidades antes de pasar a `main` |
| `feature/<nombre>` | Ramas de trabajo para nuevas funcionalidades |
| `fix/<nombre>` | Ramas de trabajo para correcciones |
 
Reglas del repositorio:
 
1. Está **prohibido el push directo a `main`**.
2. Todo cambio entra mediante **Pull Request**.
3. Cada Pull Request requiere **al menos una revisión** de una persona distinta a quien lo creó.
4. Las ramas de trabajo se integran primero en `dev`, y `dev` se integra en `main` cuando esté listo para producción.
Flujo: `feature/xxx` → PR a `dev` → PR de `dev` a `main`.

## Convención de commits
 
Se recomienda el formato [Conventional Commits](https://www.conventionalcommits.org/es/):
 
```
feat: agregar endpoint de combos
fix: corregir validación de variantes
docs: actualizar README
```

## CI/CD

El pipeline vive en [`.github/workflows/ci.yml`](.github/workflows/ci.yml) y se ejecuta en cada push y Pull Request hacia `main` y `dev`, manualmente y cada domingo.

| Job | Qué hace | Cuándo |
| --- | -------- | ------ |
| `Test (Java 25)` | Checkstyle, pruebas JUnit con Testcontainers (PostgreSQL, MongoDB, RabbitMQ), cobertura JaCoCo, análisis SonarQube (opcional, solo si existe `SONAR_TOKEN`). | Siempre |
| `Build and Push Docker Image` | Construye la imagen del [`Dockerfile`](Dockerfile) (Temurin 25) y la publica en `ghcr.io/fmat-restaurant/menu-backend` con las etiquetas `latest` y el SHA del commit. | Solo push a `main` |

Reproducir la verificación localmente (requiere Docker para Testcontainers):

```bash
./gradlew checkstyleMain checkstyleTest test jacocoTestReport
```

Configuración necesaria en GitHub:

1. **Secrets** (*Settings > Secrets and variables > Actions*): `SONAR_TOKEN` (SonarCloud: *My Account > Security*). Es opcional: sin él, el análisis de SonarQube se omite con un aviso.
2. **Sonar**: ajustar `sonar.organization` y `sonar.projectKey` en [`sonar-project.properties`](sonar-project.properties) si difieren en SonarCloud.
3. **GHCR** (*Settings > Actions > General*): *Workflow permissions* en **Read and write permissions**.
4. **Protección de ramas** (`main` y `dev`): exigir el status check `Test (Java 25)` antes de hacer merge.
