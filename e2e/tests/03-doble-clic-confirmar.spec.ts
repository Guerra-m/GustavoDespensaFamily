// Flujo 3: doble clic en "Confirmar Venta" -> una sola venta.
//
// DF-BUG-003 (ver ventas.ts/confirmarVenta): `ventaEnCurso` + deshabilitar el
// botón cortan cualquier segunda llamada mientras la primera sigue en vuelo.
// Se dispara el doble clic con dos `.click()` de DOM en el mismo tick (en vez
// de `page.dblclick`, que Playwright frenaría solo por actionability apenas
// el botón queda disabled) para reproducir de verdad la carrera de un clic
// doble real.
import { test, expect } from "@playwright/test";
import { login, irAVentas } from "../fixtures/ui.ts";
import { crearProducto, nombreUnico, contarVentas } from "../fixtures/api.ts";
import { ADMIN_USERNAME, ADMIN_PASSWORD } from "../fixtures/env.ts";

test("doble clic en Confirmar Venta registra una sola venta", async ({ page, context }) => {
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

  const nombre = nombreUnico("Fideos Doble Clic");
  await crearProducto(context.request, { nombre, precioVenta: 500, stock: 20 });

  const ventasAntes = await contarVentas(context.request);

  await irAVentas(page);

  const card = page.locator(".producto-card", { hasText: nombre });
  await card.click();
  await expect(page.locator(".carrito-item", { hasText: nombre })).toBeVisible();
  await expect(page.locator("#total")).not.toHaveText("Calculando…", { timeout: 5_000 });

  const [popup] = await Promise.all([
    page.waitForEvent("popup"),
    page.evaluate(() => {
      const btn = document.getElementById("btn-confirmar") as HTMLButtonElement;
      btn.click();
      btn.click(); // el "doble clic": mismo tick, antes de que nada async corra
    }),
  ]);
  await popup.waitForLoadState();
  await popup.close();

  // No debería aparecer una segunda ventana de ticket -- si apareciera, sería
  // la prueba de una segunda venta.
  let huboSegundoPopup = false;
  const onPopup = () => { huboSegundoPopup = true; };
  page.on("popup", onPopup);

  await expect(page.locator("#total")).toHaveText("$0,00", { timeout: 5_000 }); // carrito se vació una vez
  await page.waitForTimeout(1_000); // margen para que un segundo pedido (si lo hubiera) llegue al backend
  page.off("popup", onPopup);
  expect(huboSegundoPopup).toBe(false);

  const ventasDespues = await contarVentas(context.request);
  expect(ventasDespues - ventasAntes).toBe(1);
});
