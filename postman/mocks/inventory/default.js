// Local Inventory endpoints, including the GET /inventory/items/{id} extension.
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
  const itemPrefix = '/api/v1/inventory/items/';
  if (req.method === 'GET' && url.pathname.startsWith(itemPrefix)) {
    const id = decodeURIComponent(url.pathname.slice(itemPrefix.length));
    const item = items.find(candidate => candidate.id === id);
    if (item) {
      res.end(JSON.stringify({ data: item }));
    } else {
      res.writeHead(204);
      res.end();
    }
    return;
  }
  if (req.method !== 'GET' || url.pathname !== '/api/v1/inventory/items') {
    res.writeHead(501);
    res.end(JSON.stringify({ error: 'Endpoint not modeled' }));
    return;
  }
  const page = Number(url.searchParams.get('page') || 1);
  const pageSize = Number(url.searchParams.get('pageSize') || 12);
	if (!Number.isInteger(page) || page < 1 || !Number.isInteger(pageSize) || pageSize < 1) {
	  res.writeHead(422);
	  res.end(JSON.stringify({ error: { message: 'page and pageSize must be positive integers' } }));
	  return;
	}
  const query = (url.searchParams.get('q') || '').toLowerCase();
  const matches = items.filter(item => item.name.toLowerCase().includes(query));
  const etag = '"' + Buffer.from(JSON.stringify([query, page, pageSize, matches])).toString('base64') + '"';
  res.setHeader('ETag', etag);
  if ((req.headers['if-none-match'] || '').split(',').some(value =>
    value.trim() === '*' || value.trim().replace(/^W\//, '') === etag)) {
    res.writeHead(304);
    res.end();
    return;
  }
  res.end(JSON.stringify({
    data: matches.slice((page - 1) * pageSize, page * pageSize),
    meta: { page, pageSize, total: matches.length, totalPages: Math.ceil(matches.length / pageSize) }
  }));
}).listen(process.env.PORT);
