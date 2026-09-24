package com.guerram.DespensaFamily.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Convierte el rechazo a arrancar sobre una base mas nueva en el cartel de
 * "APPLICATION FAILED TO START" de Spring, con palabras y sin stacktrace. Es lo
 * que queda en despensafamily.log y en la consola del jar suelto.
 */
public class BaseDeUnaVersionMasNuevaFailureAnalyzer
        extends AbstractFailureAnalyzer<BaseDeUnaVersionMasNuevaException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, BaseDeUnaVersionMasNuevaException causa) {
        return new FailureAnalysis(
                "Esta base de datos la uso una version mas nueva de DespensaFamily (esquema "
                        + causa.versionDeLaBase() + "; este programa llega hasta el "
                        + causa.versionDelPrograma() + "). Arrancar igual podria danar los datos.",
                "No abras esta version vieja: instala la ultima version de DespensaFamily."
                        + " Los datos no se tocaron.",
                causa);
    }
}
