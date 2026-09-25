// Que hacer cuando el proceso del servidor termina, sin avisar. DF-ELEC-002:
// antes, main.js hacia app.quit() ante CUALQUIER salida del hijo, sin
// distinguir una caida real de un cierre voluntario o de una actualizacion en
// curso. La ventana "desaparecia" en pleno horario de atencion.
//
// Sin nada de Electron adentro, igual que actualizaciones.js, para poder
// probarlo con "node --test". Quien llama (main.js) es quien sabe cerrando,
// arrancando, cual es el hijo actual y si hay una actualizacion aprobada; esta
// funcion solo decide que corresponde hacer con esos datos.
//
// Los otros dos finales del proceso hijo los resuelven modulos que YA existen
// y no hay que duplicar aca:
//   - una caida DURANTE la prueba de una actualizacion -> actualizaciones.arrancar
//     (el flag "arrancando" de main.js se lo indica; por eso clasificarSalida
//     lo ignora, no lo reintenta)
//   - un cierre voluntario -> before-quit ya puso "cerrando" antes de que el
//     hijo termine

/**
 * Que corresponde hacer cuando el hijo del servidor termino.
 *
 *   "ignorar"    cierre voluntario, caida vigilada por el actualizador, o un
 *                proceso viejo que ya no es el que esta activo
 *   "actualizar" el servidor se apago solo porque se aprobo una actualizacion
 *   "caida"      el servidor se murio sin que nadie lo haya pedido
 */
function clasificarSalida({ cerrando, arrancando, esElServidorActual, hayActualizacionAprobada }) {
  if (cerrando || arrancando || !esElServidorActual) return "ignorar";
  if (hayActualizacionAprobada) return "actualizar";
  return "caida";
}

/**
 * Un solo reintento automatico por caida: si el servidor esta realmente roto
 * (una base corrupta, un puerto que otro proceso agarro de nuevo), reintentar
 * sin limite es un bucle silencioso y no un aviso. `intentos` es cuantas veces
 * ya se reintento desde la ultima caida sin aviso de por medio.
 */
function debeReintentar(intentos) {
  return intentos < 1;
}

/**
 * El aviso que ve el cajero cuando el reintento automatico tambien fallo. Si
 * el servidor dejo escrito por que se nego a arrancar (error-de-arranque.txt,
 * de este mismo intento), se muestra ese motivo en vez del generico.
 */
function avisoDeCaida({ motivo, puerto, registro }) {
  const cuerpo = motivo
    ? motivo
    : "El servidor se detuvo y no pudo volver a arrancar solo en el puerto " + puerto + ".";
  return {
    titulo: "DespensaFamily se detuvo",
    mensaje:
      cuerpo +
      "\n\nSe puede reintentar, o cerrar el programa y avisar a soporte." +
      "\n\nEl detalle esta en:\n" +
      registro,
    botones: ["Reintentar", "Salir"],
  };
}

module.exports = { clasificarSalida, debeReintentar, avisoDeCaida };
