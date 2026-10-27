package com.guerram.DespensaFamily.actualizacion;

/**
 * Lo que la pantalla necesita saber para decidir si muestra el aviso de
 * actualizacion, y si el boton puede hacer algo.
 *
 * @param versionInstalada      la que esta corriendo ahora
 * @param versionDisponible     la que encontro el launcher, o null si no hay
 * @param hayActualizacion      si la disponible es posterior a la instalada
 * @param listaParaAplicar      si el launcher ya la descargo y verifico el
 *                              SHA-256; hasta que esto sea true el boton de
 *                              actualizar no tiene nada que aplicar
 * @param notas                 que trae la version nueva, para mostrarle al
 *                              usuario antes de que decida
 * @param gestionadoPorLauncher false cuando el jar corre suelto: no hay quien
 *                              aplique la actualizacion, asi que no se ofrece
 */
public record EstadoDeActualizacion(
        String versionInstalada,
        String versionDisponible,
        boolean hayActualizacion,
        boolean listaParaAplicar,
        String notas,
        boolean gestionadoPorLauncher) {

    static EstadoDeActualizacion sinLauncher(String versionInstalada) {
        return new EstadoDeActualizacion(versionInstalada, null, false, false, null, false);
    }
}
