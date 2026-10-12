"use strict";
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const pendingKey = (form) =>
  `stockflow.pending:${form.dataset.endpoint}:${form.elements.productId?.value || ""}`;
const money = (cents) =>
  `€ ${(cents / 100n).toLocaleString("en-IE")}.${String(cents % 100n).padStart(2, "0")}`;
const cents = (price) => {
  const [whole, fraction = ""] = String(price).split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
};
const clearErrors = () => {
  $("#feedback").hidden = true;
  $$(".field-error").forEach((e) => (e.textContent = ""));
};
function showError(error, form) {
  const box = $("#feedback");
  box.textContent =
    error.detail || error.message || "The request could not be completed.";
  if (error.shortages)
    box.textContent +=
      "\n" +
      error.shortages
        .map(
          (s) =>
            `Product ${s.productId}: requested ${s.requested}, available ${s.available}.`,
        )
        .join("\n");
  if (error.requestId) box.textContent += `\nRequest ID: ${error.requestId}`;
  if (error.status === 401) {
    const link = document.createElement("a");
    link.href = "/login";
    link.textContent = " Sign in again";
    box.append(link);
  }
  (error.fieldErrors || []).forEach((e) => {
    const target = $$(".field-error", form || document).find(
      (el) => el.dataset.field === e.field,
    );
    if (target) target.textContent = e.message;
  });
  box.hidden = false;
  box.focus();
  box.scrollIntoView({ behavior: "smooth", block: "nearest" });
}
async function readJson(response) {
  if (
    response.redirected ||
    !(response.headers.get("content-type") || "").includes("json")
  )
    throw {
      status: 401,
      detail:
        "Your session has ended. Sign in again to continue. Your pending form has been kept.",
    };
  const body = await response.json();
  if (!response.ok) throw body;
  return body;
}
async function api(url, method = "GET", body, key) {
  const headers = { Accept: "application/json" };
  if (method !== "GET") {
    const csrf = await readJson(
      await fetch("/api/v1/csrf", { credentials: "same-origin" }),
    );
    headers[csrf.headerName] = csrf.token;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (key) headers["Idempotency-Key"] = key;
  }
  try {
    return await readJson(
      await fetch(url, {
        method,
        headers,
        credentials: "same-origin",
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      }),
    );
  } catch (error) {
    if (error instanceof TypeError)
      throw {
        detail:
          "Connection interrupted. Retry this unchanged form to safely retrieve or complete the same operation.",
      };
    throw error;
  }
}
function payload(form) {
  const values = Object.fromEntries(new FormData(form));
  const clean = (name) => values[name]?.trim() || null;
  switch (form.dataset.kind) {
    case "product":
      return {
        ...(form.dataset.method === "POST"
          ? { sku: values.sku.trim().toUpperCase() }
          : {}),
        name: values.name.trim(),
        description: clean("description"),
        unitPrice: values.unitPrice.trim(),
        reorderThreshold: Number(values.reorderThreshold),
      };
    case "customer":
      return {
        name: values.name.trim(),
        email: clean("email"),
        phone: clean("phone"),
        notes: clean("notes"),
        ...(values.active === undefined
          ? {}
          : { active: values.active === "true" }),
      };
    case "receipt":
      return {
        productId: values.productId,
        quantity: Number(values.quantity),
        reason: values.reason.trim(),
      };
    case "adjustment":
      if (Number(values.delta) === 0)
        throw { detail: "Enter a nonzero adjustment." };
      return {
        productId: values.productId,
        delta: Number(values.delta),
        reason: values.reason.trim(),
      };
    case "cancel":
      return { reason: values.reason.trim() };
    case "order":
      if (!orderLines.length) throw { detail: "Add at least one product." };
      return {
        customerId: values.customerId,
        items: orderLines.map((l) => ({
          productId: l.product.id,
          quantity: l.quantity,
        })),
        notes: clean("notes"),
      };
  }
}
function pending(form, body) {
  const storageKey = pendingKey(form);
  let saved;
  try {
    saved = JSON.parse(sessionStorage.getItem(storageKey));
  } catch (_) {
    /* Invalid local draft is replaced. */
  }
  if (!saved || JSON.stringify(saved.body) !== JSON.stringify(body)) {
    saved = { key: crypto.randomUUID(), body };
    sessionStorage.setItem(storageKey, JSON.stringify(saved));
  }
  return saved.key;
}
async function submitForm(form) {
  const button = $('button[type="submit"], button:not([type])', form);
  if (button?.disabled) return;
  clearErrors();
  try {
    const body = payload(form);
    if (
      form.dataset.kind === "cancel" &&
      !confirm(
        `Cancel order ${form.dataset.orderId} and release ${form.dataset.units} reserved units?`,
      )
    )
      return;
    const key = form.dataset.idempotent ? pending(form, body) : undefined;
    if (button) button.disabled = true;
    const result = await api(
      form.dataset.endpoint,
      form.dataset.method,
      body,
      key,
    );
    sessionStorage.removeItem(pendingKey(form));
    if (form.dataset.kind === "order")
      location.assign(`/orders/${result.id}?saved=true`);
    else if (form.dataset.redirect === "reload") {
      const u = new URL(location.href);
      u.searchParams.set("saved", "true");
      location.assign(u);
    } else location.assign(`${form.dataset.redirect}?saved=true`);
  } catch (error) {
    showError(error, form);
    if (error.status >= 400 && error.status < 500 && error.status !== 401)
      sessionStorage.removeItem(pendingKey(form));
    if (error.code === "INSUFFICIENT_STOCK" && form.dataset.kind === "order")
      await refreshLines();
  } finally {
    if (button) button.disabled = false;
  }
}
$$("form[data-kind]").forEach((form) => {
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    submitForm(form);
  });
  form.addEventListener("input", (event) => {
    if (!["customer-search", "product-search"].includes(event.target.id))
      sessionStorage.removeItem(pendingKey(form));
  });
  if (form.dataset.idempotent && form.dataset.kind !== "order") {
    try {
      const saved = JSON.parse(sessionStorage.getItem(pendingKey(form)));
      if (saved)
        Object.entries(saved.body).forEach(([name, value]) => {
          if (form.elements[name]) form.elements[name].value = value ?? "";
        });
    } catch (_) {
      /* No restorable draft. */
    }
  }
});
$$("form.filters").forEach((form) =>
  form.addEventListener("submit", () =>
    $$("input,select", form).forEach((input) => {
      if (!input.value) input.disabled = true;
    }),
  ),
);
$$("[data-page]").forEach((button) =>
  button.addEventListener("click", () => {
    const url = new URL(location.href);
    url.searchParams.set("page", button.dataset.page);
    location.assign(url);
  }),
);
$$("[data-toggle-product]").forEach((button) =>
  button.addEventListener("click", async () => {
    const active = button.dataset.active === "true";
    if (
      !confirm(
        `${active ? "Reactivate" : "Archive"} this product? Existing orders keep their history.`,
      )
    )
      return;
    button.disabled = true;
    try {
      await api(`/api/v1/products/${button.dataset.toggleProduct}`, "PATCH", {
        active,
      });
      location.reload();
    } catch (error) {
      showError(error);
      button.disabled = false;
    }
  }),
);
$$("[data-fulfil]").forEach((button) =>
  button.addEventListener("click", async () => {
    if (
      !confirm(
        `Fulfil order ${button.dataset.fulfil}? This dispatches ${button.dataset.units} units from physical stock.`,
      )
    )
      return;
    button.disabled = true;
    try {
      await api(`/api/v1/orders/${button.dataset.fulfil}/fulfil`, "POST");
      location.reload();
    } catch (error) {
      showError(error);
      button.disabled = false;
    }
  }),
);
let orderLines = [];
let availableProducts = [];
function renderLines() {
  const rows = $("#order-lines");
  rows.replaceChildren();
  let total = 0n;
  orderLines.forEach((line, index) => {
    const row = document.createElement("tr");
    const product = document.createElement("td");
    const strong = document.createElement("strong");
    strong.textContent = line.product.name;
    const sub = document.createElement("small");
    sub.className = "block muted";
    sub.textContent = `${line.product.sku} · ${line.product.available} available`;
    product.append(strong, sub);
    const price = document.createElement("td");
    price.textContent = money(cents(line.product.unitPrice));
    const quantity = document.createElement("td");
    const input = document.createElement("input");
    input.type = "number";
    input.min = "1";
    input.max = "1000000";
    input.step = "1";
    input.required = true;
    input.value = line.quantity;
    input.setAttribute("aria-label", `Quantity for ${line.product.name}`);
    input.addEventListener("input", () => {
      line.quantity = Number(input.value);
      sessionStorage.removeItem(pendingKey($("#order-form")));
      updateTotals();
    });
    quantity.append(input);
    const error = document.createElement("span");
    error.className = "field-error";
    error.dataset.field = `items[${index}].quantity`;
    quantity.append(error);
    const subtotal = document.createElement("td");
    subtotal.dataset.subtotal = String(index);
    const remove = document.createElement("td");
    const button = document.createElement("button");
    button.type = "button";
    button.className = "remove-line";
    button.textContent = "×";
    button.setAttribute("aria-label", `Remove ${line.product.name}`);
    button.addEventListener("click", () => {
      orderLines.splice(index, 1);
      sessionStorage.removeItem(pendingKey($("#order-form")));
      renderLines();
    });
    remove.append(button);
    row.append(product, price, quantity, subtotal, remove);
    rows.append(row);
  });
  $("#no-lines").hidden = orderLines.length > 0;
  $("#line-count").textContent = String(orderLines.length);
  updateTotals();
}
function updateTotals() {
  let total = 0n;
  orderLines.forEach((line, index) => {
    const amount =
      cents(line.product.unitPrice) *
      BigInt(
        Number.isInteger(line.quantity) && line.quantity > 0
          ? line.quantity
          : 0,
      );
    total += amount;
    $(`[data-subtotal="${index}"]`).textContent = money(amount);
  });
  $("#preview-total").textContent = money(total);
}
async function refreshLines() {
  try {
    for (const line of orderLines)
      line.product = await api(`/api/v1/products/${line.product.id}`);
    renderLines();
  } catch (error) {
    showError(error);
  }
}
function debounce(fn) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), 220);
  };
}
async function loadOptions(kind, query = "") {
  const select = $(`#${kind}-select`);
  const selected = select.value;
  const result = await api(
    `/api/v1/${kind === "customer" ? "customers" : "products"}?active=true&size=100&q=${encodeURIComponent(query)}`,
  );
  if (kind === "product") availableProducts = result.content;
  const first = document.createElement("option");
  first.value = "";
  first.textContent =
    kind === "customer" ? "Choose a customer" : "Choose a product to add";
  select.replaceChildren(first);
  result.content.forEach((item) => {
    const option = document.createElement("option");
    option.value = item.id;
    option.textContent =
      kind === "customer"
        ? item.name
        : `${item.sku} · ${item.name} · ${item.available} available`;
    select.append(option);
  });
  select.value = selected;
}
async function initializeOrder() {
  if (!$("#order-form")) return;
  try {
    await Promise.all([loadOptions("customer"), loadOptions("product")]);
    for (const kind of ["customer", "product"])
      $(`#${kind}-search`).addEventListener(
        "input",
        debounce((event) =>
          loadOptions(kind, event.target.value).catch(showError),
        ),
      );
    $("#add-line").addEventListener("click", () => {
      const product = availableProducts.find(
        (p) => p.id === $("#product-select").value,
      );
      if (!product) return;
      if (orderLines.some((l) => l.product.id === product.id)) {
        showError({
          detail:
            "This product is already in the order. Change its quantity instead.",
        });
        return;
      }
      if (orderLines.length >= 50) {
        showError({ detail: "An order can contain at most 50 products." });
        return;
      }
      orderLines.push({ product, quantity: 1 });
      sessionStorage.removeItem(pendingKey($("#order-form")));
      renderLines();
    });
    const saved = JSON.parse(
      sessionStorage.getItem(pendingKey($("#order-form"))),
    );
    if (saved) {
      const customer = await api(`/api/v1/customers/${saved.body.customerId}`);
      if (
        ![...$("#customer-select").options].some((o) => o.value === customer.id)
      ) {
        const option = document.createElement("option");
        option.value = customer.id;
        option.textContent = customer.name;
        $("#customer-select").append(option);
      }
      $("#customer-select").value = customer.id;
      $("#order-form").elements.notes.value = saved.body.notes || "";
      orderLines = await Promise.all(
        saved.body.items.map(async (item) => ({
          product: await api(`/api/v1/products/${item.productId}`),
          quantity: item.quantity,
        })),
      );
    }
    renderLines();
  } catch (error) {
    showError(error);
  }
}
initializeOrder();
