# Rental integration events, schema version 1

Topic: `rentals.events`. Kafka key: rental UUID as a string. Encoding: UTF-8 JSON, without Java type headers. Events are facts; consumers must not infer a command to open/return a rental. Delivery is at-least-once, with possible duplicate/late attempts. Consumers use independent group IDs for independent purposes and deduplicate by eventId. Reading does not delete records; local retention is seven days.

```json
{
  "eventId": "b2a088d3-f66d-4f47-af43-746de9fc0375",
  "eventType": "RentalOpened",
  "schemaVersion": 1,
  "occurredAt": "2026-11-16T15:00:00Z",
  "rentalId": "94f25dd5-b8ef-4b41-8d33-72c97cbac937",
  "payload": {
    "accountId": "2dfbdb0a-249e-42db-a0db-a4c066ffb067",
    "carId": "1348429c-d273-477a-a32a-396816a1a29c",
    "startDate": "2026-11-16T15:00:00Z",
    "expectedReturnDate": "2026-11-18T15:00:00Z",
    "endDate": null,
    "dailyRateCents": 15000,
    "fineAmountCents": 5000,
    "totalCents": null,
    "currency": "BRL"
  }
}
```

All envelope fields are required. `eventId` uniquely identifies this fact and remains unchanged on retries. `occurredAt` is the business operation instant, not the retry time. Version is integer 1. Event types are `RentalOpened` and `RentalReturned`.

Payload fields are required, including explicit nulls. UUIDs are strings. Instants use UTC ISO-8601. Prices are nonnegative integer Brazilian centavos, snapshotted at rental opening. `RentalOpened` has null `endDate`/`totalCents`. `RentalReturned` has an actual return instant (also `occurredAt`) and nonnegative final charge `totalCents` (64-bit integer range). It carries the same agreed prices/start/expected-return values. It does not report that any payment was collected. Consumers in JavaScript must preserve monetary integer precision if values exceed Number's safe integer range.

Account IDs are personal-data identifiers, not recipient addresses. A future notification service needs a separately agreed, authorized recipient lookup or minimized recipient contract; no cross-service direct database access. Category and vehicle labels are not included in v1; do not silently enrich historical analytics with current catalog values. Agree a snapshot contract before implementing category-based historical metrics.

Evolution: consumers tolerate additive unknown fields; do not rename/remove fields, change units/types or reinterpret existing meanings within v1. Breaking changes need a separately documented version and consumer migration. Consumer groups, partition assignments and replay do not automatically provide exactly-once external effects. Replay for analytics must not resend notifications.

## Recommendations dead letters

The recommendations group owns `rentals.events.recommendations.DLT`. It preserves
the original key/payload and source headers; recovery is at-least-once. It is not a
new business-event type and does not change schema version 1. The API's PostgreSQL
Outbox remains unchanged. See the [recovery design](../recommendations-dlt.md) for
retries, offset guarantees, retention and replay implications for other groups.
