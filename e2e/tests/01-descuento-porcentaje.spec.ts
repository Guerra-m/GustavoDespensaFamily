// Flujo 1: venta con descuento por % -- lo que muestra el carrito tiene que
// ser exactamente lo que cobra el ticket (ver VentaService.calcularLinea,
// que es la MISMA cuenta que /ventas/cotizar y que registrarVenta usan).
import { test, expect } from "@playwright/test";
import { login, irAVentas } from "../fixtures/ui.ts";
import { crearProducto, crearDescuentoPorProducto, nombreUnico } from "../fixtures/api.ts";
import { ADMIN_USERNAME, ADMIN_PASSWORD } from "../fixtures/env.ts";

test("venta con descuento por %: el total del carrito coincide con el del ticket", async ({ page, context }) => {
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

  const nombre = nombreUnico("Yerba Descuento");
  const producto = await crearProducto(context.request, {
    nombre,
    precioVenta: 1000,
    stock: 50,
  });
  await crearDescuentoPorProducto(context.request, producto.id, 20);

  await irAVentas(page);

  const card = page.locator(".producto-card", { hasText: nombre });
  await expect(card).toBeVisible();

  // 3 unidades a $1000 con 20% de descuento = $800 c/u = $2400 total.
  await card.click();
  await card.click();
  await card.click();

  const itemCarrito = page.locator(".carrito-item", { hasText: nombre });
  await expect(itemCarrito).toBeVisible();
  await expect(itemCarrito.locator(".carrito-descuento")).toHaveText("-20%");

  const totalEsperado = (800 * 3).toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  // El subtotal de la línea viene de la cotización real (debounce de 250ms),
  // no de una cuenta hecha en el test.
  await expect(itemCarrito.locator(".carrito-item-subtotal")).toHaveText(`$${totalEsperado}`, { timeout: 5_000 });
  await expect(page.locator("#total")).toHaveText(`$${totalEsperado}`, { timeout: 5_000 });

  const totalDelCarrito = await page.locator("#total").innerText();

  const [popup] = await Promise.all([
    page.waitForEvent("popup"),
    page.locator("#btn-confirmar").click(),
  ]);
  await popup.waitForLoadState();
  const textoTicket = await popup.locator("body").innerText();
  await popup.close();

  expect(textoTicket).toContain(`TOTAL: ${totalDelCarrito}`);
  await expect(page.locator("#total")).toHaveText("$0,00"); // carrito vacío tras confirmar
});
