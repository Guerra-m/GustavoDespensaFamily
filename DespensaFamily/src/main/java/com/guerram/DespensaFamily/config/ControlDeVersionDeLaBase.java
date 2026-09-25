package com.guerram.DespensaFamily.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Antes de migrar, se fija que la base no la haya migrado una version MAS NUEVA
 * del programa (DF-INST-002).
 *
 * <p>Flyway, por defecto, ignora las migraciones "futuras"
 * ({@code ignoreMigrationPatterns=*:future}): si la base tiene una V15 que este
 * jar no trae, sigue como si nada. Eso es lo que permitio que la 1.0.0, abierta
 * con un doble clic sobre una base de la 1.1.0, bajara fecha_hora a DATE. Se
 * controla aca, explicitamente, y no cambiando esa propiedad: asi el motivo
 * sale con un mensaje que el usuario entiende y no como un error de validacion
 * de Flyway.
 */
@Component
public class ControlDeVersionDeLaBase implements FlywayMigrationStrategy {

    private static final Set<MigrationState> FUTURAS =
            EnumSet.of(MigrationState.FUTURE_SUCCESS, MigrationState.FUTURE_FAILED);

    @Override
    public void migrate(Flyway flyway) {
        MigrationInfoService info = flyway.info();

        Optional<MigrationInfo> masNueva = Arrays.stream(info.all())
                .filter(m -> FUTURAS.contains(m.getState()))
                .filter(m -> m.getVersion() != null)
                .max(Comparator.comparing(MigrationInfo::getVersion));

        if (masNueva.isPresent()) {
            throw new BaseDeUnaVersionMasNuevaException(
                    masNueva.get().getVersion().getVersion(), ultimaConocida(info));
        }

        flyway.migrate();
    }

    private static String ultimaConocida(MigrationInfoService info) {
        return Arrays.stream(info.all())
                .filter(m -> !FUTURAS.contains(m.getState()))
                .filter(m -> m.getVersion() != null)
                .map(MigrationInfo::getVersion)
                .max(Comparator.naturalOrder())
                .map(v -> v.getVersion())
                .orElse("?");
    }
}
