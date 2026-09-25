// Actualizaciones de DespensaFamily.
//
// El launcher es quien se ocupa de esto y no el servidor, a proposito. El
// servidor es el proceso que atiende la caja y esta expuesto a la red del
// local: no tiene por que aprender a bajar archivos de internet. El launcher ya
// es duenio de los archivos del programa y de su ciclo de vida, que es
// exactamente lo que hace falta para reemplazar un jar y reiniciar.
//
// Que se actualiza: SOLO el jar. Vite compila el frontend adentro del jar, asi
// que un jar nuevo es la aplicacion entera nueva —backend y pantallas—. Los
// 66 MB del jar contra los 188 MB del instalador, y sin asistente de
// instalacion, sin UAC y sin el cartel de "editor desconocido" de SmartScreen.
// Cuando cambie Electron o el motor de Java si hace falta el setup.exe, y eso
// se sigue haciendo a mano.
//
// El reparto con el servidor son dos archivos en la carpeta de datos:
//
//   actualizaciones/estado.json    lo escribe este modulo; el servidor lo lee
//                                  para mostrar el aviso en la pantalla
//   actualizaciones/aplicar        lo escribe el servidor cuando el duenio
//                                  aprueba; este modulo lo lee al arrancar
//   actualizaciones/pendiente/     el jar ya bajado y verificado
//
// INTEGRIDAD (CRI-01 de la biblioteca de seguridad): el jar se verifica por
// SHA-256 contra el .sha256 publicado en la misma release, y recien despues se
// mueve a su lugar. Eso cubre integridad de la descarga —corte de red, proxy
// que corrompe, archivo truncado—. Lo que NO cubre es autoria: si alguien se
// queda con la cuenta de GitHub, publica un jar y su hash, y los dos verifican
// bien. El ancla de confianza real es HTTPS mas la cuenta del repositorio. Para
// cerrar eso hace falta una firma (Ed25519) con la clave publica adentro del
// launcher; el formato de aca esta pensado para poder agregarla despues sin
// romper nada.

const crypto = require("node:crypto");
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const https = require("node:https");
const path = require("node:path");

const REPOSITORIO = "Guerra-m/GustavoDespensaFamily";
const NOMBRE_DEL_JAR = "DespensaFamily.jar";
const NOMBRE_DEL_HASH = NOMBRE_DEL_JAR + ".sha256";

// Solo estos hosts. GitHub sirve la API desde uno y los archivos desde otro, y
// contesta con un redirect del primero al segundo: si no se acota a donde puede
// saltar, un redirect manipulado manda la descarga a cualquier lado.
const HOSTS_PERMITIDOS = ["api.github.com", "objects.githubusercontent.com", "github.com",
  "release-assets.githubusercontent.com"];
const MAXIMO_REDIRECTS = 5;

// Un jar ronda los 66 MB. El tope no es para el caso normal sino para el malo:
// sin limite, una respuesta interminable llena el disco de la PC del negocio.
const TAMANIO_MAXIMO = 250 * 1024 * 1024;
const TIMEOUT_MS = 30000;

const ESTADOS = {
  alDia: "al-dia",
  disponible: "disponible",
  descargando: "descargando",
  lista: "lista",
  error: "error",
};

// ---------------------------------------------------------------- rutas

function carpeta(datos) {
  return path.join(datos, "actualizaciones");
}

function rutaEstado(datos) {
  return path.join(carpeta(datos), "estado.json");
}

function rutaSenial(datos) {
  return path.join(carpeta(datos), "aplicar");
}

function rutaPendiente(datos) {
  return path.join(carpeta(datos), "pendiente", NOMBRE_DEL_JAR);
}

/** Deja anotada la version del jar que esta puesto ahora mismo. */
function rutaInstalada(datos) {
  return path.join(carpeta(datos), "instalada.txt");
}

/**
 * Existe mientras un jar recien aplicado no demostro que arranca. Anota a que
 * volver si no lo hace: la version previa y la copia de la base.
 */
function rutaEnPrueba(datos) {
  return path.join(carpeta(datos), "en-prueba.json");
}

/** Versiones que ya se aplicaron y no arrancaron: no se reintentan solas. */
function rutaFallidas(datos) {
  return path.join(carpeta(datos), "fallidas.txt");
}

/**
 * Lo que hizo el actualizador, con fecha. Va a la carpeta de datos porque el
 * stdout del launcher empaquetado no queda en ningun lado, y cuando algo sale
 * mal en el local esto es lo primero que hay que pedir.
 */
function rutaHistorial(datos) {
  return path.join(carpeta(datos), "historial.log");
}

/** La base H2: jdbc:h2:file:${despensa.db}/despensa */
function rutaBase(datos) {
  return path.join(datos, "db", "despensa.mv.db");
}

function carpetaRespaldos(datos) {
  return path.join(datos, "backups");
}

function rutaJar(carpetaDelServidor) {
  return path.join(carpetaDelServidor, "app", NOMBRE_DEL_JAR);
}

// ---------------------------------------------------------------- versiones

/**
 * Compara por numero y no por texto: como texto, "1.10.0" es menor que "1.9.0".
 * Es el espejo de Version.java del lado del servidor, y por el mismo motivo:
 * una version ilegible no es posterior a nada, asi que no dispara ningun aviso.
 */
function partesDeVersion(v) {
  if (!v) return null;
  const limpia = String(v).trim().replace(/^[vV]/, "").replace(/[-+].*$/, "");
  if (!limpia) return null;
  const trozos = limpia.split(".").map((t) => Number.parseInt(t, 10));
  return trozos.some((n) => Number.isNaN(n)) ? null : trozos;
}

/** -1, 0 o 1; null si alguna de las dos es ilegible. */
function compararVersiones(x, y) {
  const a = partesDeVersion(x);
  const b = partesDeVersion(y);
  if (!a || !b) return null;
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const p = a[i] ?? 0;
    const q = b[i] ?? 0;
    if (p !== q) return p > q ? 1 : -1;
  }
  return 0;
}

function esPosterior(candidata, actual) {
  return compararVersiones(candidata, actual) === 1;
}

/**
 * Si una release se ofrece: tiene que ser posterior a la puesta y no haber
 * fallado ya en esta PC. Una version que no arranco se descarta hasta que salga
 * una posterior; reintentarla sola cada seis horas seria dejar el local sin
 * sistema una y otra vez.
 */
function debeOfrecerse(candidata, actual, fallidas) {
  if (!esPosterior(candidata, actual)) return false;
  return !fallidas.some((f) => compararVersiones(f, candidata) === 0);
}

/**
 * Que version esta corriendo. instalada.txt manda sobre la version de Electron
 * porque despues de la primera actualizacion de solo-jar las dos dejan de
 * coincidir: el jar pasa a 1.2.0 y el Electron sigue siendo el 1.1.0 que
 * instalo el setup.exe.
 */
function versionInstalada(datos, versionDeElectron) {
  try {
    const anotada = fs.readFileSync(rutaInstalada(datos), "utf8").trim();
    if (anotada) return anotada;
  } catch {
    // Todavia no se actualizo nunca: vale la que trajo el instalador.
  }
  return versionDeElectron;
}

// ---------------------------------------------------------------- estado

async function escribirEstado(datos, contenido) {
  await fsp.mkdir(carpeta(datos), { recursive: true });
  await fsp.writeFile(rutaEstado(datos), JSON.stringify(contenido, null, 2), "utf8");
}

// ---------------------------------------------------------------- descarga

function pedir(url, cabeceras, redireccionesRestantes = MAXIMO_REDIRECTS) {
  return new Promise((resolver, rechazar) => {
    let destino;
    try {
      destino = new URL(url);
    } catch {
      rechazar(new Error("URL invalida"));
      return;
    }

    if (destino.protocol !== "https:" || !HOSTS_PERMITIDOS.includes(destino.hostname)) {
      rechazar(new Error("Destino no permitido: " + destino.hostname));
      return;
    }

    const pedido = https.get(
      destino,
      { headers: { "User-Agent": "DespensaFamily", ...cabeceras }, timeout: TIMEOUT_MS },
      (respuesta) => {
        const codigo = respuesta.statusCode;
        if (codigo >= 300 && codigo < 400 && respuesta.headers.location) {
          respuesta.resume();
          if (redireccionesRestantes <= 0) {
            rechazar(new Error("Demasiados redirects"));
            return;
          }
          // El destino del redirect vuelve a pasar por la lista de hosts.
          const siguiente = new URL(respuesta.headers.location, destino).toString();
          resolver(pedir(siguiente, cabeceras, redireccionesRestantes - 1));
          return;
        }
        if (codigo !== 200) {
          respuesta.resume();
          rechazar(new Error("Respuesta " + codigo));
          return;
        }
        resolver(respuesta);
      }
    );

    pedido.on("error", rechazar);
    pedido.on("timeout", () => {
      pedido.destroy();
      rechazar(new Error("Tiempo agotado"));
    });
  });
}

async function bajarTexto(url) {
  const respuesta = await pedir(url, { Accept: "application/vnd.github+json" });
  let texto = "";
  let bytes = 0;
  for await (const trozo of respuesta) {
    bytes += trozo.length;
    if (bytes > 5 * 1024 * 1024) {
      respuesta.destroy();
      throw new Error("Respuesta demasiado grande");
    }
    texto += trozo;
  }
  return texto;
}

/**
 * Baja a un archivo temporal y devuelve su SHA-256. No verifica: quien llama
 * decide que hacer con el resultado. Separar bajar de verificar es lo que
 * permite que el archivo bueno nunca se escriba en el lugar definitivo sin
 * haber pasado el control.
 */
async function bajarArchivo(url, destinoTemporal) {
  await fsp.mkdir(path.dirname(destinoTemporal), { recursive: true });
  const respuesta = await pedir(url, { Accept: "application/octet-stream" });

  const hash = crypto.createHash("sha256");
  const salida = fs.createWriteStream(destinoTemporal);
  let bytes = 0;

  try {
    for await (const trozo of respuesta) {
      bytes += trozo.length;
      if (bytes > TAMANIO_MAXIMO) {
        respuesta.destroy();
        throw new Error("El archivo supera el tamanio maximo permitido");
      }
      hash.update(trozo);
      if (!salida.write(trozo)) {
        await new Promise((r) => salida.once("drain", r));
      }
    }
  } finally {
    await new Promise((r) => salida.end(r));
  }

  return hash.digest("hex");
}

async function sha256DeArchivo(archivo) {
  const hash = crypto.createHash("sha256");
  for await (const trozo of fs.createReadStream(archivo)) {
    hash.update(trozo);
  }
  return hash.digest("hex");
}

// ---------------------------------------------------------------- buscar

/**
 * Consulta la ultima release, y si hay una version posterior la baja y la deja
 * verificada en pendiente/. No aplica nada: eso lo decide el duenio desde la
 * pantalla, y recien pasa en el proximo arranque.
 */
async function buscar(datos, versionDeElectron, registrar = () => {}) {
  const actual = versionInstalada(datos, versionDeElectron);

  const release = JSON.parse(
    await bajarTexto("https://api.github.com/repos/" + REPOSITORIO + "/releases/latest")
  );

  const nueva = release.tag_name;
  if (!debeOfrecerse(nueva, actual, versionesFallidas(datos))) {
    await escribirEstado(datos, { estado: ESTADOS.alDia, version: actual });
    registrar(esPosterior(nueva, actual)
      ? "La version " + nueva + " ya no arranco en esta PC: no se vuelve a ofrecer"
      : "Sin actualizaciones: " + actual + " es la ultima");
    return;
  }

  const jar = (release.assets || []).find((a) => a.name === NOMBRE_DEL_JAR);
  const firma = (release.assets || []).find((a) => a.name === NOMBRE_DEL_HASH);
  if (!jar || !firma) {
    // Una release sin sus dos archivos no se ofrece. Que este a medio publicar
    // es un accidente normal; ofrecerla igual no lo es.
    await escribirEstado(datos, {
      estado: ESTADOS.error,
      version: actual,
      notas: "La version " + nueva + " esta publicada sin el jar o sin su hash",
    });
    return;
  }

  const notas = release.body || "";
  await escribirEstado(datos, { estado: ESTADOS.descargando, version: nueva, notas });
  registrar("Bajando la version " + nueva);

  const esperado = (await bajarTexto(firma.browser_download_url)).trim().split(/\s+/)[0].toLowerCase();
  const temporal = rutaPendiente(datos) + ".parcial";
  const obtenido = await bajarArchivo(jar.browser_download_url, temporal);

  if (obtenido !== esperado) {
    await fsp.rm(temporal, { force: true });
    await escribirEstado(datos, {
      estado: ESTADOS.error,
      version: actual,
      notas: "La descarga de " + nueva + " no coincide con su hash y se descarto",
    });
    registrar("SHA-256 no coincide: se descarto la descarga");
    return;
  }

  // Recien ahora pasa a llamarse como el archivo definitivo. Mientras se bajaba
  // tenia otro nombre, asi que un corte de luz a mitad de camino no deja un jar
  // incompleto haciendose pasar por uno bueno.
  await fsp.rename(temporal, rutaPendiente(datos));
  // El hash queda anotado para poder revalidar el archivo en el proximo
  // arranque, que es cuando se aplica: entre la descarga y el reinicio el jar
  // estuvo en disco, y comprobarlo de nuevo cuesta un segundo.
  await escribirEstado(datos, { estado: ESTADOS.lista, version: nueva, notas, sha256: esperado });
  registrar("Version " + nueva + " descargada y verificada");
}

// ---------------------------------------------------------------- aplicar

/**
 * Si el duenio aprobo una actualizacion, la pone en su lugar. Corre con el
 * servidor apagado: en Windows no se puede reemplazar un archivo que un proceso
 * tiene abierto.
 *
 * Devuelve la version aplicada, o null si no habia nada que hacer.
 */
async function aplicarPendiente(datos, carpetaDelServidor, registrar = () => {}) {
  const senial = rutaSenial(datos);
  if (!fs.existsSync(senial)) return null;

  const anotar = anotador(datos, registrar);
  const aprobada = fs.readFileSync(senial, "utf8").trim();
  const nuevo = rutaPendiente(datos);
  const destino = rutaJar(carpetaDelServidor);

  // La senial se borra pase lo que pase: si algo esta mal, el intento no se
  // repite en cada arranque dejando la app en un bucle.
  await fsp.rm(senial, { force: true });

  if (versionesFallidas(datos).some((f) => compararVersiones(f, aprobada) === 0)) {
    anotar("Se aprobo la version " + aprobada + ", que ya no arranco en esta PC: no se aplica");
    return null;
  }

  if (hayVersionEnPrueba(datos)) {
    // No deberia pasar: la prueba se resuelve en el arranque, antes de que
    // nadie pueda aprobar nada. Si pasa, encimar otra version borraria el
    // camino de vuelta a la ultima que anduvo.
    anotar("Hay una version en prueba sin resolver: no se aplica la " + aprobada);
    return null;
  }

  if (!fs.existsSync(nuevo)) {
    anotar("Se aprobo una actualizacion pero el jar no esta: se ignora");
    return null;
  }

  // Se vuelve a verificar contra el estado guardado. El archivo estuvo en disco
  // entre la descarga y el reinicio, y comprobarlo de nuevo cuesta un segundo.
  let esperado = null;
  try {
    esperado = JSON.parse(fs.readFileSync(rutaEstado(datos), "utf8")).sha256 || null;
  } catch {
    esperado = null;
  }
  if (esperado && (await sha256DeArchivo(nuevo)) !== esperado) {
    await fsp.rm(nuevo, { force: true });
    anotar("El jar pendiente no coincide con su hash: se descarto");
    return null;
  }

  // Copia de la base ANTES de tocar nada. El respaldo diario del servidor corre
  // despues de Flyway, asi que no sirve para deshacer una migracion: este si.
  // Sin copia no hay vuelta atras, y sin vuelta atras no se actualiza.
  let copiaDeLaBase;
  try {
    copiaDeLaBase = await respaldarBase(datos, aprobada);
  } catch (error) {
    anotar("No se pudo copiar la base antes de actualizar (" + error.message
      + "): la version " + aprobada + " no se aplica");
    return null;
  }

  const desde = marcaDeTiempo();
  await escribirPrueba(datos, {
    version: aprobada,
    anterior: leerInstalada(datos),
    respaldo: copiaDeLaBase,
    desde,
    revirtiendo: false,
  });

  const respaldo = destino + ".anterior";
  try {
    await fsp.rm(respaldo, { force: true });
    if (fs.existsSync(destino)) await fsp.rename(destino, respaldo);
    await fsp.rename(nuevo, destino);
  } catch (error) {
    // Volver atras: si el jar viejo sigue ahi, se lo devuelve a su lugar. Es la
    // diferencia entre una actualizacion que fallo y un negocio sin sistema.
    if (!fs.existsSync(destino) && fs.existsSync(respaldo)) {
      await fsp.rename(respaldo, destino);
    }
    await fsp.rm(rutaEnPrueba(datos), { force: true });
    anotar("No se pudo reemplazar el jar: " + error.message);
    return null;
  }

  await fsp.writeFile(rutaInstalada(datos), aprobada, "utf8");
  await escribirEstado(datos, { estado: ESTADOS.alDia, version: aprobada });
  anotar("Actualizado a la version " + aprobada + " (en prueba hasta que arranque; base copiada en "
    + (copiaDeLaBase || "ningun lado: no habia base") + ")");
  return aprobada;
}

function hayActualizacionAprobada(datos) {
  return fs.existsSync(rutaSenial(datos));
}

// ------------------------------------------------------------ vuelta atras
//
// Un jar recien aplicado queda "en prueba" (en-prueba.json) hasta que el
// servidor responde. Si no responde, se vuelve solo a lo de antes:
//
//   aplicado ──responde──> confirmado (se borra en-prueba.json)
//      │
//      └──no responde──> revirtiendo ──> jar, base y version de antes
//                        (en-prueba.json con revirtiendo:true; se borra al final)
//
// Cada paso de la vuelta atras se puede repetir: si se corta la luz a la mitad,
// en-prueba.json sigue diciendo "revirtiendo" y el proximo arranque la termina
// antes de levantar nada. La version que fallo queda en fallidas.txt y no se
// vuelve a aplicar sola. Si la anterior tampoco arranca, no hay mas nada que
// restaurar: en-prueba.json ya no existe, asi que no se entra en un bucle.

function anotador(datos, registrar) {
  return (mensaje) => {
    registrar(mensaje);
    registrarEnHistorial(datos, mensaje);
  };
}

function registrarEnHistorial(datos, mensaje) {
  try {
    fs.mkdirSync(carpeta(datos), { recursive: true });
    fs.appendFileSync(rutaHistorial(datos), new Date().toISOString() + " " + mensaje + "\n", "utf8");
  } catch {
    // Sin historial se sigue: no poder anotar no es motivo para no arrancar.
  }
}

function marcaDeTiempo() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

/**
 * La version termina en un nombre de archivo, y viene de una senial que
 * escribe el servidor con lo que dijo GitHub: nada de barras ni "..".
 */
function paraNombreDeArchivo(texto) {
  return String(texto).replace(/[^0-9A-Za-z.-]/g, "_").replace(/\.{2,}/g, "_");
}

function leerInstalada(datos) {
  try {
    return fs.readFileSync(rutaInstalada(datos), "utf8").trim() || null;
  } catch {
    return null;
  }
}

function leerPrueba(datos) {
  try {
    return JSON.parse(fs.readFileSync(rutaEnPrueba(datos), "utf8"));
  } catch {
    return null;
  }
}

/** Temporal y rename: un corte de luz no deja un en-prueba.json a medias. */
async function escribirPrueba(datos, contenido) {
  const temporal = rutaEnPrueba(datos) + ".parcial";
  await fsp.writeFile(temporal, JSON.stringify(contenido, null, 2), "utf8");
  await fsp.rename(temporal, rutaEnPrueba(datos));
}

/**
 * Copia y comprueba que la copia sea identica al original (BKP-04: una copia
 * que no se verifico no es una copia). Deja el resultado en destino; quien
 * llama decide cuando renombrarla a su nombre definitivo.
 */
async function copiarVerificado(origen, destino) {
  await fsp.mkdir(path.dirname(destino), { recursive: true });
  await fsp.copyFile(origen, destino);
  if ((await sha256DeArchivo(origen)) !== (await sha256DeArchivo(destino))) {
    await fsp.rm(destino, { force: true });
    throw new Error("la copia de " + origen + " no coincide con el original");
  }
}

/** Devuelve la ruta de la copia, o null si todavia no habia base. */
async function respaldarBase(datos, version) {
  const base = rutaBase(datos);
  if (!fs.existsSync(base)) return null;
  const copia = path.join(carpetaRespaldos(datos),
    "antes-de-" + paraNombreDeArchivo(version) + "-" + marcaDeTiempo() + ".mv.db");
  await copiarVerificado(base, copia + ".parcial");
  await fsp.rename(copia + ".parcial", copia);
  return copia;
}

function hayVersionEnPrueba(datos) {
  return leerPrueba(datos) !== null;
}

function reversionInterrumpida(datos) {
  const prueba = leerPrueba(datos);
  return prueba !== null && prueba.revirtiendo === true;
}

function versionesFallidas(datos) {
  try {
    return fs.readFileSync(rutaFallidas(datos), "utf8").split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  } catch {
    return [];
  }
}

async function marcarFallida(datos, version) {
  if (versionesFallidas(datos).includes(version)) return;
  await fsp.appendFile(rutaFallidas(datos), version + "\n", "utf8");
}

/** El servidor respondio con la version en prueba: queda confirmada. */
async function confirmarArranque(datos, registrar = () => {}) {
  const prueba = leerPrueba(datos);
  if (!prueba || prueba.revirtiendo) return null;
  await fsp.rm(rutaEnPrueba(datos), { force: true });
  anotador(datos, registrar)("La version " + prueba.version + " arranco bien: queda confirmada");
  return prueba.version;
}

/**
 * La version en prueba no arranco: vuelve el jar anterior, la base copiada y
 * la version anotada. Corre con el servidor detenido. Devuelve
 * { fallida, restaurada } o null si no habia nada en prueba.
 */
async function volverALaAnterior(datos, carpetaDelServidor, registrar = () => {}) {
  const prueba = leerPrueba(datos);
  if (!prueba) return null;
  const anotar = anotador(datos, registrar);
  const { version, anterior, respaldo } = prueba;

  anotar("La version " + version + " no arranco: se vuelve a la "
    + (anterior || "del instalador") + (prueba.revirtiendo ? " (retomando una vuelta atras cortada)" : ""));
  if (!prueba.revirtiendo) await escribirPrueba(datos, { ...prueba, revirtiendo: true });
  await marcarFallida(datos, version);

  // 1. El jar. rename pisa el destino y es atomico: o quedo el viejo o no.
  const jar = rutaJar(carpetaDelServidor);
  if (fs.existsSync(jar + ".anterior")) await fsp.rename(jar + ".anterior", jar);

  // 2. La base. La copia se prepara al lado ANTES de mover nada: si falla, la
  // base sigue en su lugar y el proximo arranque lo vuelve a intentar.
  const base = rutaBase(datos);
  const apartada = path.join(carpetaRespaldos(datos),
    "fallida-" + paraNombreDeArchivo(version) + "-" + paraNombreDeArchivo(prueba.desde) + ".mv.db");
  if (respaldo && !fs.existsSync(respaldo)) {
    // Alguien borro la copia. Sin ella no hay a que volver; mover la base
    // dejaria a la version anterior arrancando con una base vacia, que es peor.
    anotar("No esta la copia " + respaldo + ": la base queda como la dejo la " + version);
  } else {
    if (respaldo) await copiarVerificado(respaldo, base + ".parcial");
    if (fs.existsSync(base) && !fs.existsSync(apartada)) {
      // La base que dejo la version rota no se tira: es lo que hay que mirar.
      await fsp.mkdir(carpetaRespaldos(datos), { recursive: true });
      await fsp.rename(base, apartada);
    }
    if (respaldo) await fsp.rename(base + ".parcial", base);
    else await fsp.rm(base, { force: true });
  }

  // 3. La version anotada.
  if (anterior) await fsp.writeFile(rutaInstalada(datos), anterior, "utf8");
  else await fsp.rm(rutaInstalada(datos), { force: true });
  await escribirEstado(datos, {
    estado: ESTADOS.error,
    version: anterior,
    notas: "La version " + version + " no arranco y se volvio a la anterior",
  });
  await fsp.rm(rutaPendiente(datos), { force: true });

  // 4. Recien ahora se cierra la prueba: es el "commit" de la vuelta atras.
  await fsp.rm(rutaEnPrueba(datos), { force: true });
  anotar("Vuelta atras completa: jar, base y version de antes de la " + version);
  return { fallida: version, restaurada: anterior };
}

// ---------------------------------------------------------------- arranque

/**
 * El recorrido entero de un arranque, sin nada de Electron adentro para poder
 * probarlo. ops:
 *   yaResponde()        hay un servidor de antes respondiendo en el puerto
 *   lanzar()            arranca el servidor con el jar que haya en su lugar
 *   esperar(enPrueba)   true si respondio; false si murio o se agoto la espera
 *   detener()           lo apaga y espera a que termine (libera jar y base)
 *
 * Devuelve { resultado: "ok" | "ya-estaba" | "revertida" | "no-arranco", ... }
 */
async function arrancar(datos, carpetaDelServidor, ops, registrar = () => {}) {
  const anotar = anotador(datos, registrar);
  let vuelta = null;

  try {
    if (reversionInterrumpida(datos)) {
      vuelta = await volverALaAnterior(datos, carpetaDelServidor, registrar);
    } else {
      await aplicarPendiente(datos, carpetaDelServidor, registrar);
    }
  } catch (error) {
    anotar("No se pudo preparar el arranque: " + error.message);
    // Una vuelta atras a medias no se arranca: el jar y la base pueden no ser
    // de la misma version. Se reintenta en el proximo arranque.
    if (reversionInterrumpida(datos)) return { resultado: "no-arranco", error: error.message };
  }

  if (await ops.yaResponde()) return { resultado: "ya-estaba" };

  const enPrueba = hayVersionEnPrueba(datos);
  ops.lanzar();
  if (await ops.esperar(enPrueba)) {
    if (enPrueba) await confirmarArranque(datos, registrar);
    return vuelta ? { resultado: "revertida", ...vuelta } : { resultado: "ok" };
  }
  if (!enPrueba) return { resultado: "no-arranco", ...(vuelta || {}) };

  await ops.detener();
  try {
    vuelta = await volverALaAnterior(datos, carpetaDelServidor, registrar);
  } catch (error) {
    anotar("No se pudo volver a la version anterior: " + error.message);
    return { resultado: "no-arranco", error: error.message };
  }

  ops.lanzar();
  if (await ops.esperar(false)) return { resultado: "revertida", ...vuelta };
  anotar("La version " + (vuelta.restaurada || "del instalador")
    + " tampoco arranco: no se restaura nada mas");
  return { resultado: "no-arranco", ...vuelta };
}

module.exports = {
  aplicarPendiente,
  arrancar,
  buscar,
  confirmarArranque,
  debeOfrecerse,
  esPosterior,
  hayActualizacionAprobada,
  hayVersionEnPrueba,
  registrarEnHistorial,
  versionesFallidas,
  versionInstalada,
  volverALaAnterior,
};
