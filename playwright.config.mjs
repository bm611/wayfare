import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  use: {
    baseURL: 'http://127.0.0.1:5187',
    viewport: { width: 390, height: 844 },
    timezoneId: 'Europe/Amsterdam',
    launchOptions: process.env.PLAYWRIGHT_CHROME ? { channel: 'chrome' } : {},
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 5187 --strictPort',
    url: 'http://127.0.0.1:5187',
    env: { VITE_SUPABASE_URL: 'https://wayfare-test.supabase.co', VITE_SUPABASE_PUBLISHABLE_KEY: 'test-key' },
  },
});
