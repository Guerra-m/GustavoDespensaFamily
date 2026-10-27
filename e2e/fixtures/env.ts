// Constantes compartidas entre global-setup y los specs.
//
// El puerto 18106 es fijo (no 8080, que puede estar ocupado por una
// instancia real corriendo en esta PC, y no 5173, que es Fondo Familiar en
// esta máquina — ver CLAUDE.md). El backend arranca con una base H2 nueva en
// una carpeta temporal (DESPENSA_HOME) para cada corrida de `npm run e2e`.

export const PORT = 18106;
export const BASE_URL = `http://localhost:${PORT}`;

// El admin inicial nace con una contraseña aleatoria (ver DataInitializer) y
// `debeCambiarPassword: true`. global-setup la lee de
// credenciales-iniciales.txt y la cambia por esta ANTES de que corran los
// tests, así cada spec puede loguearse con una contraseña conocida sin volver
// a leer el archivo.
export const ADMIN_USERNAME = "admin";
export const ADMIN_PASSWORD = "AdminE2E-2026!";
