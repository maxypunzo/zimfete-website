import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests against the real backend in demo mode:
 *   (cd ../asset-financing && mvn spring-boot:run -Dspring-boot.run.profiles=demo)
 *   npm run build && npx vite preview --port 4173
 *   npx playwright test
 */
export default defineConfig({
  testDir: 'e2e',
  timeout: 60_000,
  workers: 1,
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:4173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1360, height: 900 } } }],
})
