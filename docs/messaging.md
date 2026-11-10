# Mensajería con RabbitMQ

Menu usa Spring AMQP (`spring-boot-starter-amqp`), cuya versión gestiona Spring Boot.

## Publicación

Cada vez que se crea, actualiza o elimina una *fuente* (una receta de la biblioteca o una opción con un artículo de Inventario), Menu publica un evento en el exchange `menu.topic` (topic, durable) **después de confirmar la transacción**; si la solicitud se rechaza o se revierte no se publica nada. La publicación es asíncrona y nunca afecta a la respuesta HTTP.

| Routing key | Cuándo | Payload |
| ----------- | ------ | ------- |
| `menu.source.created` | Receta creada, u opción de Inventario nueva | `{sourceId, name, items: [{id, quantity}]}` |
| `menu.source.updated` | Receta con nueva revisión, u opción de Inventario cuyo artículo o cantidad cambió | `{sourceId, name, items: [{id, quantity}]}` |
| `menu.source.deleted` | Receta eliminada; opción de Inventario retirada, o eliminada junto con su oferta o entrada | `{sourceId}` |

`sourceId` es el id de la receta o el id de la opción; `name` es `null` en opciones de Inventario. Cada mensaje lleva `messageId` (id del evento), `correlationId` (`sourceId`) y la cabecera `X-Event-Type`.

### Manejo de errores

No hay outbox, así que un evento que no se entrega se pierde; lo que se garantiza es que **nunca se pierde en silencio**:

| Situación | Qué hace Menu |
| --------- | ------------- |
| Fallo de conexión o envío | Reintenta con espera creciente (3 intentos, `spring.rabbitmq.template.retry.*`; conexión con timeout de 5 s). Si se agotan, registra un `ERROR` con `eventId`, `type`, `sourceId` y `routingKey`, los datos para volver a anunciar la fuente a mano |
| El broker rechaza el mensaje (nack) o no confirma en 10 s | `ERROR` con los mismos campos y la causa (usa *publisher confirms*) |
| Ninguna cola recibe el mensaje (nadie enlazado a `menu.source.*`) | `WARN` `not routed to any queue` (*mandatory* + *returns*) |
| Apagado de la aplicación mientras se publica | `ERROR` con los mismos campos; nunca rompe una solicitud ya confirmada |

## Consumo

Menu declara la cola `menu.inventory.availability.queue` (con DLQ `menu.inventory.availability.dlq` en `menu.dlx`) y la enlaza a `inventory.topic` con la routing key de `INVENTORY_AVAILABILITY_ROUTING_KEY` (por defecto `inventory.recipe.availability-changed`). Por ahora solo registra los mensajes recibidos. Ante un fallo del listener (o un mensaje ilegible) hace 3 intentos (`spring.rabbitmq.listener.simple.retry.*`) y, si fallan, republica el mensaje en `menu.dlx` → DLQ con la causa en la cabecera `x-exception-message`; nunca se reencola. Nada consume la DLQ: revisarla es manual.

## Conexión

- Se configura con una sola URL, `amqp(s)://usuario:clave@host[:puerto][/vhost]`, que Spring lee como `spring.rabbitmq.addresses` (`SPRING_RABBITMQ_ADDRESSES`). En Compose viene de `RABBITMQ_URL` (o, si está vacía, del broker del propio Compose); en local, de `application-local.properties`. Si no se define, Spring usa sus valores por defecto (`localhost:5672`, vhost `/`, `guest`/`guest`).
- La conexión es perezosa: el API arranca aunque el broker no esté disponible.

Comprobar que el broker de Compose está listo:

```bash
docker compose up -d rabbitmq
docker compose ps rabbitmq                                  # el estado debe ser "healthy"
docker compose exec rabbitmq rabbitmq-diagnostics -q ping   # responde "Ping succeeded"
```

La prueba de integración `RabbitMqConnectionTest` valida con Testcontainers que el backend se conecta a RabbitMQ.
