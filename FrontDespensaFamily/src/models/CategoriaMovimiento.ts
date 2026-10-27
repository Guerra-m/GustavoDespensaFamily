/**
 * Por qué explica un movimiento de caja. Espeja el enum del backend
 * (model/CategoriaMovimiento.java) y tiene que tener EXACTAMENTE los mismos
 * valores: es un contrato, no una lista de conveniencia.
 *
 * Cuando faltó SUELDO, el mapa de etiquetas de la pantalla de Caja no
 * encontraba la suya y la columna Categoría imprimía "undefined" en cada pago
 * a un empleado. TypeScript no podía avisarlo, porque el que estaba incompleto
 * era este tipo: el compilador no puede exigir un valor que nadie declaró.
 *
 * Si se agrega una categoría en Java, hay que agregarla acá — y ahí sí el
 * compilador obliga a completar ETIQUETAS_CATEGORIA (está tipado como
 * Record<CategoriaMovimiento, string>). El test CategoriaMovimientoContratoTest
 * del backend falla si se agrega una allá y se olvida acá.
 */
export type CategoriaMovimiento =
  | "VENTA"
  | "COMPRA"
  | "GASTO"
  | "RETIRO"
  | "APORTE"
  | "AJUSTE"
  | "SUELDO";
