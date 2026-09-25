import { describe, expect, it } from "vitest";
import { formatearMoneda, redondearCantidad, formatearCantidad } from "./format";

// Safety net antes de tocar la columna Precio del ticket (que usa
// formatearMoneda): no había ningún test de esta función.
describe("formatearMoneda", () => {
  it("formatea con separador de miles y dos decimales, estilo es-AR", () => {
    expect(formatearMoneda(2860)).toBe("$2.860,00");
  });

  it("redondea a dos decimales sin recortar el valor", () => {
    expect(formatearMoneda(1234.5)).toBe("$1.234,50");
  });

  it("un numero no finito no rompe el ticket: cae a $0,00", () => {
    expect(formatearMoneda(NaN)).toBe("$0,00");
    expect(formatearMoneda(Infinity)).toBe("$0,00");
  });

  it("montos grandes tambien llevan separador de miles en cada grupo", () => {
    expect(formatearMoneda(1234567.89)).toBe("$1.234.567,89");
  });
});

// DF-BUG-002: redondearCantidad usaba `cantidad.toFixed(3)`, que trunca en vez
// de redondear en varios casos de punto flotante (1,2345 -> "1.234" en lugar
// de "1.235"). El backend cotiza y cobra con BigDecimal HALF_UP -- si el
// carrito muestra una cantidad pesable distinta a la que el backend usó para
// facturar, el vendedor y el cliente ven un número que no coincide con lo
// cobrado. Casos elegidos porque .toFixed(3) los redondeaba mal:
// (1.2345).toFixed(3) === "1.234" (debería ser 1.235)
// (1.2375).toFixed(3) === "1.238" (coincide, pero se agrega para triangular).
describe("redondearCantidad", () => {
  it("redondea 1,2345 kg a 1,235 (HALF_UP), no 1,234 como toFixed(3)", () => {
    expect(redondearCantidad(1.2345)).toBe(1.235);
  });

  it("redondea 1,2375 kg a 1,238 (HALF_UP)", () => {
    expect(redondearCantidad(1.2375)).toBe(1.238);
  });

  it("no toca una cantidad que ya tiene 3 decimales exactos", () => {
    expect(redondearCantidad(3.462)).toBe(3.462);
  });

  it("un numero no finito se devuelve tal cual, no rompe el carrito", () => {
    expect(redondearCantidad(NaN)).toBeNaN();
  });
});

describe("formatearCantidad (con el mismo redondeo que redondearCantidad)", () => {
  it("1,2345 se muestra como 1,235, igual que lo que cobra el backend", () => {
    expect(formatearCantidad(1.2345)).toBe("1,235");
  });

  it("1,2375 se muestra como 1,238", () => {
    expect(formatearCantidad(1.2375)).toBe("1,238");
  });
});
