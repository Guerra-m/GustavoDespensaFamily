// Compara dos capturas de capturar.mjs numero por numero.
//
// Criterio de DF-DEBT-001: la unica diferencia aceptable es la de redondear un
// arrastre de double (<= 0,005 en un monto, <= 0,0005 en una cantidad). Todo
// lo demas -una clave que falta, un tipo que cambia (numero -> string), una
// diferencia mayor- es una FALLA. Cada diferencia aceptada se lista igual,
// para poder explicarla una por una.
//
// Uso: node comparar.mjs <antes.json> <despues.json>
// Sale con codigo 1 si hay alguna FALLA.

import { readFileSync } from "node:fs";

const [archivoAntes, archivoDespues] = process.argv.slice(2);
const antes = JSON.parse(readFileSync(archivoAntes, "utf8"));
const despues = JSON.parse(readFileSync(archivoDespues, "utf8"));

const IGNORAR = new Set(["capturadoEl"]);
const ES_CANTIDAD = /(stock|cantidad|cantidadVendida|fraccionMinima|umbralStockBajo)$/i;
const ES_CENTAVOS = /saldoPorMetodoEnCentavos/;

function aplanar(valor, ruta, salida) {
  if (valor !== null && typeof valor === "object") {
    for (const [k, v] of Object.entries(valor)) {
      if (!IGNORAR.has(k)) aplanar(v, ruta ? `${ruta}.${k}` : k, salida);
    }
  } else {
    salida.set(ruta, valor);
  }
  return salida;
}

const a = aplanar(antes, "", new Map());
const d = aplanar(despues, "", new Map());

let iguales = 0;
const fallas = [];
const aceptadas = [];

for (const [ruta, va] of a) {
  if (!d.has(ruta)) {
    fallas.push({ ruta, antes: va, despues: "(falta)", motivo: "clave ausente" });
    continue;
  }
  const vd = d.get(ruta);
  if (typeof va !== typeof vd) {
    fallas.push({ ruta, antes: va, despues: vd, motivo: `tipo ${typeof va} -> ${typeof vd}` });
    continue;
  }
  if (typeof va !== "number") {
    if (va === vd) iguales++;
    else fallas.push({ ruta, antes: va, despues: vd, motivo: "valor distinto" });
    continue;
  }
  if (va === vd) {
    iguales++;
    continue;
  }
  const dif = Math.abs(va - vd);
  const tolerancia = ES_CENTAVOS.test(ruta) ? 0 : ES_CANTIDAD.test(ruta) ? 0.0005 : 0.005;
  const fila = { ruta, antes: va, despues: vd, diferencia: Number(dif.toPrecision(6)) };
  if (dif <= tolerancia + 1e-9) aceptadas.push(fila);
  else fallas.push({ ...fila, motivo: `supera la tolerancia ${tolerancia}` });
}
for (const ruta of d.keys()) {
  if (!a.has(ruta)) fallas.push({ ruta, antes: "(falta)", despues: d.get(ruta), motivo: "clave nueva" });
}

console.log(`valores comparados: ${a.size}`);
console.log(`identicos:          ${iguales}`);
console.log(`diferencias <= tolerancia (a explicar): ${aceptadas.length}`);
for (const f of aceptadas) console.log(`  ~ ${f.ruta}: ${f.antes} -> ${f.despues} (dif ${f.diferencia})`);
console.log(`FALLAS: ${fallas.length}`);
for (const f of fallas.slice(0, 200)) console.log(`  X ${f.ruta}: ${f.antes} -> ${f.despues} (${f.motivo})`);
process.exit(fallas.length ? 1 : 0);
