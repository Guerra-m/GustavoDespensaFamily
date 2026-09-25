// Ventana de escritorio de DespensaFamily.
//
// El producto sigue siendo el mismo servidor Spring Boot de siempre; lo unico
// que cambia es quien muestra la pantalla. Antes la mostraba el navegador del
// sistema (con su barra de direcciones, sus pestanas y la duda de si cerrarlo
// cerraba el programa). Ahora la muestra este proceso: una ventana propia, con
// el icono de la app en la barra de tareas, que arranca el servidor como
// proceso hijo y lo apaga al cerrarse.
//
// Reparto de tareas:
//   este proceso  -> ventana, instancia unica, ciclo de vida del servidor
//   el servidor   -> todo lo demas (datos, respaldos, icono de la bandeja)

const { app, BrowserWindow, dialog, shell } = require("electron");
const { spawn } = require("node:child_process");
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const actualizaciones = require("./actualizaciones");

const PUERTO_POR_DEFECTO = 8080;
const PAGINA_LOGIN = "/src/pages/login/login.html";

// El servidor tarda entre 5 y 15 segundos en la PC del negocio. 90 es holgado
// a proposito: preferimos esperar de mas antes que acusar un error que no es.
const ESPERA_MAXIMA_MS = 90000;
const INTERVALO_DE_SONDEO_MS = 400;

// Margen para que el servidor cierre la base con orden antes de matarlo.
const MARGEN_DE_CIERRE_MS = 8000;

let ventana = null;
let servidor = null;
let cerrando = false;

// ---------------------------------------------------------------- rutas

/**
 * La misma carpeta que resuelve AppPaths del lado de Java. Se duplica el
 * criterio (no la ruta) porque este proceso arranca antes que el servidor y
 * necesita saber en que puerto va a escuchar.
 */
function carpetaDeDatos() {
  if (process.env.DESPENSA_HOME) return process.env.DESPENSA_HOME;
  if (process.env.LOCALAPPDATA) return path.join(process.env.LOCALAPPDATA, "DespensaFamily");
  return path.join(app.getPath("home"), ".despensafamily");
}

/** Espejo de Escritorio.puertoConfigurado: si algo falla, el puerto de siempre. */
function puertoConfigurado() {
  try {
    const texto = fs.readFileSync(path.join(carpetaDeDatos(), "config.properties"), "utf8");
    const linea = texto.split(/\r?\n/).find((l) => l.trim().startsWith("server.port="));
    if (!linea) return PUERTO_POR_DEFECTO;
    const puerto = Number.parseInt(linea.split("=")[1].trim(), 10);
    return Number.isInteger(puerto) && puerto > 0 && puerto < 65536 ? puerto : PUERTO_POR_DEFECTO;
  } catch {
    return PUERTO_POR_DEFECTO;
  }
}

/**
 * Empaquetado, el servidor viaja en resources/servidor (extraResources).
 * Sin empaquetar, en empaquetado/build, que es donde lo deja jpackage: asi se
 * puede probar la ventana con "npm start" sin armar el instalador entero.
 */
function carpetaDelServidor() {
  return app.isPackaged
    ? path.join(process.resourcesPath, "servidor")
    : path.join(__dirname, "..", "build", "DespensaFamilyServidor");
}

function rutaDelServidor() {
  return path.join(carpetaDelServidor(), "DespensaFamilyServidor.exe");
}

function urlDeLaApp(puerto) {
  return "http://localhost:" + puerto + PAGINA_LOGIN;
}

// ---------------------------------------------------------------- servidor

/**
 * Pregunta por la pagina de login y no solo por el puerto: asi no confundimos
 * "el servidor ya esta listo" con "otro programa agarro el 8080". Es el mismo
 * criterio que usa Escritorio.yaEstaCorriendo del lado de Java.
 */
function servidorResponde(puerto) {
  return new Promise((resolver) => {
    const pedido = http.request(
      { host: "127.0.0.1", port: puerto, path: PAGINA_LOGIN, method: "HEAD", timeout: 1000 },
      (respuesta) => {
        respuesta.resume();
        resolver(respuesta.statusCode === 200);
      }
    );
    pedido.on("error", () => resolver(false));
    pedido.on("timeout", () => {
      pedido.destroy();
      resolver(false);
    });
    pedido.end();
  });
}

function esperar(ms) {
  return new Promise((resolver) => setTimeout(resolver, ms));
}

/**
 * Espera a que responda. Si el proceso ya termino no tiene sentido seguir
 * esperando los 90 segundos: un jar que no arranca suele morir enseguida (una
 * migracion que falla, un puerto ocupado) y cada segundo es la caja parada.
 */
async function esperarAlServidor(puerto, hijo, maximoMs = ESPERA_MAXIMA_MS) {
  const limite = Date.now() + maximoMs;
  while (Date.now() < limite) {
    if (await servidorResponde(puerto)) return true;
    if (hijo && (hijo.exitCode !== null || hijo.signalCode !== null)) return false;
    await esperar(INTERVALO_DE_SONDEO_MS);
  }
  return false;
}

/**
 * Arranca el servidor como proceso hijo.
 *
 * stdin queda abierto a proposito: es el cable por el que despues le avisamos
 * que cierre. Cuando este proceso termina (de forma ordenada o no), el sistema
 * cierra ese pipe, el servidor ve el fin de la entrada y se apaga solo. Es lo
 * que evita que quede un servidor huerfano ocupando el puerto y la base.
 */
function arrancarServidor(puerto) {
  const ejecutable = rutaDelServidor();
  if (!fs.existsSync(ejecutable)) {
    throw new Error("No se encontro el servidor en " + ejecutable);
  }
  return spawn(ejecutable, ["--despensa.escritorio.modo=ventana", "--server.port=" + puerto], {
    windowsHide: true,
    stdio: ["pipe", "ignore", "ignore"],
  });
}

/**
 * Cierre en dos tiempos: primero se cierra stdin, que es el pedido "cerra con
 * orden" (el servidor cierra la base y sale). Si pasado el margen sigue vivo,
 * se lo mata. Mismo criterio que el "cierre-forzado" del lado de Java: el
 * usuario cerro la ventana, algo tiene que terminar si o si.
 */
function detenerServidor() {
  if (!servidor || servidor.exitCode !== null || servidor.killed) return;
  const hijo = servidor;
  try {
    hijo.stdin.end();
  } catch {
    // El pipe ya estaba cerrado: no cambia nada, igual va la red de seguridad.
  }
  setTimeout(() => {
    if (hijo.exitCode === null) hijo.kill();
  }, MARGEN_DE_CIERRE_MS);
}

/**
 * Lo mismo, pero esperando a que termine de verdad: antes de volver a la
 * version anterior el jar y la base tienen que estar libres, y en Windows no se
 * puede tocar un archivo que un proceso tiene abierto.
 */
function detenerYEsperar() {
  const hijo = servidor;
  if (!hijo || hijo.exitCode !== null || hijo.signalCode !== null) return Promise.resolve();
  return new Promise((resolver) => {
    hijo.once("exit", () => resolver());
    try {
      hijo.stdin.end();
    } catch {
      // Ya cerrado: la red de seguridad de abajo lo mata igual.
    }
    setTimeout(() => {
      if (hijo.exitCode === null) hijo.kill();
    }, MARGEN_DE_CIERRE_MS);
  });
}

// ---------------------------------------------------------------- ventana

function crearVentana(puerto) {
  const origen = "http://localhost:" + puerto;

  ventana = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1024,
    minHeight: 640,
    show: false,
    backgroundColor: "#f4f6f8",
    title: "DespensaFamily",
    autoHideMenuBar: true,
    webPreferences: {
      // Minimo privilegio: la pagina es una web comun servida por el propio
      // servidor y no necesita nada de Node. Sin preload no hay puente que
      // exponer, asi que el aislamiento no tiene agujeros que tapar.
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
    },
  });

  ventana.once("ready-to-show", () => ventana.show());
  ventana.on("closed", () => {
    ventana = null;
  });

  // La ventana solo navega dentro de la propia aplicacion. Cualquier otra cosa
  // (un link externo que aparezca algun dia) se abre en el navegador del
  // sistema, donde no tiene los privilegios de esta ventana.
  ventana.webContents.on("will-navigate", (evento, destino) => {
    if (destino.startsWith(origen)) return;
    evento.preventDefault();
    abrirAfuera(destino);
  });

  ventana.webContents.setWindowOpenHandler(({ url }) => {
    // Los tickets y los carteles de precio se imprimen abriendo una ventana en
    // blanco y escribiendo el HTML adentro, asi que about:blank tiene que poder
    // abrirse o se rompe la impresion.
    if (url === "about:blank" || url.startsWith(origen)) return { action: "allow" };
    abrirAfuera(url);
    return { action: "deny" };
  });

  return ventana;
}

function abrirAfuera(destino) {
  if (destino.startsWith("http://") || destino.startsWith("https://")) {
    shell.openExternal(destino);
  }
}

/**
 * Si el servidor se nego a arrancar por un motivo que el usuario puede
 * entender (una base que uso una version mas nueva), lo deja escrito aca. Solo
 * vale si es de este intento: uno viejo confundiria mas de lo que ayuda.
 */
function motivoDelServidor(desde) {
  const archivo = path.join(carpetaDeDatos(), "logs", "error-de-arranque.txt");
  try {
    if (fs.statSync(archivo).mtimeMs < desde) return null;
    return fs.readFileSync(archivo, "utf8").trim() || null;
  } catch {
    return null;
  }
}

function avisarQueNoArranco(puerto, desde = Date.now()) {
  const registro = path.join(carpetaDeDatos(), "logs", "despensafamily.log");
  const motivo = motivoDelServidor(desde);
  dialog.showErrorBox(
    "DespensaFamily no pudo iniciar",
    (motivo
      ? motivo + "\n\n"
      : "El programa se quedo esperando al servidor en el puerto " + puerto + ".\n\n" +
        "Proba cerrar y volver a abrir. ") +
      "Si sigue igual, el detalle esta en:\n" +
      registro
  );
}

// ---------------------------------------------------------- actualizaciones

/**
 * Cada cuanto se mira si hay una version nueva. Seis horas y no cinco minutos:
 * el negocio abre a la manana y cierra a la noche, asi que un par de consultas
 * por dia alcanzan de sobra, y cada una es trafico de la conexion del local.
 */
const CADA_CUANTO_BUSCAR_MS = 6 * 60 * 60 * 1000;

/** La primera busqueda espera a que la caja este usable: primero atender. */
const DEMORA_PRIMERA_BUSQUEDA_MS = 60 * 1000;

function registrarActualizacion(mensaje) {
  // Va al log del propio launcher (stdout de Electron). El detalle util para
  // el negocio ya queda en estado.json, que es lo que ve la pantalla.
  console.log("[actualizaciones] " + mensaje);
}

async function buscarActualizacion() {
  try {
    await actualizaciones.buscar(carpetaDeDatos(), app.getVersion(), registrarActualizacion);
  } catch (error) {
    // Sin internet, GitHub caido, la PC en una red sin salida: nada de eso es
    // un problema de la aplicacion. Se anota y se sigue vendiendo.
    registrarActualizacion("No se pudo consultar: " + (error.message || error));
  }
}

function programarBusquedaDeActualizaciones() {
  setTimeout(() => {
    buscarActualizacion();
    setInterval(buscarActualizacion, CADA_CUANTO_BUSCAR_MS);
  }, DEMORA_PRIMERA_BUSQUEDA_MS);
}

/**
 * Una version en prueba tiene el doble de margen: la primera vez corre las
 * migraciones de la base, y declarar rota una version que solo era lenta la
 * dejaria marcada como fallida sin motivo.
 */
const ESPERA_MAXIMA_EN_PRUEBA_MS = 2 * ESPERA_MAXIMA_MS;

/**
 * Mientras se esta arrancando (y eventualmente volviendo a la version
 * anterior), que el servidor termine no significa "cerrar el programa": es lo
 * que se esta vigilando, y lo resuelve actualizaciones.arrancar.
 */
let arrancando = false;

/**
 * Arranca el servidor pasando por el actualizador: aplica lo aprobado, espera
 * a que responda, y si una version recien aplicada no responde, vuelve sola a
 * la anterior —jar, version y base— y arranca esa.
 *
 * Devuelve true si quedo un servidor respondiendo.
 */
async function arrancarConActualizaciones(puerto, { puedeHaberUnoCorriendo }) {
  const desde = Date.now();
  arrancando = true;
  let resultado;
  try {
    resultado = await actualizaciones.arrancar(
      carpetaDeDatos(),
      carpetaDelServidor(),
      {
        yaResponde: async () => puedeHaberUnoCorriendo && (await servidorResponde(puerto)),
        lanzar: () => lanzarServidor(puerto),
        esperar: (enPrueba) =>
          esperarAlServidor(puerto, servidor, enPrueba ? ESPERA_MAXIMA_EN_PRUEBA_MS : ESPERA_MAXIMA_MS),
        detener: detenerYEsperar,
      },
      registrarActualizacion
    );
  } catch (error) {
    // lanzar() tira si no esta el ejecutable del servidor.
    arrancando = false;
    dialog.showErrorBox("DespensaFamily no pudo iniciar", String(error.message || error));
    return false;
  }
  arrancando = false;

  registrarActualizacion("Arranque: " + JSON.stringify(resultado));

  if (resultado.resultado === "no-arranco") {
    avisarQueNoArranco(puerto, desde);
    return false;
  }

  if (resultado.resultado === "revertida") {
    // Sin await: el aviso no tiene que frenar que la caja vuelva a estar usable.
    dialog.showMessageBox(ventana, {
      type: "warning",
      title: "DespensaFamily",
      message: "La actualizacion a la version " + resultado.fallida + " no pudo arrancar.",
      detail:
        "Se volvio sola a la version " + (resultado.restaurada || "anterior") +
        ", con los datos como estaban justo antes de actualizar. Se puede seguir usando normalmente." +
        "\n\nEsa version no se va a volver a instalar sola. Lo que paso quedo anotado en:\n" +
        path.join(carpetaDeDatos(), "actualizaciones", "historial.log"),
    });
  }
  return true;
}

/**
 * El servidor se apago porque el duenio aprobo una actualizacion. Se reemplaza
 * el jar —recien ahora se puede, con el archivo ya liberado— y se lo vuelve a
 * levantar. La ventana muestra mientras tanto la misma pantalla de carga del
 * arranque, asi que se ve como un reinicio y no como una caida.
 */
async function reiniciarActualizando(puerto) {
  if (ventana) await ventana.loadFile(path.join(__dirname, "cargando.html"));

  if (!(await arrancarConActualizaciones(puerto, { puedeHaberUnoCorriendo: false }))) {
    app.quit();
    return;
  }
  if (ventana) await ventana.loadURL(urlDeLaApp(puerto));
}

// ---------------------------------------------------------------- arranque

/**
 * Arranca el servidor y decide que hacer cuando termine. Son cuatro finales
 * distintos y conviene no confundirlos:
 *
 *   - cerrando        -> lo pidio el usuario al cerrar la ventana: no hacer nada
 *   - arrancando      -> lo esta vigilando el arranque, que decide que sigue
 *   - hay una senial  -> se aprobo una actualizacion: cambiar el jar y volver
 *   - cualquier otro  -> el servidor se cayo o lo cerraron desde la bandeja
 */
function lanzarServidor(puerto) {
  const hijo = arrancarServidor(puerto);
  servidor = hijo;
  hijo.on("exit", () => {
    if (cerrando || arrancando) return;
    // Un proceso viejo que termina despues de que se lanzo otro no es noticia.
    if (hijo !== servidor) return;
    if (actualizaciones.hayActualizacionAprobada(carpetaDeDatos())) {
      reiniciarActualizando(puerto);
      return;
    }
    app.quit();
  });
  hijo.on("error", () => {
    if (!cerrando && !arrancando) app.quit();
  });
  return hijo;
}

async function iniciar() {
  const puerto = puertoConfigurado();

  crearVentana(puerto);
  await ventana.loadFile(path.join(__dirname, "cargando.html"));

  // Antes de arrancar nada, actualizaciones.arrancar termina lo que haya
  // quedado de la sesion anterior: una vuelta atras cortada por un apagon, o
  // una actualizacion aprobada que no llego a aplicarse. Si ya hay un servidor
  // respondiendo (quedo de un cierre anterior que no termino de bajar), no se
  // arranca otro: se usa el que esta.
  if (!(await arrancarConActualizaciones(puerto, { puedeHaberUnoCorriendo: true }))) {
    app.quit();
    return;
  }

  if (ventana) await ventana.loadURL(urlDeLaApp(puerto));

  programarBusquedaDeActualizaciones();
}


/**
 * Una sola copia abierta: dos servidores peleando por el mismo puerto y por el
 * mismo archivo de base es la unica forma de corromper datos que teniamos a
 * mano. Hacer doble clic en el icono con la app abierta trae la ventana al
 * frente, que es lo que la gente espera.
 */
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (!ventana) return;
    if (ventana.isMinimized()) ventana.restore();
    ventana.focus();
  });

  app.whenReady().then(iniciar);
}

// Cerrar la ventana cierra el programa: es lo que se espera de un programa de
// escritorio y lo que antes habia que explicar con un cartel en la bandeja.
app.on("window-all-closed", () => app.quit());

app.on("before-quit", () => {
  cerrando = true;
  detenerServidor();
});
