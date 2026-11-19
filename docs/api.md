# Comportamiento de la API

El contrato servido está en [`src/main/resources/static/openapi.yaml`](../src/main/resources/static/openapi.yaml) (`http://localhost:8080/openapi.yaml`). Es el bundle publicado [`docs/apis/menu/dist/openapi.yaml`](https://github.com/FMAT-Restaurant/Menu-Documentation/blob/main/docs/apis/menu/dist/openapi.yaml) del repositorio de documentación, que es la fuente de verdad: no se edita a mano.

## Sincronizar el contrato

La versión del contrato se fija en [`gradle.properties`](../gradle.properties):

- `menu.contract.version`: el `info.version` esperado (hoy `8.0.1`).
- `menu.contract.ref`: tag o commit de Menu-Documentation del que se descarga (hoy el commit `566db96`, porque aún no hay tags).

```bash
./gradlew syncOpenApi
```

La tarea descarga el bundle de ese `ref`, falla si su `info.version` no coincide y lo escribe en `static/openapi.yaml`. Para subir de versión: cambiar ambas propiedades, ejecutar la tarea y commitear el contrato. El job `contract` de CI falla si el contrato servido difiere del publicado (ver [`ci.md`](ci.md)).

### Pruebas de contrato

- `ContractValidation.conformsToContract()` (en `src/test/.../api/`) valida una respuesta de MockMvc contra el contrato servido: `.andExpect(conformsToContract())`. Solo valida la respuesta; la solicitud y la autenticación no se revisan.
- `OpenApiSpecTest` compara los `operationId` del contrato con los handlers registrados. Las operaciones sin implementar están en la lista `PENDING`; cada ticket que implementa una la quita de ahí. `/items` y `/items/{id}` se excluyen porque los sirve Inventario.

## Permisos

Cada operación exige los permisos de su `x-required-permission` en el contrato (RF-GEN-01). Se leen de la claim `permissions` del JWT de `Authorization: Bearer …`: un arreglo de strings `menu:<recurso>:<accion>`.

> [!WARNING]
> Hasta BE-12 (#63), Menu decodifica el JWT **sin verificar su firma** (`UnverifiedJwtPermissions`). No debe desplegarse expuesto sin el Gateway delante.

- **401** (`Unauthorized`): no hay credencial, no es un JWT, o `permissions` no es un arreglo de strings.
- **403** (`Forbidden`): falta un permiso exigido. Se responde antes de la idempotencia y de cualquier escritura, así que no queda ningún efecto: ni datos ni `Idempotency-Key` ligada.
- Un token **sin** `permissions` equivale a una lista vacía: 403, no 401.
- **`"*"`** concede todos los permisos (administradores). El comodín no aparece en la ERS ni en el contrato.
- **Sin credencial:** `/openapi.yaml`, el preflight `OPTIONS`, los archivos de imagen (`GET` y `HEAD /api/v1/menu/media/images/{imageId}/file`) y el mock de Inventario del perfil `local` (`/items`, `/api/v1/items`).
- **Rutas que no existen:** con una credencial legible siguen respondiendo `404`/`400`. No tienen handler, así que no hay nada que proteger.
- **PATCH de entradas y ofertas** (RF-GEN-01.CA3): el cuerpo decide el permiso.
  - Solo cambios de `status` → `…:status`.
  - Sin cambios de `status` → `…:write`.
  - Ambos → los dos.
  - En ofertas, el `status` de una opción existente cuenta como cambio de estado. La `composition` solo cuenta como cambio de estado si lista exactamente los grupos y opciones actuales, en el mismo orden, y no trae más que sus ids, `sourceType` y `status`; si añade, quita, reordena o edita miembros, exige `write`.
- **Imágenes públicas (#87):** `url` y `thumbnailUrl` apuntan a `GET /api/v1/menu/media/images/{imageId}/file`.
  - La ruta está dentro de la API, con sus prefijos comunes y junto a la imagen, así que el Gateway la reenvía igual que el resto de `/api/v1/menu/**`.
  - Sirve el archivo **sin permisos ni token**, así que un `<img src>` funciona.
  - El contrato no la define.
  - Responde con `Content-Type`, `Cache-Control: no-cache, public` y un `ETag`, que es el SHA-256 del archivo y cambia si se reemplaza (BE-17). Con un `If-None-Match` vigente responde 304.
  - `GET /menu/media/images/{imageId}` (`getMenuImage`) sigue exigiendo `menu:images:write`, como dice el contrato. Solo sirve para obtener el ETag y preparar el reemplazo del archivo.

### Implementación

- `CredentialPermissions` (`application/security`) lee la credencial. BE-12 sustituirá `UnverifiedJwtPermissions` por una versión que verifique la firma.
- `SecurityConfiguration` arma la cadena de Spring Security, que corre antes de `IdempotencyFilter`:
  - `BearerPermissionsFilter` carga los permisos;
  - `PermissionAuthorizationManager` decide con la tabla `OperationPermissions` y con `PatchPermissionRule`.
- No se usa `@PreAuthorize`, porque se evaluaría después de `IdempotencyFilter`. Una solicitud sin permiso recibiría `400`/`409` de idempotencia en lugar de `403`, y un `403` dejaría la clave ligada.
- `OperationPermissionsContractTest` comprueba tres cosas:
  - que la tabla coincide con `x-required-permission` del contrato servido;
  - que cada handler tiene regla;
  - que con solo su permiso ninguna operación responde 403.
- En las pruebas:
  - `TestTokens` genera los JWT;
  - `TestSecurityConfiguration` envía TK-ADMIN por defecto en MockMvc;
  - `PermissionsApiIntegrationTest` no lo importa: cada solicitud lleva su propia credencial.

## Disponibilidad

`isAvailable` es la disponibilidad derivada y de solo lectura de opciones, grupos de selección, ofertas y entradas (RF-DISP-01 a RF-DISP-04). `DerivedAvailability` la calcula al armar cada respuesta, a partir de lo que comunicó Inventario ([messaging.md](messaging.md)), de la composición y de los estados, así que nunca queda desactualizada y nada se propaga. No cambia ningún `status`, y un `isAvailable` enviado en un POST o PATCH se ignora.

| Elemento | Disponible si y solo si |
| -------- | ----------------------- |
| Opción `RECIPE` | Inventario comunicó su receta como disponible (todas las revisiones valen lo mismo) |
| Opción `INVENTORY_ITEM` | Inventario comunicó la propia opción como disponible |
| Grupo | Al menos una de sus opciones está `ACTIVE` y disponible |
| Oferta | Todos sus grupos están disponibles (su `status` no interviene) |
| Entrada | Al menos una de sus ofertas está `ACTIVE` y disponible; sin ofertas activas, `false` |

El `status` de una opción no interviene en su propia disponibilidad (RF-DISP-01.CA3). Hasta que Inventario comunica un origen, este no está disponible.

### ETag de entradas y ofertas

La disponibilidad cambia sin que cambie la versión de la entrada o la oferta, por un evento de Inventario o por activar o inactivar ofertas. Por eso su ETag lleva una huella: `"rev-<versión>-<huella>"`.

- **Entrada:** la huella es `a` (disponible) o `u` (no disponible).
- **Oferta:** la huella es un CRC32 de los valores `isAvailable` de la oferta, sus grupos y sus opciones.

Así, un `If-None-Match` con un ETag anterior al cambio responde 200 con el valor nuevo, no 304. `If-Match` compara solo la versión, así que un cambio de disponibilidad no hace fallar con 412 la edición de un administrador; también se acepta `"rev-<versión>"`. Categorías y recetas siguen con `"rev-<versión>"`.

## Catálogo visible

`GET /menu/catalog` y `GET /menu/catalog/categories` presentan el catálogo a quien tiene «Ver catálogo» (`menu:catalog:read`), conforme a RF-CONS-01 a RF-CONS-03.

- **Qué se presenta:** solo las entradas `ACTIVE`, sus ofertas `ACTIVE` y, en cada grupo, sus opciones `ACTIVE`. Lo que no está disponible sí se presenta, con `isAvailable: false`, para que el cliente lo muestre deshabilitado. Una entrada `ACTIVE` sin ofertas `ACTIVE` aparece con `offers: []` e `isAvailable: false`.
- **Orden:**
  - entradas por nombre comercial;
  - ofertas por etiqueta de presentación;
  - grupos y opciones en el orden del administrador.
- **Campos:** solo los que presenta el cliente. No hay `status`, origen, cantidades de Inventario ni opción predeterminada.
- **Búsqueda y filtro:** `q` busca en el nombre comercial, la descripción y los nombres de categoría. `categoryId` admite una categoría o `__uncategorized__`; una categoría inexistente devuelve una página vacía.
- **Categorías:** se devuelven todas, sin paginar y en orden alfabético, también las vacías. `entryCount` cuenta solo las entradas `ACTIVE`, y `uncategorized.entryCount`, las entradas `ACTIVE` sin categorías.
- **ETag:** ambas respuestas llevan un ETag calculado sobre el cuerpo. Por eso cambia también con `isAvailable`, y un `If-None-Match` vigente responde 304.

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

`InventoryClient.find` hace una sola consulta a `GET {INVENTORY_BASE_URL}/items/{id}` y consume el objeto plano `{id, name, unit}` (sin envoltorio `data`), validando que el ID recibido coincida exactamente con el solicitado. Devuelve vacío únicamente cuando Inventario confirma la inexistencia con un `404` que trae su sobre de error `{error: {message}}` **y** la colección `GET {INVENTORY_BASE_URL}/items?q={id}` responde `2xx`. Esa segunda consulta (solo en el caso de un `404`) descarta una URL base equivocada: un servidor que no conoce la ruta puede responder con el mismo sobre (las rutas inexistentes de Menu lo hacen), y sin ella cada artículo se reportaría como inexistente. Si la colección tampoco responde, es un fallo de Inventario (`502`) y queda en el log un aviso que indica revisar `INVENTORY_BASE_URL`; un `404` sin ese sobre (una URL base equivocada o un proxy), un `204`, cualquier otro error HTTP, un timeout o una respuesta incompleta se propagan como fallo de Inventario.

| Resultado de la consulta | Respuesta de Menu en ofertas y recetas |
| ------------------------ | -------------------------------------- |
| Artículo encontrado | La operación continúa |
| Inventario confirma que no existe (`404` con sobre de error y colección `/items` alcanzable) | `422`, sin cambios |
| Timeout, indisponibilidad, `5xx`, `401`/`403`/`429`, respuesta inválida o URL base equivocada | `502`, sin cambios |

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
| [`sql/source-availability.sql`](sql/source-availability.sql) | Agrega `slot_option.source_available` y la tabla `processed_event` para la disponibilidad que comunica Inventario |
