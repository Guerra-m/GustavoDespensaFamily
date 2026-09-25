import { describe, expect, it } from "vitest";
import { formatearMoneda } from "./format";

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
