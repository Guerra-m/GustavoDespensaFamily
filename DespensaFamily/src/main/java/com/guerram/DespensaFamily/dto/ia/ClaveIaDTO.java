package com.guerram.DespensaFamily.dto.ia;

/**
 * Pedido de PUT /configuracion/ia. Sin Lombok @Data/@ToString a proposito:
 * el toString por defecto expondria la clave en cualquier log que serialice
 * este objeto por error (SEC-05, canales de fuga de secretos).
 */
public class ClaveIaDTO {

    private String apiKey;

    public ClaveIaDTO() {
    }

    public ClaveIaDTO(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String toString() {
        return "ClaveIaDTO[apiKey=****]";
    }
}
