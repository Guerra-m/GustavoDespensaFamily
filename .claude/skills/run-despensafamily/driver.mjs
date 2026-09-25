// Smoke driver for DespensaFamily (login -> home), used by the
// run-despensafamily skill. Requires the backend serving the built app
// (port 8080 by default) already running. See SKILL.md for how to start it.
//
// Usage:
//   node driver.mjs                        # full login smoke flow
//   node driver.mjs --url <path> --shot <name>   # just nav + screenshot

import { chromium } from "playwright";
import { mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const SHOTS = join(HERE, "screenshots");
mkdirSync(SHOTS, { recursive: true });

// Puerto 8080: el backend sirve el frontend YA COMPILADO desde el mismo
// origen (ver vite.config.ts, outDir -> resources/static), así que no hace
// falta -- y no conviene-- pegarle al dev server de Vite en 5173. En esta
// máquina el 5173 es OTRO proyecto (Fondo Familiar): apuntar ahí hace que
// este driver navegue a una app distinta en vez de fallar limpio.
// Configurable con DESPENSA_PORT por si el backend corre en otro puerto
// (por ejemplo, la base H2 temporal de la suite E2E en el 18106).
const FRONT = `http://localhost:${process.env.DESPENSA_PORT || 8080}`;
const USERNAME = process.env.DESPENSA_USER || "admin";
const PASSWORD = process.env.DESPENSA_PASS || "admin123";

function shot(page, name) {
  return page.screenshot({ path: join(SHOTS, `${name}.png`), fullPage: true });
}

async function main(browser) {
  const page = await browser.newPage();
  const consoleErrors = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") consoleErrors.push(msg.text());
  });
  page.on("pageerror", (err) => consoleErrors.push(String(err)));

  const args = process.argv.slice(2);
  if (args.includes("--url")) {
    const url = args[args.indexOf("--url") + 1];
    const name = args.includes("--shot") ? args[args.indexOf("--shot") + 1] : "page";
    await page.goto(`${FRONT}${url}`, { waitUntil: "load" });
    await shot(page, name);
    console.log(JSON.stringify({ ok: true, url, consoleErrors }, null, 2));
    return;
  }

  // 1. Login page renders
  await page.goto(`${FRONT}/src/pages/login/login.html`, { waitUntil: "load" });
  await page.waitForSelector("#loginForm");
  await shot(page, "login");

  // 2. Fill + submit real credentials (seeded by DataInitializer)
  await page.fill("#username", USERNAME);
  await page.fill("#password", PASSWORD);
  await Promise.all([
    page.waitForURL("**/home/home.html", { timeout: 15000 }),
    page.click("#btn-submit"),
  ]);

  // 3. Home renders with KPIs (proves the JWT round-trip + authenticated fetch worked)
  await page.waitForSelector("#kpi-ventas");
  await shot(page, "home");

  const token = await page.evaluate(() => localStorage.getItem("token"));

  const result = {
    ok: true,
    loginOk: !!token,
    finalUrl: page.url(),
    consoleErrors,
    screenshots: [join(SHOTS, "login.png"), join(SHOTS, "home.png")],
  };
  console.log(JSON.stringify(result, null, 2));

  if (!token || consoleErrors.length > 0) process.exitCode = 1;
}

const browser = await chromium.launch({ args: ["--no-sandbox"] });
try {
  await main(browser);
} catch (err) {
  console.error(JSON.stringify({ ok: false, error: String(err) }, null, 2));
  process.exitCode = 1;
} finally {
  await browser.close();
}
