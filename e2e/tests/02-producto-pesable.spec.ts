// Flujo 2: venta de un pesable -- cantidad con 3 decimales, redondeo al gramo
// (ver util Cantidades.java, HALF_UP a 3 decimales) y total correcto.
import { test, expect } from "@playwright/test";
import { login, irAVentas } from "../fixtures/ui.ts";
import { crearProducto, nombreUnico } from "../fixtures/api.ts";
import { ADMIN_USERNAME, ADMIN_PASSWORD } from "../fixtures/env.ts";

test("venta de un pesable: redondeo al gramo y total correcto", async ({ page, context }) => {
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

  const nombre = nombreUnico("Jamon Pesable");
  await crearProducto(context.request, {
    nombre,
    precioVenta: 2500, // por kg
    stock: 10,
    tipoCantidad: "PESO",
    unidadMedida: "kg",
  });

  await irAVentas(page);

  const card = page.locator(".producto-card", { hasText: nombre });
  await expect(card).toBeVisible();
  await card.click(); // por peso: clickear selecciona, no agrega directo

  const inputCantidad = page.locator("#input-cantidad");
  await expect(inputCantidad).toHaveAttribute("placeholder", "Ingresá el peso en kg");

  // 4 decimales a propósito: la fracción mínima real es el gramo (3
  // decimales) -- esto tiene que REDONDEAR 1,2375 -> 1,238 kg (HALF_UP),
  // nunca truncar a 1,237.
  await inputCantidad.fill("1.2375");
  await page.getByRole("button", { name: "Agregar al Carrito" }).click();

  const itemCarrito = page.locator(".carrito-item", { hasText: nombre });
  await expect(itemCarrito).toBeVisible();
  await expect(itemCarrito.locator(".carrito-cantidad-texto")).toHaveText("1,238 kg");

  // 2500 * 1,238 = 3095,00 (redondeo al centavo, ver util Dinero.java).
  const totalEsperado = (2500 * 1.238).toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  await expect(itemCarrito.locator(".carrito-item-subtotal")).toHaveText(`$${totalEsperado}`, { timeout: 5_000 });
  await expect(page.locator("#total")).toHaveText(`$${totalEsperado}`, { timeout: 5_000 });
});

// BUG REAL encontrado escribiendo este flujo (no se arregla, según lo
// pedido -- se deja documentado y marcado como fallo esperado).
//
// El carrito muestra la cantidad pesable con `formatearCantidadConPaquete`
// (ventas.ts/renderCarrito) sobre `item.cantidad`, que es el número CRUDO
// que tipeó quien vende -- nunca el valor ya redondeado al gramo que
// devuelve el backend en la cotización. `formatearCantidad` (utils/format.ts)
// redondea con `Number(cantidad.toFixed(3))`, y `toFixed` de JavaScript no es
// un redondeo decimal exacto: para 1,2345 puntualmente da "1.234" en vez de
// "1.235", por el error de representación binaria de ese número (no es un
// caso raro armado a mano -- 1,2345 kg es un peso perfectamente real de
// bascula). El backend, en cambio, sí redondea bien: usa BigDecimal.HALF_UP
// sobre el valor exacto (ver util Cantidades.java) tanto para cobrar como
// para descontar stock.
//
// Resultado: el número que el vendedor ve en el carrito ("1,234 kg") no es
// el que realmente se cobra y se descuenta del stock ("1,235 kg") -- una
// diferencia de un gramo que no se nota en un producto suelto, pero sí
// contradice lo que la pantalla dice estar cobrando.
test.fail(
  "BUG conocido: la cantidad pesable mostrada en el carrito no siempre coincide con la redondeada que se cobra (formatearCantidad usa Number.toFixed, no un redondeo decimal exacto)",
  async ({ page, context }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    const nombre = nombreUnico("Jamon Pesable Bug");
    await crearProducto(context.request, {
      nombre,
      precioVenta: 2500,
      stock: 10,
      tipoCantidad: "PESO",
      unidadMedida: "kg",
    });

    await irAVentas(page);
    const card = page.locator(".producto-card", { hasText: nombre });
    await card.click();
    await page.locator("#input-cantidad").fill("1.2345");
    await page.getByRole("button", { name: "Agregar al Carrito" }).click();

    const itemCarrito = page.locator(".carrito-item", { hasText: nombre });
    // Esto es lo que DEBERÍA mostrar (mismo redondeo HALF_UP que aplica el
    // backend al cobrar) -- hoy falla porque muestra "1,234 kg".
    await expect(itemCarrito.locator(".carrito-cantidad-texto")).toHaveText("1,235 kg");
  }
);
