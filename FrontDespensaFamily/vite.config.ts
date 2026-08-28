import { defineConfig } from "vite";
import { resolve } from "path";
export default defineConfig({
  build: {
    outDir: resolve(__dirname, '../DespensaFamily/src/main/resources/static'),
    emptyOutDir: true,
    rollupOptions: {
      input: {
        index: resolve(__dirname, 'index.html'),
        login: resolve(__dirname, 'src/pages/login/login.html'),
        home: resolve(__dirname, 'src/pages/home/home.html'),
        categorias: resolve(__dirname, 'src/pages/categorias/categorias.html'),
        compras: resolve(__dirname, 'src/pages/compras/compras.html'),
        perfil: resolve(__dirname, 'src/pages/perfil/perfil.html'),
        productos: resolve(__dirname, 'src/pages/productos/productos.html'),
        promos: resolve(__dirname, 'src/pages/promos/promos.html'),
        reportes: resolve(__dirname, 'src/pages/reportes/reportes.html'),
        usuarios: resolve(__dirname, 'src/pages/usuarios/usuarios.html'),
        ventas: resolve(__dirname, 'src/pages/ventas/ventas.html'),
      }
    }
  },
  server: {
    host: true,
    port: 5173
  }
})
