// Pruebas del launcher. Se corren con "npm test" (node --test), sin Electron:
// actualizaciones.js solo usa modulos de Node, justamente para poder probarlo.
//
// Lo que se prueba aca es la logica que esta DUPLICADA con el servidor
// (Version.java): comparar versiones. Es duplicacion deliberada —los dos
// procesos necesitan decidir lo mismo y no comparten codigo— y por eso cada
// lado tiene su prueba. Si un dia una de las dos se corrige y la otra no, esto
// lo dice.

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const { esPosterior, versionInstalada, hayActualizacionAprobada } = require("./actualizaciones");

test("una version mayor es posterior", () => {
  assert.equal(esPosterior("1.2.0", "1.1.0"), true);
  assert.equal(esPosterior("2.0.0", "1.9.9"), true);
});

test("compara por numero y no por texto", () => {
  // Como texto "1.10.0" < "1.9.0". Es el error que esta funcion existe para
  // evitar, y el mismo caso que cubre VersionTest del lado de Java.
  assert.equal(esPosterior("1.10.0", "1.9.0"), true);
  assert.equal(esPosterior("1.9.0", "1.10.0"), false);
});

test("la misma version no dispara actualizacion", () => {
  assert.equal(esPosterior("1.1.0", "1.1.0"), false);
  assert.equal(esPosterior("1.2", "1.2.0"), false);
});

test("ignora la v de las etiquetas de GitHub", () => {
  // Las releases se etiquetan "v1.2.0" pero el pom dice "1.2.0".
  assert.equal(esPosterior("v1.2.0", "1.1.0"), true);
  assert.equal(esPosterior("v1.1.0", "1.1.0"), false);
});

test("una version ilegible no ofrece nada", () => {
  assert.equal(esPosterior("no-es-una-version", "1.0.0"), false);
  assert.equal(esPosterior("2.0.0", undefined), false);
  assert.equal(esPosterior("", "1.0.0"), false);
});

test("instalada.txt manda sobre la version de Electron", () => {
  // Despues de la primera actualizacion de solo-jar las dos dejan de coincidir:
  // el jar pasa a 1.2.0 y el Electron sigue en el 1.1.0 que puso el setup.exe.
  // Si se mirara la de Electron, la 1.2.0 se ofreceria para siempre.
  const datos = fs.mkdtempSync(path.join(os.tmpdir(), "despensa-"));
  fs.mkdirSync(path.join(datos, "actualizaciones"), { recursive: true });

  assert.equal(versionInstalada(datos, "1.1.0"), "1.1.0");

  fs.writeFileSync(path.join(datos, "actualizaciones", "instalada.txt"), "1.2.0");
  assert.equal(versionInstalada(datos, "1.1.0"), "1.2.0");

  fs.rmSync(datos, { recursive: true, force: true });
});

test("sin senial no hay nada aprobado", () => {
  const datos = fs.mkdtempSync(path.join(os.tmpdir(), "despensa-"));
  assert.equal(hayActualizacionAprobada(datos), false);

  fs.mkdirSync(path.join(datos, "actualizaciones"), { recursive: true });
  fs.writeFileSync(path.join(datos, "actualizaciones", "aplicar"), "1.2.0");
  assert.equal(hayActualizacionAprobada(datos), true);

  fs.rmSync(datos, { recursive: true, force: true });
});

// ---------------------------------------------------------------- aplicar

const crypto = require("node:crypto");
const { aplicarPendiente } = require("./actualizaciones");

function escenario(contenidoNuevo, hashEnEstado) {
  const raiz = fs.mkdtempSync(path.join(os.tmpdir(), "despensa-"));
  const datos = path.join(raiz, "datos");
  const servidor = path.join(raiz, "servidor");

  fs.mkdirSync(path.join(datos, "actualizaciones", "pendiente"), { recursive: true });
  fs.mkdirSync(path.join(servidor, "app"), { recursive: true });

  fs.writeFileSync(path.join(servidor, "app", "DespensaFamily.jar"), "jar viejo");
  fs.writeFileSync(
    path.join(datos, "actualizaciones", "pendiente", "DespensaFamily.jar"), contenidoNuevo);
  fs.writeFileSync(path.join(datos, "actualizaciones", "aplicar"), "1.2.0");
  fs.writeFileSync(
    path.join(datos, "actualizaciones", "estado.json"),
    JSON.stringify({ estado: "lista", version: "1.2.0", sha256: hashEnEstado }));

  return { raiz, datos, servidor, jar: path.join(servidor, "app", "DespensaFamily.jar") };
}

const sha = (t) => crypto.createHash("sha256").update(t).digest("hex");

test("reemplaza el jar y guarda el anterior", async () => {
  const e = escenario("jar nuevo", sha("jar nuevo"));

  const aplicada = await aplicarPendiente(e.datos, e.servidor);

  assert.equal(aplicada, "1.2.0");
  assert.equal(fs.readFileSync(e.jar, "utf8"), "jar nuevo");
  // El jar viejo se conserva: si la version nueva resulta estar rota, hay algo
  // a lo que volver sin tener que bajar nada.
  assert.equal(fs.readFileSync(e.jar + ".anterior", "utf8"), "jar viejo");
  assert.equal(
    fs.readFileSync(path.join(e.datos, "actualizaciones", "instalada.txt"), "utf8"), "1.2.0");
  // La senial se consume: no se reintenta en cada arranque.
  assert.equal(fs.existsSync(path.join(e.datos, "actualizaciones", "aplicar")), false);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("un jar que no coincide con su hash no se instala", async () => {
  // El archivo estuvo en disco entre la descarga y el reinicio. Si en el medio
  // cambio —disco con errores, o alguien con acceso a la maquina—, no entra.
  const e = escenario("jar cambiado", sha("jar nuevo"));

  const aplicada = await aplicarPendiente(e.datos, e.servidor);

  assert.equal(aplicada, null);
  assert.equal(fs.readFileSync(e.jar, "utf8"), "jar viejo");
  assert.equal(
    fs.existsSync(path.join(e.datos, "actualizaciones", "pendiente", "DespensaFamily.jar")),
    false);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("sin senial no toca nada", async () => {
  const e = escenario("jar nuevo", sha("jar nuevo"));
  fs.rmSync(path.join(e.datos, "actualizaciones", "aplicar"));

  assert.equal(await aplicarPendiente(e.datos, e.servidor), null);
  assert.equal(fs.readFileSync(e.jar, "utf8"), "jar viejo");

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("senial sin jar descargado no rompe el arranque", async () => {
  const e = escenario("jar nuevo", sha("jar nuevo"));
  fs.rmSync(path.join(e.datos, "actualizaciones", "pendiente", "DespensaFamily.jar"));

  assert.equal(await aplicarPendiente(e.datos, e.servidor), null);
  assert.equal(fs.readFileSync(e.jar, "utf8"), "jar viejo");
  // Y la senial se limpia igual, para no reintentar para siempre.
  assert.equal(fs.existsSync(path.join(e.datos, "actualizaciones", "aplicar")), false);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});
