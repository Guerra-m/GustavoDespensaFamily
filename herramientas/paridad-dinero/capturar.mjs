// Red de paridad de DF-DEBT-001 (dinero exacto): saca por la API todos los
// numeros de plata y cantidades que ve el negocio y los guarda en un JSON.
//
// Se corre una vez con la version en double (antes de V17) y otra con la
// version en BigDecimal (despues de V17) sobre COPIAS de la misma base, y
// comparar.mjs los enfrenta numero por numero.
//
// Uso: node capturar.mjs <base_url> <usuario> <password> <salida.json>

import { writeFileSync } from "node:fs";
import { conectar } from "./api.mjs";

const [base, usuario, password, salida] = process.argv.slice(2);
if (!salida) {
  console.error("uso: node capturar.mjs <base_url> <usuario> <password> <salida.json>");
  process.exit(2);
}

const hoy = (() => {
  const d = new Date();
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
})();
const DESDE = "2000-01-01";

const api = await conectar(base, usuario, password);
const get = (ruta) => api.ok("GET", ruta);

const r = { capturadoEl: new Date().toISOString(), hoy };

// ---------------------------------------------------------------- productos
r.productos = {};
for (const p of await get("/productos")) {
  r.productos[p.id] = {
    nombre: p.nombre,
    precioVenta: p.precioVenta,
    precioCosto: p.precioCosto,
    porcentajeGanancia: p.porcentajeGanancia,
    stock: p.stock,
    fraccionMinima: p.fraccionMinima,
  };
}

// ------------------------------------------------------------------ ventas
r.ventas = {};
for (const v of await get("/ventas")) {
  const d = await get(`/ventas/${v.id}/detalle`);
  r.ventas[v.id] = {
    total: v.total,
    estado: v.estado,
    metodoPago: v.metodoPago,
    detalleTotal: d.total,
    lineas: d.detalles.map((l) => ({
      productoId: l.productoId,
      cantidad: l.cantidad,
      precioUnitario: l.precioUnitario,
      precioCosto: l.precioCosto,
      subtotal: l.subtotal,
      descuentoPorcentaje: l.descuentoPorcentaje ?? null,
      promoDescripcion: l.promoDescripcion ?? null,
    })),
  };
}

// ----------------------------------------------------------------- compras
r.compras = {};
for (const c of await get("/compras")) {
  const d = await get(`/compras/${c.id}/detalle`);
  r.compras[c.id] = {
    total: c.total,
    estado: c.estado,
    pagada: c.pagada,
    lineas: d.detalles.map((l) => ({
      productoId: l.productoId,
      cantidad: l.cantidad,
      precioCosto: l.precioCosto,
      subtotal: l.subtotal,
    })),
  };
}

// -------------------------------------------------------------------- caja
r.caja = await get("/caja");
const movimientos = await get(`/caja/movimientos?desde=${DESDE}&hasta=${hoy}`);
r.movimientos = {};
for (const m of movimientos) {
  r.movimientos[m.id] = { tipo: m.tipo, categoria: m.categoria, metodoPago: m.metodoPago, monto: m.monto };
}
// Saldo por metodo, calculado aca en centavos enteros (sin arrastre): es la
// referencia exacta contra la que se mide lo que devuelve /caja.
r.saldoPorMetodoEnCentavos = {};
for (const m of movimientos) {
  const signo = m.tipo === "INGRESO" ? 1 : -1;
  r.saldoPorMetodoEnCentavos[m.metodoPago] =
    (r.saldoPorMetodoEnCentavos[m.metodoPago] ?? 0) + signo * Math.round(m.monto * 100);
}

// ---------------------------------------------------------------- reportes
r.reportes = {
  resumenDia: await get("/reportes/resumen?periodo=dia"),
  resumenSemana: await get("/reportes/resumen?periodo=semana"),
  resumenMes: await get("/reportes/resumen?periodo=mes"),
  rentabilidadHoy: await get(`/reportes/rentabilidad?desde=${hoy}&hasta=${hoy}`),
  rentabilidadHistorica: await get(`/reportes/rentabilidad?desde=${DESDE}&hasta=${hoy}`),
  masVendidos: await get(`/reportes/productos-mas-vendidos?desde=${DESDE}&hasta=${hoy}&top=100`),
  ventasPorCategoria: await get(`/reportes/ventas-por-categoria?desde=${DESDE}&hasta=${hoy}`),
  gananciaPorFecha: await get(`/reportes/ganancia?desde=${DESDE}&hasta=${hoy}`),
  bajoStock: await get("/reportes/productos-bajo-stock"),
};
const movs = await get(`/reportes/movimientos?desde=${DESDE}&hasta=${hoy}`);
r.reportes.movimientos = movs.map((m) => ({
  tipo: m.tipo,
  productoId: m.productoId,
  cantidad: m.cantidad,
  precio: m.precio,
  subtotal: m.subtotal,
  operacionId: m.operacionId,
}));
// El orden de /reportes/movimientos es solo por fecha (empates en orden
// arbitrario): se ordena aca para poder comparar fila por fila.
r.reportes.movimientos.sort((a, b) =>
  `${a.tipo}|${a.operacionId}|${a.productoId}|${a.cantidad}`.localeCompare(
    `${b.tipo}|${b.operacionId}|${b.productoId}|${b.cantidad}`,
  ),
);

r.configuracion = await get("/configuracion");
r.descuentos = (await get("/descuentos")).map((d) => ({ id: d.id, porcentaje: d.porcentaje, activo: d.activo }));

writeFileSync(salida, JSON.stringify(r, null, 1));
console.log(
  `capturado: ${Object.keys(r.productos).length} productos, ${Object.keys(r.ventas).length} ventas, ` +
    `${Object.keys(r.compras).length} compras, ${movimientos.length} movimientos -> ${salida}`,
);
