import { describe, expect, it } from "vitest";
import type { Producto } from "../models/Producto";
import { filtrarProductos } from "./filtroProductos";

function producto(overrides: Partial<Producto>): Producto {
  return {
    id: 1,
    nombre: "Producto",
    precioVenta: 100,
    stock: 10,
    ...overrides,
  };
}

describe("filtrarProductos", () => {
  it("sin texto ni categoria devuelve todos los productos", () => {
    const productos = [
      producto({ id: 1, nombre: "Coca Cola" }),
      producto({ id: 2, nombre: "Fanta" }),
    ];

    const resultado = filtrarProductos(productos, { texto: "", categoriaId: null });

    expect(resultado).toHaveLength(2);
  });

  it("filtra por categoria activa, sin importar el texto", () => {
    const productos = [
      producto({ id: 1, nombre: "Coca Cola", categoriaId: 5 }),
      producto({ id: 2, nombre: "Fanta", categoriaId: 7 }),
      producto({ id: 3, nombre: "Sprite", categoriaId: 5 }),
    ];

    const resultado = filtrarProductos(productos, { texto: "", categoriaId: 5 });

    expect(resultado.map(p => p.id)).toEqual([1, 3]);
  });

  it("combina texto y categoria: las dos condiciones tienen que cumplirse", () => {
    const productos = [
      producto({ id: 1, nombre: "Coca Cola", categoriaId: 5 }),
      producto({ id: 2, nombre: "Coca Light", categoriaId: 7 }),
      producto({ id: 3, nombre: "Fanta", categoriaId: 5 }),
    ];

    const resultado = filtrarProductos(productos, { texto: "coca", categoriaId: 5 });

    expect(resultado.map(p => p.id)).toEqual([1]);
  });

  it("el texto tambien matchea por codigo de barras, sin importar mayusculas", () => {
    const productos = [
      producto({ id: 1, nombre: "Coca Cola", codigoBarras: "ABC123" }),
      producto({ id: 2, nombre: "Fanta", codigoBarras: "XYZ999" }),
    ];

    const resultado = filtrarProductos(productos, { texto: "abc", categoriaId: null });

    expect(resultado.map(p => p.id)).toEqual([1]);
  });

  it("sin coincidencias devuelve un array vacio", () => {
    const productos = [producto({ id: 1, nombre: "Coca Cola" })];

    const resultado = filtrarProductos(productos, { texto: "inexistente", categoriaId: null });

    expect(resultado).toEqual([]);
  });
});
