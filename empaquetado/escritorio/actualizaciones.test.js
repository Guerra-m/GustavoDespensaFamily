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

// ------------------------------------------------------- vuelta atras
//
// DF-ELEC-001. Antes, si el jar nuevo no arrancaba, el local quedaba sin
// sistema hasta que alguien renombrara archivos a mano. Ahora: la base se copia
// antes de tocar nada, el jar nuevo queda "en prueba" hasta que responde, y si
// no responde se vuelve solo al jar, la version y la base de antes.

const {
  confirmarArranque,
  volverALaAnterior,
  hayVersionEnPrueba,
  versionesFallidas,
  debeOfrecerse,
  arrancar,
} = require("./actualizaciones");

const rutaDb = (e) => path.join(e.datos, "db", "despensa.mv.db");
const rutaPrueba = (e) => path.join(e.datos, "actualizaciones", "en-prueba.json");
const rutaInstaladaTxt = (e) => path.join(e.datos, "actualizaciones", "instalada.txt");
const rutaHistorial = (e) => path.join(e.datos, "actualizaciones", "historial.log");
const leer = (archivo) => fs.readFileSync(archivo, "utf8");

/** Un negocio con datos, en la 1.1.0, con la 1.2.0 aprobada y esperando. */
function negocioConDatos({ instalada = "1.1.0" } = {}) {
  const e = escenario("jar nuevo", sha("jar nuevo"));
  fs.mkdirSync(path.join(e.datos, "db"), { recursive: true });
  fs.writeFileSync(rutaDb(e), "ventas de la 1.1.0");
  if (instalada) fs.writeFileSync(rutaInstaladaTxt(e), instalada);
  return e;
}

/** Lo que hace la version nueva al arrancar: migra la base. */
function laVersionNuevaMigraLaBase(e) {
  fs.writeFileSync(rutaDb(e), "base migrada por la 1.2.0");
}

/** Simula un corte de luz a mitad de la vuelta atras. */
function marcarRevirtiendo(e) {
  const prueba = JSON.parse(leer(rutaPrueba(e)));
  fs.writeFileSync(rutaPrueba(e), JSON.stringify({ ...prueba, revirtiendo: true }));
}

test("antes de aplicar copia la base a backups y deja la version en prueba", async () => {
  const e = negocioConDatos();

  assert.equal(await aplicarPendiente(e.datos, e.servidor), "1.2.0");

  const respaldos = fs.readdirSync(path.join(e.datos, "backups"));
  assert.equal(respaldos.length, 1);
  assert.match(respaldos[0], /^antes-de-1\.2\.0-.*\.mv\.db$/);
  assert.equal(leer(path.join(e.datos, "backups", respaldos[0])), "ventas de la 1.1.0");
  assert.equal(hayVersionEnPrueba(e.datos), true);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("si no se puede respaldar la base, no se aplica nada", async () => {
  // Sin copia no hay vuelta atras posible: mejor seguir en la version vieja.
  const e = negocioConDatos();
  fs.writeFileSync(path.join(e.datos, "backups"), "un archivo donde deberia ir la carpeta");

  assert.equal(await aplicarPendiente(e.datos, e.servidor), null);
  assert.equal(leer(e.jar), "jar viejo");
  assert.equal(hayVersionEnPrueba(e.datos), false);
  // El jar descargado se conserva: se puede volver a aprobar.
  assert.equal(
    fs.existsSync(path.join(e.datos, "actualizaciones", "pendiente", "DespensaFamily.jar")), true);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("si el jar nuevo no arranca vuelve al jar, la version y la base de antes", async () => {
  const e = negocioConDatos();
  await aplicarPendiente(e.datos, e.servidor);
  laVersionNuevaMigraLaBase(e);

  const vuelta = await volverALaAnterior(e.datos, e.servidor);

  assert.deepEqual(vuelta, { fallida: "1.2.0", restaurada: "1.1.0" });
  assert.equal(leer(e.jar), "jar viejo");
  assert.equal(leer(rutaDb(e)), "ventas de la 1.1.0");
  assert.equal(leer(rutaInstaladaTxt(e)), "1.1.0");
  assert.equal(hayVersionEnPrueba(e.datos), false);
  assert.deepEqual(versionesFallidas(e.datos), ["1.2.0"]);
  // La base que dejo la version rota no se tira: queda para mirar que paso.
  const apartadas = fs.readdirSync(path.join(e.datos, "backups")).filter((n) => n.startsWith("fallida-"));
  assert.equal(apartadas.length, 1);
  assert.equal(leer(path.join(e.datos, "backups", apartadas[0])), "base migrada por la 1.2.0");
  // Y queda escrito, en la carpeta de datos, que paso.
  assert.match(leer(rutaHistorial(e)), /1\.2\.0 no arranco/);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("volver atras desde la version del instalador borra instalada.txt", async () => {
  // Primera actualizacion de todas: no habia instalada.txt, la version era la
  // de Electron. Volver es volver a no tenerlo.
  const e = negocioConDatos({ instalada: null });
  await aplicarPendiente(e.datos, e.servidor);

  const vuelta = await volverALaAnterior(e.datos, e.servidor);

  assert.deepEqual(vuelta, { fallida: "1.2.0", restaurada: null });
  assert.equal(fs.existsSync(rutaInstaladaTxt(e)), false);
  assert.equal(leer(e.jar), "jar viejo");

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("una vuelta atras cortada a la mitad se termina en el proximo arranque", async () => {
  // Corte de luz justo despues de devolver el jar y de apartar la base, antes
  // de copiar el respaldo: no hay jar .anterior y no hay base.
  const e = negocioConDatos();
  await aplicarPendiente(e.datos, e.servidor);
  laVersionNuevaMigraLaBase(e);
  marcarRevirtiendo(e);
  fs.renameSync(e.jar + ".anterior", e.jar);
  fs.renameSync(rutaDb(e), path.join(e.datos, "backups", "fallida-a-mano.mv.db"));

  const vuelta = await volverALaAnterior(e.datos, e.servidor);

  assert.deepEqual(vuelta, { fallida: "1.2.0", restaurada: "1.1.0" });
  assert.equal(leer(e.jar), "jar viejo");
  assert.equal(leer(rutaDb(e)), "ventas de la 1.1.0");
  assert.equal(hayVersionEnPrueba(e.datos), false);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("volver atras dos veces no rompe nada", async () => {
  const e = negocioConDatos();
  await aplicarPendiente(e.datos, e.servidor);
  await volverALaAnterior(e.datos, e.servidor);
  fs.writeFileSync(rutaDb(e), "ventas hechas despues, con la 1.1.0");

  // Sin version en prueba no hay nada que revertir: no se pisa la base.
  assert.equal(await volverALaAnterior(e.datos, e.servidor), null);
  assert.equal(leer(rutaDb(e)), "ventas hechas despues, con la 1.1.0");
  assert.equal(leer(e.jar), "jar viejo");

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("una version que no arranco no se vuelve a aplicar sola", async () => {
  const e = negocioConDatos();
  await aplicarPendiente(e.datos, e.servidor);
  await volverALaAnterior(e.datos, e.servidor);

  // Alguien la vuelve a aprobar con el mismo jar bajado.
  fs.writeFileSync(path.join(e.datos, "actualizaciones", "pendiente", "DespensaFamily.jar"), "jar nuevo");
  fs.writeFileSync(path.join(e.datos, "actualizaciones", "aplicar"), "1.2.0");

  assert.equal(await aplicarPendiente(e.datos, e.servidor), null);
  assert.equal(leer(e.jar), "jar viejo");

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("confirmar el arranque cierra la prueba y ya no hay vuelta atras automatica", async () => {
  const e = negocioConDatos();
  await aplicarPendiente(e.datos, e.servidor);

  assert.equal(await confirmarArranque(e.datos), "1.2.0");
  assert.equal(hayVersionEnPrueba(e.datos), false);
  assert.equal(await volverALaAnterior(e.datos, e.servidor), null);
  assert.equal(leer(e.jar), "jar nuevo");

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("una version que ya fallo no se ofrece; una posterior si", () => {
  assert.equal(debeOfrecerse("v1.2.0", "1.1.0", ["1.2.0"]), false);
  assert.equal(debeOfrecerse("1.2.0", "1.1.0", ["v1.2.0"]), false);
  assert.equal(debeOfrecerse("v1.2.1", "1.1.0", ["1.2.0"]), true);
  assert.equal(debeOfrecerse("1.1.0", "1.1.0", []), false);
});

// ----------------------------------------------------- arranque completo
//
// El recorrido que hace main.js, con el servidor reemplazado por un doble:
// "lanzar" anota que jar se levanto y "esperar" contesta si ese jar responde.

function servidorDeMentira(e, jarsQueArrancan) {
  const s = { lanzados: [], detenido: 0 };
  s.ops = {
    yaResponde: async () => false,
    lanzar: () => s.lanzados.push(leer(e.jar)),
    esperar: async () => jarsQueArrancan.includes(leer(e.jar)),
    detener: async () => {
      s.detenido++;
    },
  };
  return s;
}

test("arranque: si la version nueva responde, queda confirmada", async () => {
  const e = negocioConDatos();
  const s = servidorDeMentira(e, ["jar nuevo", "jar viejo"]);

  const r = await arrancar(e.datos, e.servidor, s.ops);

  assert.equal(r.resultado, "ok");
  assert.deepEqual(s.lanzados, ["jar nuevo"]);
  assert.equal(hayVersionEnPrueba(e.datos), false);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("arranque: si la version nueva no responde, se detiene y arranca la anterior", async () => {
  const e = negocioConDatos();
  const s = servidorDeMentira(e, ["jar viejo"]);

  const r = await arrancar(e.datos, e.servidor, s.ops);

  assert.equal(r.resultado, "revertida");
  assert.equal(r.fallida, "1.2.0");
  assert.equal(r.restaurada, "1.1.0");
  assert.deepEqual(s.lanzados, ["jar nuevo", "jar viejo"]);
  assert.equal(s.detenido, 1);
  assert.equal(leer(rutaDb(e)), "ventas de la 1.1.0");

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("arranque: si la anterior tampoco arranca, no sigue restaurando", async () => {
  const e = negocioConDatos();
  const s = servidorDeMentira(e, []);

  const r = await arrancar(e.datos, e.servidor, s.ops);
  assert.equal(r.resultado, "no-arranco");
  assert.deepEqual(s.lanzados, ["jar nuevo", "jar viejo"]);

  // Y el arranque siguiente es uno comun: un intento, sin tocar la base.
  fs.writeFileSync(rutaDb(e), "lo que haya quedado");
  const otra = servidorDeMentira(e, []);
  assert.equal((await arrancar(e.datos, e.servidor, otra.ops)).resultado, "no-arranco");
  assert.deepEqual(otra.lanzados, ["jar viejo"]);
  assert.equal(leer(rutaDb(e)), "lo que haya quedado");

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("arranque: sin nada en prueba, una caida no toca archivos", async () => {
  const e = negocioConDatos();
  fs.rmSync(path.join(e.datos, "actualizaciones", "aplicar"));
  const s = servidorDeMentira(e, []);

  const r = await arrancar(e.datos, e.servidor, s.ops);

  assert.equal(r.resultado, "no-arranco");
  assert.deepEqual(s.lanzados, ["jar viejo"]);
  assert.equal(s.detenido, 0);
  assert.equal(leer(rutaDb(e)), "ventas de la 1.1.0");

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("arranque: termina una vuelta atras cortada antes de levantar nada", async () => {
  const e = negocioConDatos();
  await aplicarPendiente(e.datos, e.servidor);
  laVersionNuevaMigraLaBase(e);
  marcarRevirtiendo(e);
  const s = servidorDeMentira(e, ["jar viejo", "jar nuevo"]);

  const r = await arrancar(e.datos, e.servidor, s.ops);

  assert.equal(r.resultado, "revertida");
  assert.deepEqual(s.lanzados, ["jar viejo"]);
  assert.equal(leer(rutaDb(e)), "ventas de la 1.1.0");

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("si falta la copia de la base, la vuelta atras no deja una base vacia", async () => {
  // Mover la base sin tener con que reemplazarla haria arrancar la version
  // anterior con una base nueva y vacia: peor que quedarse con la migrada.
  const e = negocioConDatos();
  await aplicarPendiente(e.datos, e.servidor);
  laVersionNuevaMigraLaBase(e);
  const { respaldo } = JSON.parse(leer(rutaPrueba(e)));
  fs.rmSync(respaldo);

  const vuelta = await volverALaAnterior(e.datos, e.servidor);

  assert.deepEqual(vuelta, { fallida: "1.2.0", restaurada: "1.1.0" });
  assert.equal(leer(rutaDb(e)), "base migrada por la 1.2.0");
  assert.equal(leer(e.jar), "jar viejo");
  assert.match(leer(rutaHistorial(e)), /No esta la copia/);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});

test("una version con barras no saca la copia de la carpeta de respaldos", async () => {
  const e = negocioConDatos();
  fs.writeFileSync(path.join(e.datos, "actualizaciones", "aplicar"), "..\..\afuera");

  await aplicarPendiente(e.datos, e.servidor);

  const respaldos = fs.readdirSync(path.join(e.datos, "backups"));
  assert.equal(respaldos.length, 1);
  assert.doesNotMatch(respaldos[0], /[\/]|\.\./);
  assert.deepEqual(fs.readdirSync(e.raiz).sort(), ["datos", "servidor"]);

  fs.rmSync(e.raiz, { recursive: true, force: true });
});
