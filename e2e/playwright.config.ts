import { defineConfig, devices } from "@playwright/test";
import { BASE_URL } from "./fixtures/env.ts";

export default defineConfig({
  testDir: "./tests",
  // Un único backend (con su única base H2 temporal) para toda la corrida:
  // en serie, para que la venta doble-clic, el stock que descuentan las
  // otras ventas y el conteo de ventas por API no se pisen entre specs
  // corriendo en paralelo.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  timeout: 30_000,
  globalSetup: "./global-setup.ts",
  use: {
    baseURL: BASE_URL,
    headless: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
});
