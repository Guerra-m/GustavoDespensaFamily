import { describe, expect, it } from "vitest";
import type { Producto } from "../models/Producto";
import { formatearCantidadConPaquete, formatearCantidadDeProducto } from "./producto";

function producto(overrides: Partial<Producto>): Producto {
  return {
    id: 1,
    nombre: "Producto",
    precioVenta: 100,
    stock: 10,
    ...overrides,
  };
}

describe("formatearCantidadDeProducto", () => {
  it("sin producto, devuelve solo el numero formateado", () => {
    expect(formatearCantidadDeProducto(3)).toBe("3");
  });

  it("con producto por unidad, agrega 'u'", () => {
    expect(formatearCantidadDeProducto(3, producto({ tipoCantidad: "UNIDAD" }))).toBe("3 u");
  });

  it("con producto pesable, agrega 'kg'", () => {
    expect(formatearCantidadDeProducto(1.5, producto({ tipoCantidad: "PESO" }))).toBe("1,5 kg");
  });
});

describe("formatearCantidadConPaquete", () => {
  it("sin desglose de paquete (producto suelto), es igual a formatearCantidadDeProducto", () => {
    expect(formatearCantidadConPaquete(3, producto({ tipoCantidad: "UNIDAD" }))).toBe("3 u");
  });

  it("con desglose de paquete, agrega paquetes + sueltas", () => {
    const prod = producto({ tipoCantidad: "UNIDAD", unidadesPorPaquete: 6 });
    expect(formatearCantidadConPaquete(14, prod)).toBe("14 u (2 paq + 2 u)");
  });

  it("un producto pesable nunca desglosa en paquetes, aunque tenga unidadesPorPaquete", () => {
    const prod = producto({ tipoCantidad: "PESO", unidadesPorPaquete: 6 });
    expect(formatearCantidadConPaquete(1.5, prod)).toBe("1,5 kg");
  });
});
