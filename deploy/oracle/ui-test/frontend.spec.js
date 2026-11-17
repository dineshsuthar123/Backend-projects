const { test, expect } = require('@playwright/test');

test('demo creation, alias, TTL, analytics and responsive layout', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('#createButton')).toBeEnabled();
  await expect(page.locator('#creationPolicy')).toContainText('Public demo');
  const alias = `ui_${Date.now()}`;
  await page.locator('#originalUrl').fill('https://example.com/ui');
  await page.locator('#customAlias').fill(alias);
  await page.locator('#ttlSeconds').fill('60');
  await page.locator('#createButton').click();
  await expect(page.locator('#createResult a')).toContainText(alias);
  await expect(page.locator('#shortCode')).toHaveValue(alias);
  const redirect = await page.request.get(`/${alias}`, { maxRedirects: 0 });
  expect(redirect.status()).toBe(302);
  expect(redirect.headers().location).toBe('https://example.com/ui');
  await page.locator('#statsButton').click();
  await expect(page.locator('#eventCount')).toHaveText('1');
  await expect(page.locator('#cacheState')).toHaveText('Warm');
  await expect(page.locator('#burstButton, #evictButton')).toHaveCount(0);
  for (const width of [390, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  }
});

test('untrusted success and error strings render only as text', async ({ page }) => {
  await page.route('**/api/v1/demo/urls', route => route.fulfill({ status: 201,
    contentType: 'application/json', body: JSON.stringify({ shortCode: 'safe1234',
      shortUrl: '<img src=x onerror="window.injected=1">', originalUrl: '<script>window.injected=2</script>' }) }));
  await page.goto('/');
  await expect(page.locator('#createButton')).toBeEnabled();
  await page.locator('#createButton').click();
  await expect(page.locator('#createResult')).toContainText('<script>');
  await expect(page.locator('#createResult img, #createResult script')).toHaveCount(0);
  expect(await page.evaluate(() => window.injected)).toBeUndefined();
  await page.unroute('**/api/v1/demo/urls');
  await page.route('**/api/v1/demo/urls', route => route.fulfill({ status: 400,
    contentType: 'application/json', body: JSON.stringify({ message: '<img src=x onerror="window.injected=3">' }) }));
  await page.locator('#createButton').click();
  await expect(page.locator('#createResult')).toContainText('<img');
  await expect(page.locator('#eventLog img, #createResult img')).toHaveCount(0);
});
