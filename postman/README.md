La simulación local de Inventario modela GET /items?q=, que devuelve la lista plana de artículos sin paginación, y GET /items/{id}, la ruta que usa el cliente de Menu, con respuesta plana {id, name, unit} o 404 con el sobre de error {error: {message}} si no existe. No se buscó un mock publicado del equipo de Inventario porque la CLI no tiene una sesión autenticada. No requiere credenciales ni publica recursos.

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

La prueba ejecuta el cliente HTTP real y comprueba IDs alfanuméricos, ceros iniciales, compatibilidad con UUID representados como strings y comparación exacta. Sin la variable usa el servidor HTTP local de las pruebas. El cliente hace una sola llamada a `GET {base-url}/items/{id}`, consume el objeto plano `{id, name, unit}` y solo considera ausente un `404` con el sobre de error; un `204` o un `404` sin sobre son fallo. El contrato publicado define UUID; los IDs opacos adicionales de la simulación son fixtures de compatibilidad del cliente. Los eventos request de la simulación permiten comprobar las llamadas reales a Inventario. Esto verifica el cliente contra un sustituto local; no verifica el servicio externo desplegado.
