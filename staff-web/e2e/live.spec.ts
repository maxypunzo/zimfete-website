import { expect, request, test, type APIRequestContext, type Page } from '@playwright/test'

/**
 * Full lifecycle against a REAL Fineract (test server only: it creates real records there).
 * Needs Fineract prepared by ../asset-financing/deploy/setup-fineract.py --test-data --webhook-url ...
 * and the module running in live mode. Run with: LIVE=1 npx playwright test e2e/live.spec.ts
 */
const FINERACT = process.env.FINERACT_URL ?? 'http://localhost:8080/fineract-provider/api/v1'
const PASSWORD = process.env.STAFF_PASSWORD ?? 'Zimfete@Test2026'
const MEMBER = process.env.LIVE_MEMBER ?? 'Farai Ncube'

test.skip(!process.env.LIVE, 'set LIVE=1 to run against a real Fineract')

let fineract: APIRequestContext

test.beforeAll(async () => {
  fineract = await request.newContext({
    extraHTTPHeaders: {
      Authorization: 'Basic ' + Buffer.from(`${process.env.FINERACT_USER ?? 'mifos'}:${process.env.FINERACT_PASSWORD ?? 'password'}`).toString('base64'),
      'Fineract-Platform-TenantId': 'default',
    },
  })
})

async function fin(method: 'get' | 'post', path: string, data?: object) {
  const r = await fineract[method](FINERACT + path, { data })
  expect(r.ok(), `${method} ${path}: ${await r.text()}`).toBeTruthy()
  return r.json()
}

const today = () => new Date().toLocaleDateString('en-GB', { day: '2-digit', month: 'long', year: 'numeric' })

async function signIn(page: Page, user: string) {
  await page.goto('/')
  await page.getByLabel('Username').fill(user)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
}

async function signOut(page: Page) {
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
}

async function teller(savingsId: number, amount: number) {
  await fin('post', `/savingsaccounts/${savingsId}/transactions?command=deposit`, {
    transactionDate: today(), transactionAmount: amount, paymentTypeId: 1, locale: 'en', dateFormat: 'dd MMMM yyyy',
  })
}

test('real Fineract: application to ownership', async ({ page }) => {
  // 1. Officer (Fineract user af.officer, Marondera) opens an application.
  await signIn(page, 'af.officer')
  await page.getByRole('link', { name: 'New application' }).first().click()
  await page.getByLabel('Search members').fill(MEMBER.split(' ')[0])
  await page.getByRole('button', { name: new RegExp(MEMBER) }).click()
  if (!(await page.getByRole('button', { name: /Borehole/ }).count())) {
    test.fail(true, 'Add a "Borehole" asset to the catalogue first (admin)')
  }
  await page.getByRole('button', { name: /Borehole/ }).first().click()
  await page.getByRole('button', { name: new RegExp(`Open application for ${MEMBER}`) }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('Saving')
  const account = Number((await page.getByText(/^#\d+$/).first().textContent())!.slice(1))
  const ref = (await page.locator('.page-header p').textContent())!.split(' · ')[0]

  // Fineract: account is active, opening fee attached and outstanding.
  let sa = await fin('get', `/savingsaccounts/${account}?associations=charges`)
  expect(sa.status.active).toBe(true)
  expect(sa.externalId).toBe(ref)
  expect(sa.charges[0].amountOutstanding).toBe(10)

  // 2. Teller deposits in Fineract -> webhook -> module collects the fee and updates progress.
  await teller(account, 1200)
  await expect.poll(async () => {
    await page.reload()
    return page.locator('.progress-text').textContent()
  }, { timeout: 20_000 }).toContain('US$1,190.00')
  sa = await fin('get', `/savingsaccounts/${account}?associations=charges`)
  expect(sa.charges[0].amountOutstanding).toBe(0)
  expect(sa.summary.accountBalance).toBe(1190)

  await teller(account, 900)
  await expect.poll(async () => {
    await page.reload()
    return page.locator('.page-header .badge').textContent()
  }, { timeout: 20_000 }).toBe('In queue')

  // 3. Purchase order.
  await page.getByRole('button', { name: 'Raise purchase order' }).click()
  await page.getByLabel('Supplier', { exact: true }).selectOption({ index: 1 })
  const reason = page.getByLabel('Reason for serving out of queue order')
  if (await reason.count()) await reason.fill('Live test')
  await page.getByRole('button', { name: 'Raise order' }).click()
  await expect(page.getByText('Awaiting approval')).toBeVisible()
  const url = page.url()
  await signOut(page)

  // 4. Manager approves.
  await signIn(page, 'af.manager')
  await page.goto(url)
  await page.getByRole('button', { name: 'Approve' }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('Being procured')
  await signOut(page)

  // 5. Officer records delivery.
  await signIn(page, 'af.officer')
  await page.goto(url)
  await page.getByRole('button', { name: 'Record delivery' }).click()
  await page.getByLabel('Latitude').fill('-18.1853')
  await page.getByLabel('Longitude').fill('31.5519')
  await page.getByLabel(/The member has received the asset/).check()
  await page.getByRole('button', { name: 'Save delivery' }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('Delivered')
  await signOut(page)

  // 6. Manager converts: real withdrawal + loan in Fineract.
  await signIn(page, 'af.manager')
  await page.goto(url)
  await page.getByRole('button', { name: 'Convert to loan' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Convert to loan' }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('Repaying', { timeout: 30_000 })
  const loanId = Number((await page.getByText(/^#\d+$/).first().textContent())!.slice(1))

  const loan = await fin('get', `/loans/${loanId}`)
  expect(loan.status.active).toBe(true)
  expect(loan.principal).toBe(4000 - 2090)
  expect(loan.externalId).toBe(ref)
  expect(loan.fundId).toBeTruthy()
  sa = await fin('get', `/savingsaccounts/${account}?associations=transactions`)
  expect(sa.summary.accountBalance).toBe(0)
  expect(sa.transactions.find((t: { transactionType: { withdrawal: boolean } }) => t.transactionType.withdrawal).amount)
    .toBe(2090)

  // 7. Member repays the loan in full in Fineract -> webhook -> paid off, ownership transferred.
  const payoff = await fin('get', `/loans/${loanId}/transactions/template?command=prepayLoan`)
  await fin('post', `/loans/${loanId}/transactions?command=repayment`, {
    transactionDate: today(), transactionAmount: payoff.amount, paymentTypeId: 1, locale: 'en', dateFormat: 'dd MMMM yyyy',
  })
  await expect.poll(async () => {
    await page.reload()
    return page.locator('.page-header .badge').textContent()
  }, { timeout: 20_000 }).toBe('Paid off')
  await page.getByRole('link', { name: 'Open asset record' }).click()
  await expect(page.locator('.page-header .badge')).toHaveText('Member owned')
})
