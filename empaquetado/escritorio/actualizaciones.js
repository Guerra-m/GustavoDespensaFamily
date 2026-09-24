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

// ---------------------------------------------------------------- versiones

/**
 * Compara por numero y no por texto: como texto, "1.10.0" es menor que "1.9.0".
 * Es el espejo de Version.java del lado del servidor, y por el mismo motivo:
 * una version ilegible no es posterior a nada, asi que no dispara ningun aviso.
 */
function esPosterior(candidata, actual) {
  const partes = (v) => {
    if (!v) return null;
    const limpia = String(v).trim().replace(/^[vV]/, "").replace(/[-+].*$/, "");
    if (!limpia) return null;
    const trozos = limpia.split(".").map((t) => Number.parseInt(t, 10));
    return trozos.some((n) => Number.isNaN(n)) ? null : trozos;
  };

  const a = partes(candidata);
  const b = partes(actual);
  if (!a || !b) return false;

  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const x = a[i] ?? 0;
    const y = b[i] ?? 0;
    if (x !== y) return x > y;
  }
  return false;
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
  if (!esPosterior(nueva, actual)) {
    await escribirEstado(datos, { estado: ESTADOS.alDia, version: actual });
    registrar("Sin actualizaciones: " + actual + " es la ultima");
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

  const aprobada = fs.readFileSync(senial, "utf8").trim();
  const nuevo = rutaPendiente(datos);
  const destino = path.join(carpetaDelServidor, "app", NOMBRE_DEL_JAR);

  // La senial se borra pase lo que pase: si algo esta mal, el intento no se
  // repite en cada arranque dejando la app en un bucle.
  await fsp.rm(senial, { force: true });

  if (!fs.existsSync(nuevo)) {
    registrar("Se aprobo una actualizacion pero el jar no esta: se ignora");
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
    registrar("El jar pendiente no coincide con su hash: se descarto");
    return null;
  }

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
    registrar("No se pudo reemplazar el jar: " + error.message);
    return null;
  }

  await fsp.writeFile(rutaInstalada(datos), aprobada, "utf8");
  await escribirEstado(datos, { estado: ESTADOS.alDia, version: aprobada });
  registrar("Actualizado a la version " + aprobada);
  return aprobada;
}

function hayActualizacionAprobada(datos) {
  return fs.existsSync(rutaSenial(datos));
}

module.exports = {
  aplicarPendiente,
  buscar,
  esPosterior,
  hayActualizacionAprobada,
  versionInstalada,
};
