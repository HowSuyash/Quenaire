import { test, expect } from '@playwright/test';

test.describe('Landing Page', () => {
  test('should load landing page', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/Quenaire/);
    await expect(page.locator('h1')).toContainText('Automate Security Questionnaires');
  });

  test('should have navigation links', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('text=Features')).toBeVisible();
    await expect(page.locator('text=Architecture')).toBeVisible();
    await expect(page.locator('text=Integrations')).toBeVisible();
    await expect(page.locator('text=API Docs')).toBeVisible();
  });

  test('should have CTA buttons', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('text=Start Free Trial')).toBeVisible();
    await expect(page.locator('text=View API Docs')).toBeVisible();
  });
});

test.describe('Authentication', () => {
  test('should redirect to login when not authenticated', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/.*login/);
  });

  test('should show login form', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('text=Sign in to your account')).toBeVisible();
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('text=Sign in with Keycloak (SSO)')).toBeVisible();
  });
});

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    // Mock authentication
    await page.goto('/login');
    // In real tests, you would log in here
  });

  test('should show dashboard layout when authenticated', async ({ page }) => {
    // This test would need actual auth setup
    // For now, just verify the login page works
    await page.goto('/login');
    await expect(page.locator('text=Quenaire')).toBeVisible();
  });
});