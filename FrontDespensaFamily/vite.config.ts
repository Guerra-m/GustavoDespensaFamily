import { defineConfig } from "vite";
import { resolve } from "path";
export default defineConfig({
  build: {
    rollupOptions: {
      input: {
        index: resolve(__dirname, 'index.html'),
        login: resolve(__dirname, 'src/pages/login/login.html'),
        home: resolve(__dirname, 'src/pages/home/home.html'),
      }
    }
  },
  server: {
    host: true,   
    port: 5173    
  }
})
