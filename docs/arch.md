# Arquitectura por capas

Este documento describe cómo se organiza el código de `menu-backend` y qué reglas siguen las dependencias entre capas.

## De .NET a Spring Boot

En .NET lo habitual es separar cada capa en un **proyecto** dentro de una solución (`BookingService.Api`, `BookingService.Domain`, ...). En Spring Boot el estándar para un microservicio es **un solo proyecto Gradle** donde cada capa es un **paquete** de Java:

| .NET (proyecto)                   | Spring Boot (paquete)                          |
| --------------------------------- | ---------------------------------------------- |
| `BookingService.Api`              | `com.fmatrestaurant.menu.api`                  |
| `BookingService.Application`      | `com.fmatrestaurant.menu.application`          |
| `BookingService.Domain`           | `com.fmatrestaurant.menu.domain`               |
| `BookingService.Infrastructure`   | `com.fmatrestaurant.menu.infrastructure`       |
| `BookingService.Tests`            | `src/test/java` (mismos paquetes que `src/main/java`) |

Las pruebas no van en un proyecto aparte: por convención de Gradle/Maven viven en `src/test/java` y **replican los paquetes** del código que prueban. Así una prueba puede acceder a clases con visibilidad de paquete y Gradle las compila y ejecuta por separado sin incluirlas en el `.jar` final.

## Estructura

```
src/
├── main/java/com/fmatrestaurant/menu/
│   ├── MenuBackendApplication.java   # Punto de entrada (debe quedarse en el paquete raíz)
│   ├── api/                          # Controladores REST, DTOs HTTP, manejo de errores HTTP
│   ├── application/                  # Casos de uso, orquestación, DTOs de aplicación, validaciones
│   ├── domain/                       # Entidades JPA (Producto, Variante, Modificador, Combo) con sus reglas de negocio
│   └── infrastructure/               # Repositorios Spring Data, mensajería (RabbitMQ), configuración técnica
└── test/java/com/fmatrestaurant/menu/
    ├── MenuBackendApplicationTests.java  # Prueba de arranque del contexto
    ├── TestcontainersConfiguration.java  # PostgreSQL 18.6 para pruebas
    ├── api/                          # Pruebas de controladores (@WebMvcTest)
    ├── application/                  # Pruebas unitarias de casos de uso (JUnit + Mockito)
    ├── domain/                       # Pruebas unitarias de reglas de negocio (JUnit, sin Spring)
    └── infrastructure/               # Pruebas de integración con Testcontainers (@DataJpaTest)
```

`MenuBackendApplication` se queda en el paquete raíz porque `@SpringBootApplication` escanea su paquete y todos los subpaquetes; si se moviera a una capa, Spring no encontraría los componentes de las demás.

> **Nota:** la persistencia es PostgreSQL 18.6 + Spring Data JPA; las dependencias ya están, pero todavía no hay entidades. RabbitMQ (Spring AMQP) está aprobado en el stack, pero si Menu lo usa depende de la topología, que sigue sin decidir. Los límites transaccionales y AuthN/AuthZ también están pendientes. Las referencias a RabbitMQ en este documento describen el diseño previsto, no una dependencia que ya exista.

## Responsabilidades

| Capa | Contiene | No contiene |
| ---- | -------- | ----------- |
| `api` | `@RestController`, records de request/response, `@RestControllerAdvice` | Reglas de negocio, acceso a datos |
| `application` | `@Service` con los casos de uso, DTOs, validaciones de entrada. Los límites transaccionales están pendientes de decisión | Detalles HTTP, SQL o consultas |
| `domain` | Entidades con anotaciones JPA, value objects (`@Embeddable`), invariantes del menú como métodos de las entidades | Servicios, repositorios, anotaciones de Spring (`@Service`, `@Component`) |
| `infrastructure` | Interfaces de Spring Data (`JpaRepository`), publicadores RabbitMQ, `@Configuration` | Reglas de negocio |

## Regla de dependencias

```
api ──► application ──► infrastructure
             │                │
             └───► domain ◄───┘
```

- `domain` no depende de ninguna otra capa del proyecto. Solo usa anotaciones de JPA (`jakarta.persistence`) para mapear sus entidades.
- `application` usa `domain` y los repositorios de `infrastructure`.
- `api` solo llama a `application`; nunca a `infrastructure` directamente.
- `infrastructure` contiene las interfaces de Spring Data, tipadas con las entidades de `domain`.

Ejemplo del flujo para consultar un producto:

```
ProductoController (api)
  → ProductoService (application)
    → ProductoRepository extends JpaRepository<Producto, Long> (infrastructure)
      → Producto @Entity (domain)
```

## Dominio pragmático

Se decidió **no** separar el modelo de dominio del modelo de persistencia para no escribir código duplicado:

- Las entidades de `domain` llevan directamente `@Entity`, `@Id`, `@OneToMany`, etc. No hay una segunda clase "entidad JPA" ni mappers entre ambas.
- Las reglas de negocio e invariantes viven como métodos en esas mismas entidades (por ejemplo, `combo.agregarProducto(...)` valida sus reglas), no en los servicios.
- Los repositorios son interfaces de Spring Data en `infrastructure` (`extends JpaRepository`). Spring genera la implementación; no hay interfaces propias en `domain` ni clases que las implementen.

Si en el futuro el modelo de persistencia y el de dominio divergen (por ejemplo, una tabla no se parece a la entidad de negocio), se separan solo en ese caso.

## Convenciones

- Dentro de cada capa se agrupa por concepto del dominio cuando crezca, por ejemplo `domain/producto`, `domain/combo`.
- Los DTOs de `api` (request/response) no llegan a `domain`; los controladores reciben un DTO y `application` lo convierte en entidad. Las entidades no se devuelven directamente en las respuestas HTTP.
- Cada capa tiene un `package-info.java` que documenta su propósito.

## Pruebas

| Capa | Tipo de prueba | Herramientas |
| ---- | -------------- | ------------ |
| `domain` | Unitaria | JUnit 6 |
| `application` | Unitaria | JUnit 6 + Mockito |
| `api` | Slice web | `@WebMvcTest`, MockMvc |
| `infrastructure` | Integración | `@DataJpaTest` + Testcontainers |

Todas se ejecutan con:

```bash
./gradlew test
```

Las pruebas con `@SpringBootTest` y las de `infrastructure` requieren Docker: [`TestcontainersConfiguration`](../src/test/java/com/fmatrestaurant/menu/TestcontainersConfiguration.java) levanta PostgreSQL 18.6.

## Alternativa: multi-módulo Gradle

Si en el futuro se necesita que el compilador **impida** dependencias incorrectas (por ejemplo, que `domain` importe algo de `infrastructure`), se puede migrar a un proyecto multi-módulo de Gradle (`menu-api`, `menu-application`, `menu-domain`, `menu-infrastructure`), equivalente a los proyectos de .NET. Para un solo microservicio se descartó por ahora: añade configuración sin aportar valor mientras el equipo respete la regla de dependencias en las revisiones de PR.
