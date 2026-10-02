/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In development the API runs on :8090; Vite forwards /api to it so the browser sees one origin
// (needed for the session cookie). In production Nginx does the same.
export default defineConfig({
  plugins: [react()],
  // Not "assets": that is also the Asset register's address (/assets/5), which must load the app.
  build: { assetsDir: 'static' },
  server: {
    proxy: { '/api': 'http://localhost:8090' },
  },
  preview: {
    proxy: { '/api': 'http://localhost:8090' },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
