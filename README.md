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
| Frontend | [Frontend Repository](URL_DEL_REPO_FRONTEND) |

## Tecnologías
 
| Tecnología | Versión | Notas |
| ---------- | ------- | ----- |
| Java | 21 (LTS) o superior | Spring Boot 4.1 exige Java 17 como mínimo y es compatible hasta Java 26 |
| Spring Boot | 4.1.1 | Basado en Spring Framework 7 |
| Maven | Wrapper incluido (`mvnw`) | No es necesario instalar Maven por separado |
 
> **Importante:** todo el equipo debe usar la misma versión de Java. La versión del proyecto queda fijada en el `pom.xml`.
 
## Requisitos previos
 
- **JDK 21 o superior** instalado (`java -version` para verificarlo).
- **Git**.
- Un IDE con soporte para Java y Spring (IntelliJ IDEA, VS Code con Extension Pack for Java, Eclipse STS).


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