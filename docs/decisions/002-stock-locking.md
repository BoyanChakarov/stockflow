# ADR 002 — Immediate reservations and deterministic row locks

Accepted. An order starts CONFIRMED, reserves its entire contents, and stays reserved until an employee fulfils or cancels it. There are no drafts, partial transitions, expiring reservations, or backorders.

Use READ COMMITTED and PostgreSQL pessimistic row locks. Acquire product locks individually in canonical UUID order; lock a customer before order creation and an existing order before a transition. Customer/product edits respect the same locks so snapshots and eligibility cannot change midway. This trades some same-customer concurrency for straightforward consistency in a small single-warehouse application. Bounded database waits return a retryable conflict instead of partial writes.
