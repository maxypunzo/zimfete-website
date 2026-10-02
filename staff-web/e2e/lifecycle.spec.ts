import { expect, test, type Page } from '@playwright/test'

const SHOTS = process.env.SHOTS_DIR

async function shot(page: Page, name: string) {
  if (SHOTS) await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true })
}

async function signIn(page: Page, user: string) {
  await page.goto('/')
  await page.getByLabel('Username').fill(user)
  await page.getByLabel('Password').fill(user)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
}

async function signOut(page: Page) {
  const menu = page.getByRole('button', { name: 'Menu' })
  if (await menu.isVisible()) await menu.click()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
}

test('a member goes from application to owning a borehole', async ({ page }) => {
  // Served through nginx, the Content-Security-Policy must not block anything the app does.
  const blocked: string[] = []
  page.on('console', (m) => {
    if (m.type() === 'error' && /Content Security Policy/i.test(m.text())) blocked.push(m.text())
  })
  // Wrong password is refused without a browser pop-up.
  await page.goto('/')
  await shot(page, '01-login')
  await page.getByLabel('Username').fill('officer')
  await page.getByLabel('Password').fill('wrong')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toContainText('Wrong username or password')

  // 1. Marondera officer opens an application.
  await signIn(page, 'officer')
  await shot(page, '02-dashboard-officer')
  await page.getByRole('link', { name: 'New application' }).first().click()
  await page.getByLabel('Search members').fill('chipo')
  await page.getByRole('button', { name: /Chipo Mutasa/ }).click()
  await page.getByRole('button', { name: /Borehole, 40 m/ }).click()
  await expect(page.getByText('US$2,000.00').first()).toBeVisible()
  await shot(page, '03-new-application')
  await page.getByRole('button', { name: /Open application for Chipo Mutasa/ }).click()
  await expect(page.getByRole('heading', { name: 'Chipo Mutasa' })).toBeVisible()
  await expect(page.locator('.page-header .badge')).toHaveText('Saving')

  // 2. Deposits reach 50% → member joins the queue.
  await page.getByRole('button', { name: 'Demo: record deposit' }).click()
  await page.getByLabel('Amount (USD)').fill('1200')
  await page.getByRole('button', { name: 'Deposit', exact: true }).click()
  await expect(page.locator('.progress-text')).toContainText('US$1,200.00')
  const bar = await page.locator('.card .progress-fill').evaluate((e) => e.getBoundingClientRect().width)
  expect(bar).toBeGreaterThan(0)
  await shot(page, '04-application-saving')
  await page.getByRole('button', { name: 'Demo: record deposit' }).click()
  await page.getByLabel('Amount (USD)').fill('900')
  await page.getByRole('button', { name: 'Deposit', exact: true }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('In queue')

  // 3. Others qualified first, so serving Chipo now needs a reason.
  await page.getByRole('button', { name: 'Raise purchase order' }).click()
  await expect(page.getByRole('dialog').getByText(/number \d+ in the queue/)).toBeVisible()
  await page.getByLabel('Supplier', { exact: true }).selectOption({ label: 'Mashonaland Drilling (Pvt) Ltd' })
  await page.getByLabel('Reason for serving out of queue order').fill('Drilling rig already in the area this week')
  await shot(page, '05-raise-order')
  await page.getByRole('button', { name: 'Raise order' }).click()
  await expect(page.getByText('Awaiting approval')).toBeVisible()
  const url = page.url()
  await signOut(page)

  // 4. A manager approves (maker-checker).
  await signIn(page, 'manager')
  await shot(page, '06-dashboard-manager')
  await page.getByRole('link', { name: 'Purchase orders' }).click()
  await shot(page, '07-purchase-orders')
  await page.getByRole('row', { name: /Chipo Mutasa/ }).getByRole('button', { name: 'Approve' }).click()
  await expect(page.getByText(/approved/)).toBeVisible()
  await signOut(page)

  // 5. Officer records delivery at the site, with location and a photo.
  await signIn(page, 'officer')
  await page.goto(url)
  await expect(page.locator('.page-header .badge')).toHaveText('Being procured')
  await page.getByRole('button', { name: 'Record delivery' }).click()
  await page.getByLabel('Latitude').fill('-18.185300')
  await page.getByLabel('Longitude').fill('31.551900')
  await page.getByLabel('Serial number').fill('SP-11-7781')
  await page.getByLabel('Photos').setInputFiles({
    name: 'site.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
      'base64'),
  })
  await page.getByLabel(/The member has received the asset/).check()
  await shot(page, '08-delivery')
  await page.getByRole('button', { name: 'Save delivery' }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('Delivered')
  await expect(page.getByText('Waiting for a manager.')).toBeVisible()
  await signOut(page)

  // 6. Manager converts: deposit applied + loan for the rest.
  await signIn(page, 'manager')
  await page.goto(url)
  await page.getByRole('button', { name: 'Convert to loan' }).click()
  await shot(page, '09-convert')
  await page.getByRole('dialog').getByRole('button', { name: 'Convert to loan' }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('Repaying')
  await expect(page.getByText('US$2,100.00')).toBeVisible()
  await expect(page.getByText('US$1,900.00')).toBeVisible()
  await shot(page, '10-repaying')

  // 7. Loan repaid → paid off; ownership passes to the member.
  await page.getByRole('button', { name: 'Demo: repay loan in full' }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('Paid off')
  await page.getByRole('link', { name: 'Open asset record' }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('Member owned')
  await expect(page.locator('.photos img')).toHaveCount(1)
  await expect(page.getByText('SP-11-7781')).toBeVisible()
  await shot(page, '11-asset')

  await page.getByRole('link', { name: 'Queue & cash' }).click()
  await expect(page.getByRole('heading', { name: 'Queue & cash needed' })).toBeVisible()
  await shot(page, '12-queue')
  expect(blocked).toEqual([])
})

test('officers only see their own location and cannot approve', async ({ page }) => {
  await signIn(page, 'officer2') // Hwedza
  await page.getByRole('link', { name: 'Applications' }).click()
  await expect(page.getByRole('table')).toBeVisible()
  const offices = await page.locator('tbody td[data-label="Member"] .muted').allTextContents()
  expect(offices.length).toBeGreaterThan(0)
  for (const o of offices) expect(o).toContain('Hwedza')
  await expect(page.getByLabel('Location')).toHaveCount(0)

  await page.getByRole('link', { name: 'Catalogue' }).click()
  await expect(page.getByText('Only administrators can change the catalogue.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Add asset' })).toHaveCount(0)
})

test('works on a phone', async ({ browser }) => {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  await signIn(page, 'officer')
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
  await shot(page, '13-mobile-dashboard')
  await page.getByRole('button', { name: 'Menu' }).click()
  await page.getByRole('link', { name: 'Applications' }).click()
  await expect(page.getByRole('heading', { name: 'Applications' })).toBeVisible()
  const width = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(width).toBeLessThanOrEqual(390)
  await shot(page, '14-mobile-applications')
  await page.close()
})
