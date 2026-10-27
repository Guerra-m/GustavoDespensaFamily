import { describe, expect, it } from "vitest";
import type { Producto } from "../models/Producto";
import {
  formatearCantidadConPaquete,
  formatearCantidadDeProducto,
  textoDesglosePaquete,
} from "./producto";

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

describe("textoDesglosePaquete", () => {
  it("si no llega a 1 paquete entero, devuelve null", () => {
    const prod = producto({ tipoCantidad: "UNIDAD", unidadesPorPaquete: 4 });
    expect(textoDesglosePaquete(3, prod)).toBeNull();
  });

  it("si son paquetes exactos, devuelve solo la cantidad de paquetes sin sueltas en 0", () => {
    const prod = producto({ tipoCantidad: "UNIDAD", unidadesPorPaquete: 4 });
    expect(textoDesglosePaquete(4, prod)).toBe("1 paq");
    expect(textoDesglosePaquete(8, prod)).toBe("2 paq");
  });

  it("si tiene paquetes y sueltas, devuelve ambos", () => {
    const prod = producto({ tipoCantidad: "UNIDAD", unidadesPorPaquete: 4 });
    expect(textoDesglosePaquete(6, prod)).toBe("1 paq + 2 u");
  });
});

describe("formatearCantidadConPaquete", () => {
  it("sin desglose de paquete (producto suelto), es igual a formatearCantidadDeProducto", () => {
    expect(formatearCantidadConPaquete(3, producto({ tipoCantidad: "UNIDAD" }))).toBe("3 u");
  });

  it("si no llega a 1 paquete entero, no muestra el desglose con 0 paq", () => {
    const prod = producto({ tipoCantidad: "UNIDAD", unidadesPorPaquete: 6 });
    expect(formatearCantidadConPaquete(3, prod)).toBe("3 u");
  });

  it("con paquetes exactos, no agrega '+ 0 u'", () => {
    const prod = producto({ tipoCantidad: "UNIDAD", unidadesPorPaquete: 6 });
    expect(formatearCantidadConPaquete(12, prod)).toBe("12 u (2 paq)");
  });

  it("con desglose de paquete mixto, agrega paquetes + sueltas", () => {
    const prod = producto({ tipoCantidad: "UNIDAD", unidadesPorPaquete: 6 });
    expect(formatearCantidadConPaquete(14, prod)).toBe("14 u (2 paq + 2 u)");
  });

  it("un producto pesable nunca desglosa en paquetes, aunque tenga unidadesPorPaquete", () => {
    const prod = producto({ tipoCantidad: "PESO", unidadesPorPaquete: 6 });
    expect(formatearCantidadConPaquete(1.5, prod)).toBe("1,5 kg");
  });
});
