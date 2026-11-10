# Comportamiento de la API

El contrato servido está en [`src/main/resources/static/openapi.yaml`](../src/main/resources/static/openapi.yaml) (`http://localhost:8080/openapi.yaml`). Es una copia de [`docs/apis/menu/openapi.yaml`](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/apis/menu/openapi.yaml) del repositorio de documentación, que es la fuente de verdad: cualquier cambio al contrato se hace primero allá y luego se copia aquí.

## Versionado

Todas las rutas de Menu viven bajo `/api/{version}` (hoy `/api/v1`). Usa el versionado nativo de Spring Framework 7: la versión es el segundo segmento del path, es obligatoria bajo `/api` y una versión que ningún controlador declare (`/api/v2/...`) responde `400` con el sobre de error.

- Los controladores se anotan con `@MenuApi("/menu/...")` y declaran rutas relativas; no repiten el prefijo.
- Prefijo y versión se definen una sola vez en `ApiVersioning` (`PREFIX`, `CURRENT`); `ApiVersioningConfiguration` los conecta a Spring MVC. Los filtros de servlet (Idempotency-Key, ETag) toman la ruta de `ApiVersioning.BASE`.
- Para servir otra versión: crear una anotación equivalente a `@MenuApi` con `version = "v2"` en los controladores nuevos; la versión se detecta sola.
- El mock de Inventario (perfil `local`) simula otro servicio y queda fuera del prefijo.

## Idempotencia

Los `POST` de categorías, entradas, ofertas, recetas e imágenes y `PATCH /api/v1/menu/offers/{offerId}` exigen `Idempotency-Key` (UUID sin comillas; si falta o no es válido, `400 INVALID_IDEMPOTENCY_KEY`).

Durante 24 horas desde el primer éxito, repetir la misma solicitud con la misma clave devuelve el estado, cuerpo y `ETag` originales sin volver a ejecutarla; otra solicitud con esa clave da `409 IDEMPOTENCY_KEY_REUSED`, y una en curso, `409 IDEMPOTENCY_IN_PROGRESS` con `Retry-After`. Los registros viven en la tabla `idempotency_record`, que crean los mapeos JPA; una tarea horaria borra los vencidos.

## Eliminación de recetas

`DELETE /api/v1/menu/recipes/{recipeId}` (REQ-MENU-REC-005) exige `If-Match` y rechaza con `409` cualquier referencia desde una opción de una oferta existente, sin filtrar por estado ni revisión. La baja retira la receta de la biblioteca; conserva sus revisiones publicadas y sus ingredientes.

## Errores 404

Los errores 404 mantienen el formato `{ "error": { "message": "..." } }`. Cuando falta una entidad, el mensaje identifica el tipo y el ID; para revisiones de recetas también identifica la revisión. Las rutas inexistentes y los UUID inválidos devuelven un 404 JSON con `The requested resource was not found.`.

## CORS

CORS permite por defecto `http://localhost` y `https://localhost`, con o sin puerto, y rechaza los demás orígenes salvo que se configuren explícitamente:

```properties
menu.cors.allowed-origins=https://menu.example.com,https://admin.example.com
menu.cors.allow-localhost=true
```

O con las variables `MENU_CORS_ALLOWED_ORIGINS` y `MENU_CORS_ALLOW_LOCALHOST` (Compose las toma de `.env`). La lista debe contener orígenes HTTP(S) exactos separados por comas, sin rutas, barra final ni comodines; una configuración inválida impide el arranque. Una lista vacía permite únicamente localhost si está habilitado. En producción, establecer `MENU_CORS_ALLOW_LOCALHOST=false` y declarar los orígenes autorizados.

La política global cubre errores y recursos estáticos. Permite `GET`, `HEAD`, `POST`, `PATCH`, `DELETE` y `OPTIONS`; acepta `Accept`, `Content-Type`, `Authorization`, `If-Match`, `If-None-Match` e `Idempotency-Key`; expone `ETag`, `Location` y `Retry-After` al navegador. Los preflight se cachean una hora. No habilita credenciales de cookies cross-origin; el token explícito en `Authorization` sí está permitido. CORS no sustituye la autenticación ni restringe clientes sin cabecera `Origin`.

## Integración con Inventario

`InventoryClient.find` hace una sola consulta a `GET {INVENTORY_BASE_URL}/items/{id}` y consume el objeto plano `{id, name, unit}` (sin envoltorio `data`), validando que el ID recibido coincida exactamente con el solicitado. Devuelve vacío únicamente cuando Inventario confirma la inexistencia con un `404` que trae su sobre de error `{error: {message}}`; un `404` sin ese sobre (una URL base equivocada o un proxy), un `204`, cualquier otro error HTTP, un timeout o una respuesta incompleta se propagan como fallo de Inventario.

| Resultado de la consulta | Respuesta de Menu en ofertas y recetas |
| ------------------------ | -------------------------------------- |
| Artículo encontrado | La operación continúa |
| Inventario confirma que no existe (`404` con sobre de error) | `422`, sin cambios |
| Timeout, indisponibilidad, `5xx`, `401`/`403`/`429` o respuesta inválida | `502`, sin cambios |

Ni un `422` ni un `502` dejan cambios parciales, y tras recuperarse Inventario la misma solicitud (con la misma `Idempotency-Key` en las operaciones que la exigen) puede reintentarse.

Las recetas y las opciones de inventario de las ofertas reciben `inventoryItemId` como string opaco (por ejemplo, `INV-beef-001` o `000042`), sin exigir formato UUID. Menu conserva el identificador exacto, el nombre y la unidad. Los identificadores propios de Menu siguen siendo UUID.

### Mock de Inventario (perfil `local`)

`InventoryMockController` expone un inventario fijo **solo con el perfil `local`**. Con cualquier otro perfil no existe y las consultas se delegan al servicio configurado en `INVENTORY_BASE_URL`.

- `GET /api/v1/items` (también `/items`): lista plana de 10 artículos con UUID válidos, sin paginación. Acepta `q` (búsqueda por nombre sin distinguir mayúsculas).
- `GET /items/{id}` (también bajo `/api/v1`): devuelve `{id, name, unit}`, o `404` con el sobre de error si el ID no existe. Ejemplo: `/api/v1/items/2c21767f-0f9a-4e9f-adc7-2730556c1801`.

Para usarlo, asignar `INVENTORY_BASE_URL=http://localhost:8080` (la raíz del servicio, sin `/api/v1`). El mock no necesita token ni base de datos.

Para probar el cliente contra una simulación de Postman, ver [`postman/README.md`](../postman/README.md).

## Migraciones manuales

No hay herramienta de migraciones: Hibernate (`ddl-auto=update`, solo desarrollo) crea el esquema en bases nuevas. En bases existentes, detener el backend y ejecutar estos scripts antes de iniciar la versión que los introduce:

| Script | Cambio |
| ------ | ------ |
| [`sql/inventory-item-id-to-text.sql`](sql/inventory-item-id-to-text.sql) | `component_ingredient.inventory_item_id` y `slot_option.inventory_item_id` pasan de UUID a `text` |
| [`sql/recipe-deletion.sql`](sql/recipe-deletion.sql) | Agrega `recipe.deleted` para la baja lógica de recetas |
