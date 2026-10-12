# Demonstration

Start from a fresh synthetic demo database using the README Compose workflow. Set your own account passwords. The blue ceramic mug (`MUG-BLUE`, product UUID `10000000-0000-4000-8000-000000000001`) starts at zero on-hand stock and costs EUR 12.50. Other products demonstrate active, archived and low-stock states.

1. Sign in as **admin**, open Products → Blue ceramic mug, and receive **10** units with an opening-stock reference.
2. Sign out and sign in as **staff**. Create an order for Northline Studio and **3** blue mugs. The total is **EUR 37.50**.
3. The product now shows **10 on-hand / 3 reserved / 7 available**.
4. Repeating the same order payload with its original Idempotency-Key returns the same order ID. The browser automation demonstrates a stronger case: a successful response is deliberately lost, the page reloads, and retry still creates only one order.
5. Open the order and confirm **Fulfil order**. The balance becomes **7 / 0 / 7**. History records creation and fulfilment with actors and UTC timestamps.
6. Create another order for **2** mugs, then cancel it with a reason. Available stock returns to **7**, physical stock stays **7**, and the cancellation reason is retained.
7. Try an order for **8** mugs. The error reports requested/available units, retains the form, and creates no partial order.
8. Open `StockFlowIT.lastUnitRaceWithDistinctCustomers`: two committed requests compete for one unit, and exactly one succeeds. The suite also tests reversed line order and a fulfil/cancel race.
9. STAFF sees no stock-entry controls. Calling the receipt API as STAFF still returns **403**, even with valid CSRF.
10. Open the repository's GitHub Actions run. Inspect unit/integration reports, JaCoCo coverage, Chromium screenshots, fresh Compose smoke, and restart persistence evidence.

Optional API smoke against a disposable demo (credentials exported into the shell):

```bash
python3 scripts/smoke.py
docker compose restart app
docker compose up --wait
python3 scripts/smoke.py --check-restart
```

The first command creates its own synthetic product/customer and writes retry state only under ignored `target/`. The restart check logs in again, replays the original order, checks its later FULFILLED state, and verifies unchanged balances/history.

See [testing](testing.md) for the reproducible full browser workflow. Normal restarts do not reset demo passwords or duplicate seed receipts. Deleting the Compose named volume is the intentional destructive reset described in the README.
