# ADR 003 — Transactional durable idempotency

Accepted. Actor + canonical operation + UUID key is unique in PostgreSQL. The normalized request hash and successful response are stored in the same transaction as the mutation. INSERT ON CONFLICT avoids an aborted-transaction uniqueness race. A concurrent duplicate waits for the winner or receives a bounded retryable timeout.

Successful records are retained for the dataset lifetime. Validation/business failures retain no claim. A replay occurs before current catalogue checks and returns the original creation snapshot, not a later recalculation. Reusing the key with a changed normalized payload is a 409. Form keys and payloads survive network uncertainty and reload in sessionStorage.

Fulfil/cancel instead use order-row locking and terminal-state checks; they do not need another idempotency key.
