// Local Inventory endpoints, including GET /items/{id}, the route Menu's client calls.
const http = require('http');
const items = [
  { id: '2c21767f-0f9a-4e9f-adc7-2730556c1801', name: 'Carne Molida Res 80/20', unit: 'g' },
  // Compatibility fixtures: Menu preserves opaque identifiers received from Inventory.
  { id: 'INV-beef-001', name: 'Beef patty', unit: 'unit' },
  { id: '000042', name: 'Bun', unit: 'unit' },
  { id: '5c67b890-1a2b-4d3e-8f9a-0b1c2d3e4f5a', name: 'Potatoes', unit: 'g' }
];
http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  res.setHeader('Content-Type', 'application/json');
  const itemPrefix = '/api/v1/items/';
  if (req.method === 'GET' && url.pathname.startsWith(itemPrefix)) {
    const id = decodeURIComponent(url.pathname.slice(itemPrefix.length));
    const item = items.find(candidate => candidate.id === id);
    if (item) {
      res.end(JSON.stringify(item));
    } else {
      res.writeHead(404);
      res.end(JSON.stringify({ error: { message: 'No se encontró el recurso solicitado.' } }));
    }
    return;
  }
  if (req.method !== 'GET' || url.pathname !== '/api/v1/items') {
    res.writeHead(501);
    res.end(JSON.stringify({ error: 'Endpoint not modeled' }));
    return;
  }
  const query = (url.searchParams.get('q') || '').toLowerCase();
  res.end(JSON.stringify(items.filter(item => item.name.toLowerCase().includes(query))));
}).listen(process.env.PORT);
