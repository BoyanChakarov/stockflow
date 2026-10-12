import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";
import assert from "node:assert/strict";
const secrets = {
  admin: process.env.STOCKFLOW_ADMIN_PASSWORD,
  staff: process.env.STOCKFLOW_STAFF_PASSWORD,
};
if (!secrets.admin || !secrets.staff)
  throw Error("Set demo account passwords in the environment.");
const browser = await chromium.launch({
  headless: true,
  ...(process.env.STOCKFLOW_BROWSER_PATH
    ? { executablePath: process.env.STOCKFLOW_BROWSER_PATH }
    : {}),
});
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
page.on("pageerror", (error) => errors.push(error.message));
const screenshots = path.resolve("target/browser-screenshots");
fs.mkdirSync(screenshots, { recursive: true });
const product = "10000000-0000-4000-8000-000000000001";
const customer = "20000000-0000-4000-8000-000000000001";
async function login(name) {
  await page.goto("http://localhost:8080/login");
  await page.getByLabel("Username", { exact: true }).fill(name);
  await page.getByLabel("Password", { exact: true }).fill(secrets[name]);
  await page.getByRole("button", { name: "Sign in →" }).click();
  await page.waitForURL("http://localhost:8080/");
}
async function get(url) {
  return page.evaluate(async (url) => {
    const r = await fetch(url);
    return { status: r.status, body: await r.json() };
  }, url);
}
async function balance(onHand, reserved, available) {
  const { body: p } = await get("/api/v1/products/" + product);
  assert.deepEqual(
    [p.onHand, p.reserved, p.available],
    [onHand, reserved, available],
  );
}
async function prepareOrder(q) {
  await page.goto("http://localhost:8080/orders/new");
  await page.locator("#customer-select").selectOption(customer);
  await page.locator("#product-select").selectOption(product);
  await page.getByRole("button", { name: "Add product" }).click();
  await page
    .getByRole("spinbutton", { name: "Quantity for Blue ceramic mug" })
    .fill(String(q));
}
async function confirmOrder() {
  await page.getByRole("button", { name: "Confirm & reserve stock" }).click();
  await page.waitForURL(/\/orders\/[a-f0-9-]+\?saved=true$/);
  return page.url().split("/orders/")[1].split("?")[0];
}
try {
  await login("admin");
  await page.goto("http://localhost:8080/products/" + product);
  const receipt = page.locator('form[data-kind="receipt"]');
  await receipt.getByLabel("Quantity", { exact: true }).fill("10");
  await receipt
    .getByLabel("Reason / reference")
    .fill("Browser acceptance opening stock");
  await receipt.getByRole("button", { name: "Receive stock" }).click();
  await page.waitForURL(/saved=true/);
  await balance(10, 0, 10);
  await page.locator('.profile button[aria-label="Sign out"]').click();
  await login("staff");
  assert.equal(
    await page
      .getByRole("link", { name: "Stock history", exact: false })
      .count(),
    0,
  );
  assert.equal((await get("/api/v1/inventory/movements")).status, 403);
  await prepareOrder(3);
  let intercepted = false;
  await page.route("**/api/v1/orders", async (route) => {
    if (route.request().method() === "POST" && !intercepted) {
      intercepted = true;
      const response = await route.fetch();
      assert.equal(response.status(), 201);
      await route.abort("failed");
    } else await route.continue();
  });
  await page.getByRole("button", { name: "Confirm & reserve stock" }).click();
  await page
    .locator("#feedback")
    .filter({ hasText: "Connection interrupted" })
    .waitFor();
  await balance(10, 3, 7);
  await page.unroute("**/api/v1/orders");
  await page.reload();
  await page
    .getByRole("spinbutton", { name: "Quantity for Blue ceramic mug" })
    .waitFor();
  assert.equal(
    await page
      .getByRole("spinbutton", { name: "Quantity for Blue ceramic mug" })
      .inputValue(),
    "3",
  );
  const order = await confirmOrder();
  await balance(10, 3, 7);
  assert.equal((await get("/api/v1/orders")).body.totalElements, 1);
  await page.screenshot({
    path: path.join(screenshots, "order-confirmed.png"),
    fullPage: true,
  });
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Fulfil order", exact: true }).click();
  await page.waitForLoadState("networkidle");
  await page.getByText("This order is fulfilled.", { exact: false }).waitFor();
  await balance(7, 0, 7);
  await prepareOrder(2);
  await confirmOrder();
  await page
    .getByLabel("Cancellation reason")
    .fill("Customer cancelled before collection");
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Cancel order", exact: true }).click();
  await page.waitForLoadState("networkidle");
  await page.getByText("This order is cancelled.", { exact: false }).waitFor();
  await balance(7, 0, 7);
  await prepareOrder(8);
  await page.getByRole("button", { name: "Confirm & reserve stock" }).click();
  await page
    .locator("#feedback")
    .filter({ hasText: "exceed available stock" })
    .waitFor();
  assert.equal(
    await page
      .getByRole("spinbutton", { name: "Quantity for Blue ceramic mug" })
      .inputValue(),
    "8",
  );
  await balance(7, 0, 7);
  await page.screenshot({
    path: path.join(screenshots, "stock-conflict.png"),
    fullPage: true,
  });
  await page.goto("http://localhost:8080/customers/new");
  await page
    .getByLabel("Customer name", { exact: true })
    .fill("Browser Demo Customer");
  await page.getByLabel("Email", { exact: true }).fill("browser@example.com");
  await page.getByRole("button", { name: "Save customer" }).click();
  await page.waitForURL(/customers\?saved=true/);
  await page
    .getByRole("link", { name: "Browser Demo Customer", exact: true })
    .click();
  await page.getByLabel("Notes").fill("Updated from staff browser");
  await page.getByRole("button", { name: "Save customer" }).click();
  await page.waitForURL(/customers\?saved=true/);
  await page.goto("http://localhost:8080/products");
  await page.screenshot({
    path: path.join(screenshots, "products.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("http://localhost:8080/");
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  );
  await page.screenshot({
    path: path.join(screenshots, "mobile-dashboard.png"),
    fullPage: true,
  });
  assert.deepEqual(errors, []);
  fs.writeFileSync(
    "target/browser-result.json",
    JSON.stringify(
      {
        passed: true,
        order,
        checks: [
          "admin receipt",
          "staff permissions",
          "lost response retry after reload",
          "reserve 10/3/7",
          "fulfil 7/0/7",
          "cancel 7/0/7",
          "insufficient stock preserves form",
          "staff customer create/edit",
          "390px viewport",
        ],
        consoleErrors: errors,
      },
      null,
      2,
    ),
  );
  console.log(
    "Browser workflow passed: login, receipt, staff permissions, lost-response retry, fulfil, cancel, shortage, customer editing, mobile layout.",
  );
} finally {
  await browser.close();
}
