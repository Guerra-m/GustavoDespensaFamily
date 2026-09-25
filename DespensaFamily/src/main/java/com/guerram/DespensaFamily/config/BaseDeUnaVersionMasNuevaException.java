package com.guerram.DespensaFamily.config;

/**
 * La base la migro una version de DespensaFamily posterior a la que esta
 * arrancando. Seguir la degradaria (DF-INST-002): la version vieja no conoce las
 * columnas nuevas y, con suerte, solo falla; sin suerte, las pisa.
 */
public class BaseDeUnaVersionMasNuevaException extends RuntimeException {

    private final String versionDeLaBase;
    private final String versionDelPrograma;

    public BaseDeUnaVersionMasNuevaException(String versionDeLaBase, String versionDelPrograma) {
        super("Esta base de datos la uso una version mas nueva de DespensaFamily"
                + " (esquema " + versionDeLaBase + "; este programa llega hasta el "
                + versionDelPrograma + "). Para no danar los datos, esta version no arranca:"
                + " instala la ultima version de DespensaFamily.");
        this.versionDeLaBase = versionDeLaBase;
        this.versionDelPrograma = versionDelPrograma;
    }

    public String versionDeLaBase() {
        return versionDeLaBase;
    }

    public String versionDelPrograma() {
        return versionDelPrograma;
    }
}
