import { test, expect } from '@playwright/test';

async function capture(page, options) {
  await page.evaluate(() => document.fonts.ready);
  // Motion springs and CountUp use JS animation, not CSS animations.
  await page.waitForTimeout(1200);
  await page.screenshot(options);
}

const user = { id: 'traveller-you', aud: 'authenticated', role: 'authenticated', email: 'traveller@example.test', user_metadata: {}, app_metadata: {}, created_at: '2026-01-01T00:00:00Z' };
const session = { access_token: `${Buffer.from('{"alg":"HS256"}').toString('base64url')}.${Buffer.from(JSON.stringify({ sub: user.id, exp: 4102444800 })).toString('base64url')}.test`, refresh_token: 'test-refresh', expires_at: 4102444800, expires_in: 3600, token_type: 'bearer', user };
const trip = { id: 'lisbon', user_id: user.id, name: 'A week in Lisbon', destination: 'Lisbon, Portugal', start_date: '2026-09-03', end_date: '2026-09-09', budget: 1500, currency: 'EUR', accent: 'clay', share_code: 'BCDF2345', cover_status: 'ready', cover_path: null, created_at: '2026-09-01T00:00:00Z' };
const expenses = [
  { id: 'flight', title: 'Flights to Lisbon', amount: 200, category: 'flights', user_id: user.id, spent_on: '2026-09-01', note: null },
  { id: 'hotel', title: 'Hotel deposit', amount: 50, category: 'stays', user_id: user.id, spent_on: '2026-09-01', note: null },
  { id: 'museum', title: 'Museum tickets', amount: 30, category: 'activities', user_id: 'traveller-maya', spent_on: '2026-09-04', note: null },
  { id: 'souvenirs', title: 'Souvenirs', amount: 20, category: 'shopping', user_id: 'traveller-maya', spent_on: '2026-09-04', note: null },
  { id: 'dinner', title: 'Dinner by the river', amount: 50, category: 'food', user_id: 'traveller-maya', spent_on: '2026-09-05', note: 'Booked a table outside' },
  { id: 'tram', title: 'Tram tickets', amount: 10, category: 'transport', user_id: user.id, spent_on: '2026-09-05', note: null },
].map((expense) => ({ ...expense, trip_id: trip.id, original_currency: null, original_amount: null, fx_rate: null, created_at: '2026-09-05T10:00:00Z' }));

async function mockApp(page, options = {}) {
  await page.clock.setFixedTime(new Date('2026-09-05T12:00:00+02:00'));
  if (options.signedIn !== false) await page.addInitScript((value) => localStorage.setItem('sb-wayfare-test-auth-token', JSON.stringify(value)), session);
  const requests = [];
  let rows = expenses.map((e) => ({ ...e }));
  const currentTrip = { ...trip, ...options.trip };
  await page.route('https://wayfare-test.supabase.co/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const body = request.postDataJSON();
    requests.push({ path: url.pathname, method: request.method(), body, url: request.url() });
    let data = {};
    if (url.pathname.endsWith('/trips')) {
      if (request.method() === 'PATCH') Object.assign(currentTrip, body);
      data = url.searchParams.has('id') ? currentTrip : [currentTrip,
        { ...trip, id: 'upcoming', name: 'Autumn in Kyoto', start_date: '2026-10-01', end_date: '2026-10-10' },
        { ...trip, id: 'past', name: 'Summer in Rome', start_date: '2026-07-01', end_date: '2026-07-07' },
        { ...trip, id: 'open', name: 'Next adventure', start_date: null, end_date: null },
      ];
    } else if (url.pathname.endsWith('/expenses')) {
      if (request.method() === 'POST') {
        if (options.saveDelay) await new Promise((resolve) => setTimeout(resolve, options.saveDelay));
        data = { ...body, id: `new-${rows.length}`, created_at: '2026-09-05T12:00:00Z' };
        rows.push(data);
      } else if (request.method() === 'PATCH') {
        const id = url.searchParams.get('id').replace('eq.', '');
        rows = rows.map((row) => row.id === id ? { ...row, ...body } : row);
        data = rows.find((row) => row.id === id);
      } else data = rows;
    } else if (url.pathname.endsWith('/trip_members')) data = [
      { trip_id: trip.id, user_id: user.id, role: 'owner', joined_at: '2026-09-01' },
      { trip_id: trip.id, user_id: 'traveller-maya', role: 'member', joined_at: '2026-09-02' },
    ];
    else if (url.pathname.endsWith('/profiles')) data = [{ id: user.id, display_name: 'Alex' }, { id: 'traveller-maya', display_name: 'Maya' }];
    else if (url.pathname.endsWith('/user')) data = user;
    else if (url.pathname.endsWith('/token')) data = session;
    else if (url.pathname.endsWith('/signup')) data = { user, session: null };
    else if (url.pathname.endsWith('/join_trip')) data = trip.id;
    await route.fulfill({ json: data });
  });
  await page.route('**/*frankfurter*/**', (route) => route.abort());
  await page.route('**/.netlify/**', (route) => route.fulfill({ status: 202, body: '' }));
  return requests;
}

test('budget, grouped trips, ledger search and filters', async ({ page }, testInfo) => {
  await mockApp(page);
  await page.goto('/');
  for (const name of ['Active trips', 'Upcoming trips', 'Past trips', 'Dates open']) await expect(page.getByRole('heading', { name, exact: false })).toBeVisible();
  await capture(page, { path: testInfo.outputPath('trips.png'), fullPage: true });
  await page.getByRole('link').filter({ hasText: trip.name }).click();
  await expect(page.getByText('Budget remaining', { exact: true })).toBeVisible();
  await expect(page.getByText('€228.00')).toBeVisible();
  await expect(page.getByText('Group budget', { exact: false })).toBeVisible();
  await expect(page.getByRole('meter', { name: 'Budget used' })).toHaveAttribute('aria-valuetext', '24% of budget used');
  await expect(page.getByLabel('Search expenses')).toHaveCount(0);
  await expect(page.getByLabel('Category', { exact: true })).toHaveCount(0);
  await capture(page, { path: testInfo.outputPath('trip-mobile.png'), fullPage: true });
  await page.locator('#ledger').scrollIntoViewIfNeeded();
  await capture(page, { path: testInfo.outputPath('ledger-collapsed.png') });
  await page.getByRole('button', { name: 'Show search' }).click();
  await page.getByLabel('Search expenses').fill('outside');
  await expect(page.getByRole('status')).toHaveText('1 matching expense');
  await expect(page.getByRole('button', { name: /Dinner by the river/ })).toBeVisible();
  await page.getByRole('button', { name: 'Show filters' }).click();
  await page.getByLabel('Category', { exact: true }).selectOption('transport');
  await expect(page.getByText('No expenses match.', { exact: false })).toBeVisible();
  await capture(page, { path: testInfo.outputPath('no-results.png'), fullPage: true });
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await page.getByText('Spending by category', { exact: true }).click();
  await page.getByRole('button', { name: /Food/ }).click();
  await expect(page.getByLabel('Category', { exact: true })).toHaveValue('food');
  await page.getByLabel('Paid by', { exact: true }).selectOption(user.id);
  await expect(page.getByRole('status')).toHaveText('0 matching expenses');
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await page.evaluate(() => window.scrollTo(0, 0));
  await capture(page, { path: testInfo.outputPath('trip-desktop.png'), fullPage: true });
});

test('fast entry, currency preference, focus, discard and busy protection', async ({ page }, testInfo) => {
  const requests = await mockApp(page, { saveDelay: 700 });
  await page.goto('/trip/lisbon');
  const add = page.getByRole('button', { name: 'Add expense', exact: true }).filter({ visible: true });
  await add.click();
  const dialog = page.getByRole('dialog');
  await expect(page.getByLabel('Amount', { exact: true })).toBeFocused();
  await expect(page.getByLabel('Note', { exact: true })).toHaveCount(0);
  await expect(page.locator('main > header')).toHaveAttribute('inert', '');
  await page.getByRole('button', { name: 'Close', exact: true }).focus();
  await page.keyboard.press('Shift+Tab');
  await expect(page.getByRole('button', { name: 'Add to the ledger' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Close', exact: true })).toBeFocused();
  await page.getByLabel('Amount', { exact: true }).fill('12');
  await page.getByLabel('For', { exact: true }).fill('Airport coffee');
  await page.getByLabel('Paid in').selectOption('USD');
  await page.keyboard.press('Escape');
  await expect(page.getByText('Discard your changes?')).toBeVisible();
  await capture(page, { path: testInfo.outputPath('discard.png') });
  await page.getByRole('button', { name: 'Keep editing' }).click();
  await expect(page.getByLabel('Amount', { exact: true })).toHaveValue('12');
  await page.getByRole('button', { name: /More details/ }).click();
  await page.getByLabel('Note', { exact: true }).fill('Before boarding');
  await page.getByLabel('Note', { exact: true }).scrollIntoViewIfNeeded();
  const noteBox = await page.getByLabel('Note', { exact: true }).boundingBox();
  const saveBox = await page.getByRole('button', { name: 'Add to the ledger' }).boundingBox();
  expect(noteBox.y + noteBox.height).toBeLessThanOrEqual(saveBox.y);
  await capture(page, { path: testInfo.outputPath('expense.png') });
  await page.getByRole('button', { name: 'Add to the ledger' }).click();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveCount(0);
  await expect(add).toBeFocused();
  expect(requests.filter((r) => r.path.endsWith('/expenses') && r.method === 'POST')).toHaveLength(1);
  await add.click();
  await expect(page.getByLabel('Paid in')).toHaveValue('USD');
  await page.getByLabel('Amount', { exact: true }).fill('5');
  await page.getByRole('button', { name: 'Close', exact: true }).click();
  await page.getByRole('button', { name: 'Discard', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await add.click();
  await expect(page.getByLabel('Amount', { exact: true })).toHaveValue('');
  await page.setViewportSize({ width: 390, height: 460 });
  await expect(page.getByRole('button', { name: 'Add to the ledger' })).toBeInViewport();
  await capture(page, { path: testInfo.outputPath('short-viewport.png') });
  const scrollArea = dialog.locator('.overflow-y-auto');
  const contentBox = await scrollArea.boundingBox();
  const footerBox = await dialog.locator('footer').boundingBox();
  expect(contentBox.y + contentBox.height).toBeLessThanOrEqual(footerBox.y + 1);
  await page.getByRole('button', { name: /More details/ }).scrollIntoViewIfNeeded();
  await expect(page.getByRole('button', { name: /More details/ })).toBeInViewport();
  await expect(page.getByRole('button', { name: 'Add to the ledger' })).toBeInViewport();
  await capture(page, { path: testInfo.outputPath('short-scrolled.png') });
});

test('other travellers entries are read-only and own entries remain editable', async ({ page }, testInfo) => {
  await mockApp(page);
  await page.goto('/trip/lisbon');
  await page.getByRole('button', { name: /Dinner by the river/ }).click();
  await expect(page.getByRole('dialog')).toContainText('Paid by Maya');
  await expect(page.getByRole('button', { name: 'Delete entry' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Save changes' })).toHaveCount(0);
  await capture(page, { path: testInfo.outputPath('read-only.png') });
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: /^Tram tickets You/ }).click();
  await expect(page.getByRole('button', { name: 'Save changes' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete entry' })).toBeVisible();
});

test('trip drafts are protected and narrow layouts do not overflow', async ({ page }, testInfo) => {
  await mockApp(page);
  await page.setViewportSize({ width: 320, height: 740 });
  await page.goto('/');
  await page.getByRole('button', { name: 'New trip' }).click();
  await expect(page.getByLabel('Trip name')).toBeFocused();
  await page.getByLabel('Trip name').fill('Weekend away');
  await page.keyboard.press('Escape');
  await expect(page.getByText('Discard your changes?')).toBeVisible();
  await page.getByRole('button', { name: 'Discard', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'New trip' })).toBeFocused();
  await page.getByRole('button', { name: 'New trip' }).click();
  await expect(page.getByLabel('Trip name')).toHaveValue('');
  await capture(page, { path: testInfo.outputPath('trip-sheet.png') });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('over-budget, zero-budget and last-day guidance', async ({ page }, testInfo) => {
  await mockApp(page, { trip: { budget: 300, end_date: '2026-09-05' } });
  await page.goto('/trip/lisbon');
  await expect(page.getByText('€0.00 available/day remaining')).toBeVisible();
  await expect(page.getByText('Across 1 day, including today.', { exact: false })).toBeVisible();
  await expect(page.getByText('over by €60.00', { exact: false })).toBeVisible();
  await expect(page.getByRole('meter', { name: 'Budget used' })).toHaveAttribute('aria-valuetext', '120% of budget used');
  await capture(page, { path: testInfo.outputPath('over-budget.png'), fullPage: true });
  await page.getByRole('button', { name: 'Edit trip details' }).click();
  await page.getByLabel('Budget', { exact: true }).fill('0');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('No budget set', { exact: false })).toBeVisible();
  await expect(page.getByText('available/day remaining', { exact: false })).toHaveCount(0);
  await expect(page.getByRole('meter', { name: 'Budget used' })).toHaveCount(0);
});

test('password visibility and reset request with cooldown', async ({ page }, testInfo) => {
  const requests = await mockApp(page, { signedIn: false });
  await page.goto('/auth');
  await page.getByRole('button', { name: 'Show password' }).click();
  await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('type', 'text');
  await page.getByRole('button', { name: 'Hide password' }).click();
  await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('type', 'password');
  await page.getByRole('button', { name: 'Forgot password?' }).click();
  await page.getByLabel('Email').fill(user.email);
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByRole('status')).toContainText('If an account exists');
  await expect(page.getByRole('button', { name: /Send again in/ })).toBeDisabled();
  const request = requests.find((r) => r.path.endsWith('/recover'));
  expect(new URL(request.url).searchParams.get('redirect_to')).toBe('http://127.0.0.1:5187/auth?mode=recovery');
  await capture(page, { path: testInfo.outputPath('reset-request.png'), fullPage: true });
});

test('recovery session survives reload and completes password update', async ({ page }, testInfo) => {
  const requests = await mockApp(page);
  await page.goto('/auth?mode=recovery');
  await page.reload();
  await expect(page.getByLabel('New password', { exact: true })).toBeVisible();
  await capture(page, { path: testInfo.outputPath('new-password.png'), fullPage: true });
  await page.getByLabel('New password', { exact: true }).fill('new-test-password');
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page).toHaveURL('http://127.0.0.1:5187/');
  expect(requests.some((r) => r.path.endsWith('/user') && r.method === 'PUT' && r.body.password === 'new-test-password')).toBe(true);
});

test('expired recovery links offer a fresh reset', async ({ page }, testInfo) => {
  await mockApp(page, { signedIn: false });
  await page.goto('/auth?mode=recovery#error=access_denied&error_description=expired');
  await expect(page.getByText('This reset link is invalid', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Update password' })).toBeDisabled();
  await capture(page, { path: testInfo.outputPath('expired-link.png'), fullPage: true });
  await page.getByRole('button', { name: 'Request a new link' }).click();
  await expect(page.getByRole('button', { name: 'Send reset link' })).toBeVisible();
});

test('confirmation resend is throttled and reports success', async ({ page }, testInfo) => {
  test.setTimeout(90000);
  const requests = await mockApp(page, { signedIn: false });
  await page.goto('/auth');
  await page.getByRole('button', { name: 'Create an account' }).click();
  await page.getByLabel('Email').fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill('test-password');
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Check your inbox' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Resend in/ })).toBeDisabled();
  // Exercise the real cooldown: React schedules its effect through MessageChannel,
  // so advancing only browser timers does not reliably flush every countdown tick.
  await expect(page.getByRole('button', { name: 'Resend confirmation email' })).toBeEnabled({ timeout: 65000 });
  await page.getByRole('button', { name: 'Resend confirmation email' }).click();
  await expect(page.getByRole('status')).toHaveText('Confirmation email resent.');
  expect(requests.some((r) => r.path.endsWith('/resend') && r.body.type === 'signup')).toBe(true);
  await capture(page, { path: testInfo.outputPath('confirmation.png'), fullPage: true });
});
