// Genera volumen de datos por la API, con los casos que estresan la plata:
// ganancia global con decimales, compras con centavos, pesables con 3
// decimales, descuentos por producto y por categoria, combos NxM, ventas
// con los cuatro metodos de pago, reintentos con la misma Idempotency-Key,
// anulaciones de ventas y compras, compras a cuenta pagadas despues, gastos,
// retiros, aportes, una reversion manual, un sueldo y tres arqueos (con
// sobrante, exacto y con faltante).
//
// Es deterministico (PRNG con semilla fija) para que dos corridas sobre la
// misma base dejen los mismos datos. Se corre UNA vez, con la version vieja
// (double), antes de capturar la linea de base.
//
// Uso: node generar-volumen.mjs <base_url> <usuario> <password> [ventas=400]

import { conectar } from "./api.mjs";

const [base, usuario, password, cuantasArg] = process.argv.slice(2);
const CUANTAS_VENTAS = Number(cuantasArg ?? 400);

// mulberry32: chico, deterministico, suficiente para elegir productos.
let semilla = 20260925;
function azar() {
  semilla |= 0;
  semilla = (semilla + 0x6d2b79f5) | 0;
  let t = Math.imul(semilla ^ (semilla >>> 15), 1 | semilla);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const entero = (min, max) => min + Math.floor(azar() * (max - min + 1));
const elegir = (lista) => lista[Math.floor(azar() * lista.length)];
const METODOS = ["EFECTIVO", "EFECTIVO", "EFECTIVO", "TARJETA", "TRANSFERENCIA", "OTRO"];

const api = await conectar(base, usuario, password);
const log = (...a) => console.log(...a);

const productos = (await api.ok("GET", "/productos")).filter((p) => p.nombre);
const pesables = productos.filter((p) => p.tipoCantidad === "PESO");
const porUnidad = productos.filter((p) => p.tipoCantidad !== "PESO");
log(`catalogo: ${productos.length} productos (${pesables.length} pesables)`);

// ---------------------------------------------------- ganancia con decimales
// 33.3% hace que casi ningun precio caiga justo en el centavo antes de
// redondear: es el peor caso para Dinero.precioConGanancia.
await api.ok("POST", "/configuracion/aplicar-ganancia-global", { porcentaje: 33.3 });

const elegidosUnidad = [];
while (elegidosUnidad.length < Math.min(60, porUnidad.length)) {
  const p = elegir(porUnidad);
  if (!elegidosUnidad.includes(p)) elegidosUnidad.push(p);
}
const elegidosPeso = pesables.slice(0, 15);
const elegidos = [...elegidosUnidad, ...elegidosPeso];

// ----------------------------------------------------------------- compras
const comprasHechas = [];
for (let i = 0; i < elegidos.length; i += 5) {
  const lote = elegidos.slice(i, i + 5);
  const pagada = i % 25 !== 10;
  const compra = await api.ok("POST", "/compras", {
    proveedor: `Proveedor ${i / 5 + 1}`,
    pagada,
    metodoPago: elegir(["EFECTIVO", "TRANSFERENCIA"]),
    detalles: lote.map((p) => ({
      productoId: p.id,
      cantidad: p.tipoCantidad === "PESO" ? Number((entero(20000, 60000) / 1000).toFixed(3)) : entero(60, 200),
      // Costo con centavos "feos": 123.45, 99.99, 1234.57...
      precioCosto: Number((entero(5000, 900000) / 100).toFixed(2)),
    })),
  });
  comprasHechas.push(compra);
}
log(`compras: ${comprasHechas.length}`);
for (const c of comprasHechas.filter((c) => !c.pagada)) {
  await api.ok("POST", `/compras/${c.id}/pagar`);
}

// Una compra extra, chica, que se anula enseguida (restaura el precio anterior).
const compraAnulable = await api.ok("POST", "/compras", {
  proveedor: "Compra mal tipeada",
  pagada: true,
  metodoPago: "EFECTIVO",
  detalles: [{ productoId: elegidosUnidad[0].id, cantidad: 3, precioCosto: 777.77 }],
});
await api.ok("DELETE", `/compras/${compraAnulable.id}`);

// Un precio cargado a mano con 3 decimales (el PATCH no redondea hoy): V17 lo
// tiene que dejar en el centavo mas cercano.
await api.ok("PATCH", `/productos/${elegidosUnidad[1].id}/stock-precio`, { precioVenta: 1234.567 });

// ------------------------------------------------------ descuentos y combos
const descuentosCreados = [];
for (const [p, pct] of [
  [elegidosUnidad[2], 12.5],
  [elegidosUnidad[3], 33],
  [elegidosUnidad[4], 7.75],
  [elegidosPeso[0], 15],
  [elegidosPeso[1], 33.33],
].filter(([p]) => p)) {
  const r = await api.pedir("POST", "/descuentos", { alcance: "PRODUCTO", productoId: p.id, porcentaje: pct, activo: true });
  if (r.status === 200) descuentosCreados.push(r.json);
  else log(`descuento sobre ${p.id} no se creo: ${r.status} ${r.texto.slice(0, 120)}`);
}
const categoriaConDescuento = elegidosUnidad.find((p, i) => i > 10 && p.categoriaId)?.categoriaId;
if (categoriaConDescuento) {
  const r = await api.pedir("POST", "/descuentos", {
    alcance: "CATEGORIA",
    categoriaId: categoriaConDescuento,
    porcentaje: 17,
    activo: true,
  });
  if (r.status === 200) descuentosCreados.push(r.json);
  else log(`descuento de categoria no se creo: ${r.status} ${r.texto.slice(0, 120)}`);
}
for (const [p, lleva, paga] of [
  [elegidosUnidad[5], 3, 2],
  [elegidosUnidad[6], 2, 1],
  [elegidosUnidad[7], 4, 3],
]) {
  const r = await api.pedir("POST", "/promociones", { productoId: p.id, cantidadLleva: lleva, cantidadPaga: paga, activo: true });
  if (r.status !== 200) log(`promo sobre ${p.id} no se creo: ${r.status} ${r.texto.slice(0, 120)}`);
}
log(`descuentos: ${descuentosCreados.length}`);

// ------------------------------------------------------------------ ventas
const ventas = [];
for (let i = 0; i < CUANTAS_VENTAS; i++) {
  const lineas = new Map();
  const cuantas = entero(1, 5);
  for (let j = 0; j < cuantas; j++) {
    // Sesgo hacia los que tienen descuento o combo, para que aparezcan seguido.
    const p = j === 0 && i % 3 === 0 ? elegir(elegidos.slice(0, 8)) : elegir(elegidos);
    const cantidad = p.tipoCantidad === "PESO" ? Number((entero(50, 1999) / 1000).toFixed(3)) : entero(1, 5);
    lineas.set(p.id, cantidad);
  }
  const detalles = [...lineas].map(([productoId, cantidad]) => ({ productoId, cantidad }));
  const clave = `paridad-${i}`;
  const cuerpo = { detalles, metodoPago: elegir(METODOS), cliente: i % 7 === 0 ? `Cliente ${i}` : null };
  const r = await api.pedir("POST", "/ventas", cuerpo, { "Idempotency-Key": clave });
  if (r.status !== 200) {
    log(`venta ${i} rechazada: ${r.status} ${r.texto.slice(0, 120)}`);
    continue;
  }
  ventas.push(r.json);
  // Doble clic: mismo cuerpo, misma clave -> tiene que devolver la misma venta.
  if (i % 25 === 0) {
    const otra = await api.ok("POST", "/ventas", cuerpo, { "Idempotency-Key": clave });
    if (otra.id !== r.json.id) throw new Error(`idempotencia rota en venta ${i}`);
  }
}
log(`ventas: ${ventas.length}`);

let anuladas = 0;
for (const v of ventas.filter((_, i) => i % 10 === 4)) {
  await api.ok("DELETE", `/ventas/${v.id}`);
  anuladas++;
}
log(`ventas anuladas: ${anuladas}`);

// Anular una compra vieja (puede no poder: si ya se vendio parte).
for (const c of comprasHechas.slice(-2)) {
  const r = await api.pedir("DELETE", `/compras/${c.id}`);
  log(`anular compra ${c.id}: ${r.status}`);
}

// ---------------------------------------------------------- caja manual
const gasto = await api.ok("POST", "/caja/movimientos", {
  tipo: "EGRESO", categoria: "GASTO", monto: 1234.56, metodoPago: "EFECTIVO", descripcion: "Luz",
});
await api.ok("POST", "/caja/movimientos", {
  tipo: "EGRESO", categoria: "GASTO", monto: 333.33, metodoPago: "TRANSFERENCIA", descripcion: "Internet",
});
await api.ok("POST", "/caja/movimientos", {
  tipo: "EGRESO", categoria: "RETIRO", monto: 500.1, metodoPago: "EFECTIVO", descripcion: "Retiro del dueño",
});
await api.ok("POST", "/caja/movimientos", {
  tipo: "INGRESO", categoria: "APORTE", monto: 1000.01, metodoPago: "EFECTIVO", descripcion: "Cambio",
});
await api.ok("POST", `/caja/movimientos/${gasto.id}/revertir`);

const empleados = await api.ok("GET", "/empleados");
if (empleados.length) {
  await api.ok("POST", `/empleados/${empleados[0].id}/pagar`, {
    monto: 45678.9, metodoPago: "EFECTIVO", descripcion: "Quincena",
  });
}

// ---------------------------------------------------------------- arqueos
let estado = await api.ok("GET", "/caja");
// Las compras salen en efectivo y pueden dejar el cajon en negativo: un
// aporte lo lleva a un monto contable antes de arquear.
if (estado.efectivoEnCaja < 10000) {
  await api.ok("POST", "/caja/movimientos", {
    tipo: "INGRESO", categoria: "APORTE", metodoPago: "EFECTIVO", descripcion: "Fondo para arqueo",
    monto: Number((10000 - estado.efectivoEnCaja + 0.37).toFixed(2)),
  });
  estado = await api.ok("GET", "/caja");
}
const sobrante = await api.ok("POST", "/caja/arqueo", {
  efectivoContado: Number((estado.efectivoEnCaja + 12.34).toFixed(2)),
});
estado = await api.ok("GET", "/caja");
const exacto = await api.ok("POST", "/caja/arqueo", { efectivoContado: estado.efectivoEnCaja });
estado = await api.ok("GET", "/caja");
const faltante = await api.ok("POST", "/caja/arqueo", {
  efectivoContado: Number((estado.efectivoEnCaja - 0.07).toFixed(2)),
});
log(`arqueos: sobrante ${sobrante.diferencia}, exacto ${exacto.diferencia}, faltante ${faltante.diferencia}`);
log("listo");
