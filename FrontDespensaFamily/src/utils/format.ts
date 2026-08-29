/**
 * Formato de cantidades (stock, kilos, unidades).
 *
 * El backend guarda las cantidades como número de punto flotante, y restar
 * 1,538 de 5 no da 3,462 sino 3.4619999999999997. El backend ya redondea al
 * gramo al escribir y al leer; acá se redondea igual por dos motivos: los
 * productos que quedaron sucios antes de ese arreglo, y las cuentas que hace
 * la propia pantalla sin pasar por el servidor (por ejemplo descontar el
 * carrito del stock que ya está en memoria).
 *
 * Tres decimales = el gramo, que es la fracción mínima de un producto pesable.
 */
const DECIMALES = 3;

export function redondearCantidad(cantidad: number): number {
  if (!Number.isFinite(cantidad)) return cantidad;
  return Number(cantidad.toFixed(DECIMALES));
}

/**
 * Sin ceros al final: 3.462 → "3.462", 2 → "2" y no "2.000". El cartel de un
 * producto por unidad tiene que decir "2 u", no "2.000 u".
 */
export function formatearCantidad(cantidad: number): string {
  if (!Number.isFinite(cantidad)) return "0";
  return String(redondearCantidad(cantidad));
}
