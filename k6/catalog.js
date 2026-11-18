// Pruebas de rendimiento de la consulta, búsqueda y filtrado del catálogo (RNF-REND-01, 02 y 03).
// https://fmat-restaurant.github.io/Menu-Documentation/specification/non-functional-requirements/
//
//   k6 run -e BASE_URL=http://localhost:8080 k6/catalog.js
//
// La API debe correr con el perfil `local` (mock de Inventario) para que setup() pueda crear ofertas.
import http from 'k6/http';
import { check, fail } from 'k6';
import encoding from 'k6/encoding';

const BASE = `${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1/menu`;
const NOMINAL = __ENV.NOMINAL_DURATION || '30m'; // RNF-REND-02: sostenido 30 minutos
const BURST = __ENV.BURST_DURATION || '60s'; // RNF-REND-03: 60 segundos
const CATEGORIES = Number(__ENV.CATEGORIES || 12);
const ENTRIES = Number(__ENV.ENTRIES || 150);
const OFFERS_PER_ENTRY = 2;
// Los nombres de categoría son únicos por menú: el sufijo permite repetir la prueba sobre la misma base.
const RUN = Date.now().toString(36);
const UNCATEGORIZED = '__uncategorized__';
const WORDS = ['tacos', 'pollo', 'res', 'cerdo', 'queso', 'arroz', 'frijol', 'huevo', 'salsa', 'tortilla'];
// Artículo del mock de Inventario (InventoryMockController, perfil local).
const ITEM = '2c21767f-0f9a-4e9f-adc7-2730556c1802';
// PNG de 1x1 px: Menu exige imagen en entradas y ofertas.
const PNG = encoding.b64decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC');
// Menu exige un JWT con permissions (BE-13). Mientras no verifique la firma (BE-12), bastan tokens sin firmar:
// el comodín de administrador para sembrar y solo «Ver catálogo» para consultar, como quien consulta el catálogo.
// TOKEN y CATALOG_TOKEN permiten usar tokens reales.
const unsigned = (permissions) => [{ alg: 'none', typ: 'JWT' }, { sub: 'k6', permissions }]
  .map((part) => encoding.b64encode(JSON.stringify(part), 'rawurl')).join('.') + '.';
const AUTH = { Authorization: `Bearer ${__ENV.TOKEN || unsigned(['*'])}` };
const CATALOG_AUTH = { Authorization: `Bearer ${__ENV.CATALOG_TOKEN || unsigned(['menu:catalog:read'])}` };

export const options = {
  setupTimeout: '10m',
  scenarios: {
    // RNF-REND-02: hasta 40 sesiones concurrentes y 30 solicitudes por segundo.
    nominal: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1s',
      duration: NOMINAL,
      preAllocatedVUs: 40,
      maxVUs: 40,
    },
    // RNF-REND-03: 100 solicitudes por segundo durante 60 s con hasta 40 sesiones, tras el perfil nominal.
    burst: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: BURST,
      preAllocatedVUs: 40,
      maxVUs: 40,
      startTime: NOMINAL,
    },
  },
  thresholds: {
    // RNF-REND-02: p95 <= 200 ms, p99 <= 1 s y errores internos < 0.1 % de lo ofrecido.
    'http_req_duration{scenario:nominal}': ['p(95)<=200', 'p(99)<=1000'],
    'http_req_failed{scenario:nominal}': ['rate<0.001'],
    // RNF-REND-03: sin latencias nominales; solo se exige que Menu siga en pie (ver teardown).
    'checks{check:alive after burst}': ['rate==1'],
    // Desglose por operación, para ubicar cuál se degrada.
    ...Object.fromEntries(['catalog', 'search', 'filter', 'categories'].map(
      (name) => [`http_req_duration{scenario:nominal,name:${name}}`, ['p(95)<=200']])),
  },
  // RNF-REND-03.CA2: el reporte cuenta completadas, fallidas y pendientes (dropped_iterations).
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max', 'count'],
};

function post(path, body, params = {}) {
  const res = http.post(`${BASE}${path}`, body, {
    ...params,
    headers: { ...AUTH, 'Idempotency-Key': crypto.randomUUID(), ...params.headers },
    tags: { name: 'setup' },
  });
  if (res.status !== 201) fail(`POST ${path} -> ${res.status} ${res.body}`);
  return { ...res.json('data'), etag: res.headers.Etag };
}

const json = { headers: { 'Content-Type': 'application/json' } };

// Dataset representativo: categorías, entradas activas (una de cada diez sin categoría) y sus ofertas activas,
// porque el catálogo solo presenta lo ACTIVE.
export function setup() {
  const image = (name) => post('/media/images', { file: http.file(PNG, `${name}.png`, 'image/png') }).id;
  const categories = [];
  for (let i = 0; i < CATEGORIES; i++) {
    categories.push(post('/categories', JSON.stringify({ name: `categoria ${i} ${WORDS[i % WORDS.length]} ${RUN}` }), json).id);
  }
  const entries = [];
  const offers = [];
  for (let i = 0; i < ENTRIES; i++) {
    const entry = post('/entries', JSON.stringify({
      brandName: `Platillo ${i} de ${WORDS[i % WORDS.length]}`,
      description: `Preparado con ${WORDS[(i + 3) % WORDS.length]} y ${WORDS[(i + 7) % WORDS.length]}`,
      imageId: image(`entry-${i}`),
      categoryIds: i % 10 === 0 ? [] : [categories[i % CATEGORIES], categories[(i + 5) % CATEGORIES]],
    }), json);
    const activated = http.patch(`${BASE}/entries/${entry.id}`, JSON.stringify({ status: 'ACTIVE' }),
      { headers: { ...AUTH, ...json.headers, 'If-Match': entry.etag }, tags: { name: 'setup' } });
    if (activated.status !== 200) fail(`PATCH entry -> ${activated.status} ${activated.body}`);
    entries.push(entry.id);
    for (let j = 0; j < OFFERS_PER_ENTRY; j++) {
      const offer = post(`/entries/${entry.id}/offers`, JSON.stringify({
        presentationTag: j === 0 ? 'Individual' : 'Orden',
        basePrice: 80 + 20 * j,
        imageId: image(`offer-${i}-${j}`),
        composition: { slots: [{ name: 'Proteína', quantity: 1, course: 'plato fuerte', options: [
          { displayName: 'Pollo', sourceType: 'INVENTORY_ITEM', inventoryItemId: ITEM, quantity: 150 },
        ] }] },
      }), json);
      const active = http.patch(`${BASE}/offers/${offer.id}`, JSON.stringify({ status: 'ACTIVE' }), {
        headers: { ...AUTH, ...json.headers, 'If-Match': offer.etag, 'Idempotency-Key': crypto.randomUUID() },
        tags: { name: 'setup' },
      });
      if (active.status !== 200) fail(`PATCH offer -> ${active.status} ${active.body}`);
      offers.push(offer.id);
    }
  }
  return { categories, entries, offers };
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];

// La mezcla de clases está pendiente (OPEN-009): se reparte por igual entre las cuatro operaciones del catálogo
// visible (BE-14), consultadas con solo «Ver catálogo».
const OPERATIONS = [
  // RF-CONS-01/02: página de entradas con sus ofertas vendibles y su composición.
  (d) => http.get(`${BASE}/catalog?page=${1 + Math.floor(Math.random() * 5)}&pageSize=12`,
    { headers: CATALOG_AUTH, tags: { name: 'catalog' } }),
  // RF-CONS-03.CA1: búsqueda por texto.
  (d) => http.get(`${BASE}/catalog?q=${pick(WORDS)}`, { headers: CATALOG_AUTH, tags: { name: 'search' } }),
  // RF-CONS-03.CA2: filtro por categoría o «sin categoría».
  (d) => http.get(`${BASE}/catalog?categoryId=${Math.random() < 0.1 ? UNCATEGORIZED : pick(d.categories)}`,
    { headers: CATALOG_AUTH, tags: { name: 'filter' } }),
  // RF-CONS-01.CA4: categorías del catálogo y la agrupación «sin categoría».
  () => http.get(`${BASE}/catalog/categories`, { headers: CATALOG_AUTH, tags: { name: 'categories' } }),
];

export default function (data) {
  const res = pick(OPERATIONS)(data);
  check(res, { 'status 200': (r) => r.status === 200 });
}

// RNF-REND-03.CA1: Menu no cae ni reinicia durante la ráfaga.
export function teardown() {
  check(http.get(`${BASE}/catalog/categories`, { headers: CATALOG_AUTH }),
    { 'alive after burst': (r) => r.status === 200 });
}
