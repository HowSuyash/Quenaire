/**
 * Drives the running app in a real browser and captures each screen.
 *
 * Uses the system Chrome (`channel: 'chrome'`) rather than a downloaded
 * Playwright build, so it works without the ~150MB browser bundle.
 *
 * Usage:  node scripts/capture-screens.mjs [outDir]
 * Needs:  next dev on :3000 (and the API on :8080 for live data;
 *         without it the client falls back to its built-in fixtures).
 */
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

const BASE = process.env.BASE_URL ?? 'http://localhost:3000';
const OUT = process.argv[2] ?? 'screenshots';

const SCREENS = [
  { name: '01-landing', path: '/', auth: false },
  { name: '02-login', path: '/login', auth: false },
  { name: '03-dashboard', path: '/dashboard', auth: true },
  { name: '04-review-queue', path: '/dashboard/review-queue', auth: true },
  { name: '05-analytics', path: '/dashboard/analytics', auth: true },
  { name: '06-program', path: '/dashboard/program', auth: true },
  { name: '07-branding', path: '/dashboard/branding', auth: true },
  { name: '08-questionnaires', path: '/dashboard/questionnaires', auth: true },
  { name: '09-documents', path: '/dashboard/documents', auth: true },
  { name: '10-admin-portal', path: '/admin', auth: true },
  { name: '11-vendor-intake', path: '/intake/demo-token-123', auth: false },
];

/** The login form falls back to a demo session for any credentials. */
async function signIn(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await page.fill('input[type="email"]', 'alex.chen@acmesecurity.com');
  await page.fill('input[type="password"]', 'demo');
  await Promise.all([
    page.waitForURL('**/dashboard**', { timeout: 20000 }).catch(() => {}),
    page.click('button[type="submit"]'),
  ]);
  await page.waitForTimeout(1500);
}

const main = async () => {
  mkdirSync(OUT, { recursive: true });

  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
  });
  const page = await context.newPage();

  const errors = [];
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text().slice(0, 160));
  });
  page.on('pageerror', (e) => errors.push(`PAGEERROR: ${e.message.slice(0, 160)}`));

  await signIn(page);

  for (const screen of SCREENS) {
    try {
      await page.goto(`${BASE}${screen.path}`, {
        waitUntil: 'networkidle',
        timeout: 30000,
      });
      // Charts and SSE-backed lists settle a beat after networkidle.
      await page.waitForTimeout(2000);

      const file = join(OUT, `${screen.name}.png`);
      await page.screenshot({ path: file, fullPage: true });

      const title = await page.title();
      const landed = new URL(page.url()).pathname;
      const redirected = landed !== screen.path ? `  (redirected -> ${landed})` : '';
      console.log(`OK   ${screen.name.padEnd(22)} ${screen.path.padEnd(34)} "${title}"${redirected}`);
    } catch (e) {
      console.log(`FAIL ${screen.name.padEnd(22)} ${screen.path.padEnd(34)} ${e.message.split('\n')[0]}`);
    }
  }

  await browser.close();

  if (errors.length) {
    console.log('\n--- console errors seen ---');
    [...new Set(errors)].slice(0, 15).forEach((e) => console.log('  ' + e));
  } else {
    console.log('\nno console errors');
  }
};

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
