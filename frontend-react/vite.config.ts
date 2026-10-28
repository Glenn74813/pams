import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The Spring Boot API runs on 8095 (see backend-java/src/main/resources/application.yml).
const API_TARGET = process.env.PAMS_API_URL ?? 'http://127.0.0.1:8095'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5190,
    strictPort: true,
    proxy: {
      '/api': { target: API_TARGET, changeOrigin: false },
    },
  },
})
