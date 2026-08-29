package com.guerram.DespensaFamily.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La carpeta de datos es lo que separa el programa (que se pisa en cada
 * instalación) de los datos del negocio (que tienen que sobrevivirla).
 * Estas reglas de resolución son la base de F0.
 */
class AppPathsTest {

    @Test
    void usaElOverrideCuandoEstaDefinido() {
        Path home = AppPaths.resolveHome("D:/DatosDespensa", "C:/Users/gustavo/AppData/Local", "C:/Users/gustavo");

        assertEquals(Paths.get("D:/DatosDespensa"), home);
    }

    @Test
    void enWindowsUsaLocalAppDataCuandoNoHayOverride() {
        Path home = AppPaths.resolveHome(null, "C:/Users/gustavo/AppData/Local", "C:/Users/gustavo");

        assertEquals(Paths.get("C:/Users/gustavo/AppData/Local/DespensaFamily"), home);
    }

    @Test
    void sinOverrideNiLocalAppDataCaeAlHomeDelUsuario() {
        Path home = AppPaths.resolveHome(null, null, "/home/martin");

        assertEquals(Paths.get("/home/martin/.despensafamily"), home);
    }

    @Test
    void ignoraElOverrideEnBlanco() {
        Path home = AppPaths.resolveHome("   ", "C:/Users/gustavo/AppData/Local", "C:/Users/gustavo");

        assertEquals(Paths.get("C:/Users/gustavo/AppData/Local/DespensaFamily"), home);
    }

    @Test
    void ignoraLocalAppDataEnBlanco() {
        Path home = AppPaths.resolveHome(null, "  ", "/home/martin");

        assertEquals(Paths.get("/home/martin/.despensafamily"), home);
    }

    @Test
    void lasSubcarpetasColganDelHome() {
        Path home = Paths.get("D:/DatosDespensa");

        assertEquals(Paths.get("D:/DatosDespensa/db"), AppPaths.db(home));
        assertEquals(Paths.get("D:/DatosDespensa/imagenes"), AppPaths.imagenes(home));
        assertEquals(Paths.get("D:/DatosDespensa/backups"), AppPaths.backups(home));
        assertEquals(Paths.get("D:/DatosDespensa/logs"), AppPaths.logs(home));
    }
}
