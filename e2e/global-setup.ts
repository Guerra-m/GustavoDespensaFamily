// Global setup de la suite E2E.
//
// Qué hace, en orden:
//  1. Compila el frontend (Vite) -- el build cae dentro de
//     DespensaFamily/src/main/resources/static, que es lo que el backend
//     sirve como estático.
//  2. Arranca el backend (mvnw spring-boot:run) con una base H2 NUEVA en una
//     carpeta temporal (DESPENSA_HOME), puerto 18106, sin el modo de
//     escritorio (no intenta abrir un navegador ni poner ícono en la
//     bandeja) y con el JDK 17 del proyecto.
//  3. Espera a que el servidor conteste y a que DataInitializer escriba
//     credenciales-iniciales.txt (nace con una contraseña al azar y
//     debeCambiarPassword=true).
//  4. Loguea como admin con esa contraseña y la cambia por la constante
//     ADMIN_PASSWORD (fixtures/env.ts), para que cada spec pueda loguearse
//     sin volver a leer ningún archivo.
//
// Devuelve una función de teardown (patrón recomendado por Playwright) que
// mata el proceso del backend -- en Windows, con `taskkill /T` porque
// mvnw.cmd corre bajo cmd.exe y a su vez lanza un java.exe hijo: matar solo
// el PID de cmd.exe deja el java.exe (y el puerto 18106) colgado.
import { execSync, spawn, type ChildProcess } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir, homedir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { BASE_URL, PORT, ADMIN_USERNAME, ADMIN_PASSWORD } from "./fixtures/env.ts";

const HERE = fileURLToPath(new URL(".", import.meta.url));
const REPO_ROOT = join(HERE, "..");
const FRONT_DIR = join(REPO_ROOT, "FrontDespensaFamily");
const BACKEND_DIR = join(REPO_ROOT, "DespensaFamily");
const JAVA_HOME = join(homedir(), ".jdks", "ms-17.0.15");

function log(msg: string) {
  console.log(`[global-setup] ${msg}`);
}

function construirFrontend() {
  log("Compilando el frontend (Vite)...");
  if (!existsSync(join(FRONT_DIR, "node_modules"))) {
    log("node_modules del frontend no existe, corriendo npm install...");
    execSync("npm install", { cwd: FRONT_DIR, stdio: "inherit", shell: true });
  }
  execSync("npm run build", { cwd: FRONT_DIR, stdio: "inherit", shell: true });
}

async function esperarServidorListo(timeoutMs: number): Promise<void> {
  const limite = Date.now() + timeoutMs;
  let ultimoError: unknown = null;
  while (Date.now() < limite) {
    try {
      const res = await fetch(`${BASE_URL}/`);
      if (res.ok || res.status === 404) return;
    } catch (err) {
      ultimoError = err;
    }
    await new Promise(r => setTimeout(r, 500));
  }
  throw new Error(
    `El backend no respondió en ${timeoutMs}ms en ${BASE_URL}. Último error: ${String(ultimoError)}`
  );
}

function leerCredencialInicial(despensaHome: string, timeoutMs: number): Promise<string> {
  const archivo = join(despensaHome, "credenciales-iniciales.txt");
  const limite = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const intentar = () => {
      if (existsSync(archivo)) {
        const contenido = readFileSync(archivo, "utf-8");
        const match = contenido.match(/Contrasena:\s*(\S+)/);
        if (match) {
          resolve(match[1]);
          return;
        }
      }
      if (Date.now() > limite) {
        reject(new Error(`No apareció ${archivo} (o no tiene el formato esperado) a tiempo`));
        return;
      }
      setTimeout(intentar, 300);
    };
    intentar();
  });
}

/** Extrae el valor de la cookie "sesion" de un header Set-Cookie. */
function extraerCookieSesion(setCookie: string | null): string {
  if (!setCookie) throw new Error("El login no devolvió Set-Cookie");
  const match = setCookie.match(/sesion=([^;]+)/);
  if (!match) throw new Error(`No se encontró la cookie "sesion" en: ${setCookie}`);
  return `sesion=${match[1]}`;
}

async function cambiarPasswordInicialDeAdmin(passwordGenerada: string) {
  log("Cambiando la contraseña inicial del admin por la de la suite E2E...");

  const loginRes = await fetch(`${BASE_URL}/usuario/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: ADMIN_USERNAME, password: passwordGenerada }),
  });
  if (!loginRes.ok) {
    throw new Error(`Login inicial del admin falló: ${loginRes.status} ${await loginRes.text()}`);
  }
  const cookie = extraerCookieSesion(loginRes.headers.get("set-cookie"));

  const cambioRes = await fetch(`${BASE_URL}/usuario/1/password`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Cookie: cookie },
    body: JSON.stringify({ passwordActual: passwordGenerada, passwordNueva: ADMIN_PASSWORD }),
  });
  if (!cambioRes.ok) {
    throw new Error(`No se pudo cambiar la contraseña inicial: ${cambioRes.status} ${await cambioRes.text()}`);
  }
}

function matarProceso(proceso: ChildProcess) {
  if (proceso.pid == null) return;
  try {
    if (process.platform === "win32") {
      // /T mata todo el árbol (cmd.exe -> mvnw.cmd -> java.exe); sin esto el
      // java.exe queda vivo y el puerto 18106 sigue ocupado en la próxima
      // corrida.
      execSync(`taskkill /PID ${proceso.pid} /T /F`, { stdio: "ignore" });
    } else {
      process.kill(-proceso.pid, "SIGKILL");
    }
  } catch {
    // Ya puede estar muerto -- no es un error de la suite.
  }
}

export default async function globalSetup() {
  construirFrontend();

  const despensaHome = mkdtempSync(join(tmpdir(), "despensafamily-e2e-"));
  log(`DESPENSA_HOME temporal: ${despensaHome}`);

  const esWindows = process.platform === "win32";
  // Con solo "mvnw.cmd" (sin ruta), cmd.exe /c NO lo encuentra al spawnearlo
  // así (a diferencia de tipearlo a mano en una consola ya parada en esa
  // carpeta) -- hace falta la ruta absoluta.
  const mvnw = join(BACKEND_DIR, esWindows ? "mvnw.cmd" : "mvnw");
  const comando = esWindows ? "cmd.exe" : mvnw;
  const args = esWindows
    ? ["/c", mvnw, "spring-boot:run", "-q",
       `-Dspring-boot.run.arguments=--server.port=${PORT} --despensa.escritorio=false`]
    : ["spring-boot:run", "-q",
       `-Dspring-boot.run.arguments=--server.port=${PORT} --despensa.escritorio=false`];

  log("Arrancando el backend (mvnw spring-boot:run)...");
  const proceso = spawn(comando, args, {
    cwd: BACKEND_DIR,
    env: {
      ...process.env,
      DESPENSA_HOME: despensaHome,
      JAVA_HOME,
      PATH: `${join(JAVA_HOME, "bin")}${process.platform === "win32" ? ";" : ":"}${process.env.PATH}`,
    },
    stdio: ["ignore", "pipe", "pipe"],
  });

  let salida = "";
  proceso.stdout?.on("data", d => { salida += d.toString(); });
  proceso.stderr?.on("data", d => { salida += d.toString(); });

  proceso.on("exit", (code) => {
    if (code !== null && code !== 0) {
      log(`El proceso del backend terminó con código ${code}. Últimas líneas:\n${salida.slice(-4000)}`);
    }
  });

  try {
    await esperarServidorListo(120_000);
    const passwordGenerada = await leerCredencialInicial(despensaHome, 20_000);
    await cambiarPasswordInicialDeAdmin(passwordGenerada);
  } catch (err) {
    matarProceso(proceso);
    await new Promise(r => setTimeout(r, 1_000));
    rmSync(despensaHome, { recursive: true, force: true, maxRetries: 5, retryDelay: 500 });
    log(`Falló el arranque del backend. Salida acumulada:\n${salida.slice(-6000)}`);
    throw err;
  }

  log("Backend listo, admin con contraseña de la suite E2E. Corriendo tests...");

  return async () => {
    log("Deteniendo el backend...");
    matarProceso(proceso);
    // taskkill devuelve antes de que Windows termine de soltar los handles
    // de los archivos de la base H2 / el log -- un rmSync inmediato tira
    // EPERM/EBUSY. maxRetries + retryDelay (más el margen fijo) le dan
    // tiempo sin volverlo flaky.
    await new Promise(r => setTimeout(r, 1_000));
    rmSync(despensaHome, { recursive: true, force: true, maxRetries: 5, retryDelay: 500 });
  };
}
