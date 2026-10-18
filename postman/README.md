La simulación local de Inventario conserva GET /inventory/items y modela la extensión local GET /inventory/items/{id}, con respuesta {data: {id, name, unit}} o 204 si no existe. No se buscó un mock publicado del equipo de Inventario porque la CLI no tiene una sesión autenticada. No requiere credenciales ni publica recursos.

En una terminal:

```powershell
postman simulation run postman/simulations/inventory.sim.yaml --port 4900 --output ndjson --no-history
```

En otra terminal:

```powershell
$env:MENU_INVENTORY_SIMULATION_URL = 'http://localhost:4900/inventory/api/v1'
.\gradlew.bat test --tests '*InventoryClientTest' --rerun-tasks
Remove-Item Env:MENU_INVENTORY_SIMULATION_URL
```

La prueba ejecuta el cliente HTTP real y comprueba IDs alfanuméricos, ceros iniciales, compatibilidad con UUID representados como strings y comparación exacta. Sin la variable usa el servidor HTTP local de las pruebas. El cliente hace una sola llamada a `GET /inventory/items/{id}`, consume `{data: {id, name, unit}}` y considera `204` como ausencia. La ruta por ID es una extensión del mock local, todavía no incluida en el contrato externo publicado. El contrato publicado define UUID; los IDs opacos adicionales de la simulación son fixtures de compatibilidad del cliente. Los eventos request de la simulación permiten comprobar las llamadas reales a Inventario. Esto verifica el cliente contra un sustituto local; no verifica el servicio externo desplegado.
