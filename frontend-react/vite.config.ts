import { existsSync, readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The Spring Boot API runs on 8095 (see backend-java/src/main/resources/application.yml).
const API_TARGET = process.env.PAMS_API_URL ?? 'http://127.0.0.1:8095'

// `npm run dev:lan` serves the app to other devices on the network over HTTPS: phone browsers only
// allow the camera (QR scanning) on secure pages. The certificate is a local self-signed one in .certs.
const LAN = process.env.PAMS_LAN === '1'
const CERT = '.certs/lan-cert.pem'
const KEY = '.certs/lan-key.pem'
const https = LAN && existsSync(CERT) && existsSync(KEY) ? { cert: readFileSync(CERT), key: readFileSync(KEY) } : undefined

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5190,
    strictPort: true,
    host: LAN ? '0.0.0.0' : undefined,
    https,
    proxy: {
      '/api': { target: API_TARGET, changeOrigin: false },
    },
  },
})
