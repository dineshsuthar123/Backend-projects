const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({
  testDir: '.', workers: 1, retries: 0, reporter: 'line',
  use: { baseURL: process.env.TEST_BASE_URL || 'http://127.0.0.1:18080', headless: true },
});
