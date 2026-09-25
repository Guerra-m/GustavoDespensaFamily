// Helpers de UI compartidos por los specs.
import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { BASE_URL } from "./env.ts";

/** Login real por la pantalla (no por API): deja la cookie de sesión puesta
 * en el `page`/`context`, que es lo que después usan los pedidos de
 * fixtures/api.ts vía `context.request`. */
export async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto(`${BASE_URL}/src/pages/login/login.html`);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#btn-submit").click();
  // No navega a una URL fija con éxito siempre: si debeCambiarPassword es
  // true, va a perfil.html?cambiar=1 en vez de home.html. Los specs que usan
  // este helper ya cambiaron esa contraseña en global-setup (admin) o la
  // crean sin ese flag (empleado), así que acá alcanza con esperar salir del
  // login.
  await page.waitForURL(url => !url.pathname.endsWith("/login.html"), { timeout: 15_000 });
}

export async function irAVentas(page: Page): Promise<void> {
  await page.goto(`${BASE_URL}/src/pages/ventas/ventas.html`);
  await expect(page.locator("#productos-grid")).toBeVisible();
}
