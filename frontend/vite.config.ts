import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': new URL('./src', import.meta.url).pathname },
  },
  server: {
    // The backend owns the Codeforces mirror; the browser never talks to Codeforces directly.
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
})
