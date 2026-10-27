// Pruebas de caida.js: la logica de "el servidor termino, ¿que hago?" sin nada
// de Electron adentro, igual que actualizaciones.js. DF-ELEC-002.

const test = require("node:test");
const assert = require("node:assert");

const { clasificarSalida, debeReintentar, avisoDeCaida } = require("./caida");

// ------------------------------------------------------------ clasificarSalida

test("cierre voluntario: no hay nada que hacer", () => {
  // El usuario cerro la ventana: before-quit ya puso cerrando=true.
  assert.equal(
    clasificarSalida({ cerrando: true, arrancando: false, esElServidorActual: true, hayActualizacionAprobada: false }),
    "ignorar"
  );
});

test("caida durante la prueba de una actualizacion: la maneja el rollback, no el reintento", () => {
  // actualizaciones.arrancar esta vigilando este mismo hijo (arrancando=true).
  // Si tambien se dispara un reintento desde aca, se pisan los dos.
  assert.equal(
    clasificarSalida({ cerrando: false, arrancando: true, esElServidorActual: true, hayActualizacionAprobada: false }),
    "ignorar"
  );
});

test("un proceso viejo que termina despues de que ya se lanzo otro no es noticia", () => {
  assert.equal(
    clasificarSalida({ cerrando: false, arrancando: false, esElServidorActual: false, hayActualizacionAprobada: true }),
    "ignorar"
  );
});

test("actualizacion aprobada: se cambia el jar y se reinicia, no es una caida", () => {
  assert.equal(
    clasificarSalida({ cerrando: false, arrancando: false, esElServidorActual: true, hayActualizacionAprobada: true }),
    "actualizar"
  );
});

test("nada de lo anterior: es una caida de verdad", () => {
  assert.equal(
    clasificarSalida({ cerrando: false, arrancando: false, esElServidorActual: true, hayActualizacionAprobada: false }),
    "caida"
  );
});

// ------------------------------------------------------------ debeReintentar

test("la primera caida se reintenta sola", () => {
  assert.equal(debeReintentar(0), true);
});

test("una segunda caida seguida ya no se reintenta sola: se avisa", () => {
  assert.equal(debeReintentar(1), false);
  assert.equal(debeReintentar(2), false);
});

// ------------------------------------------------------------ avisoDeCaida

test("sin motivo puntual, el aviso es generico y menciona el puerto y el log", () => {
  const aviso = avisoDeCaida({ motivo: null, puerto: 8080, registro: "C:\\logs\\despensafamily.log" });
  assert.match(aviso.mensaje, /8080/);
  assert.match(aviso.mensaje, /C:\\logs\\despensafamily.log/);
  assert.deepEqual(aviso.botones, ["Reintentar", "Salir"]);
});

test("con error-de-arranque.txt escrito, se muestra ese motivo y no el generico", () => {
  const aviso = avisoDeCaida({
    motivo: "La base fue usada por una version mas nueva y no se puede abrir con esta.",
    puerto: 8080,
    registro: "C:\\logs\\despensafamily.log",
  });
  assert.match(aviso.mensaje, /version mas nueva/);
  assert.doesNotMatch(aviso.mensaje, /se quedo esperando/);
});
