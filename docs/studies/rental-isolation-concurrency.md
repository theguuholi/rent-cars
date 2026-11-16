# Study 1: isolation, competing requests and unique indexes

Follow a full-stack race: two drivers open the same car detail, both see it
available, and both press **Rent car**. Stale React state is expected; the server
must decide which operation can commit.

## Trace the request before running the experiment

1. React [`RentalActions`](../../apps/web/src/features/rentals/components/RentalActions/RentalActions.tsx) prevents duplicate clicks in one pending form. That does not coordinate two tabs or drivers.
2. [`rentals-api.ts`](../../apps/web/src/features/rentals/rentals-api.ts) sends POST `/rentals` and translates 409 into a useful message.
3. [`RentalService.create`](../../apps/api/src/main/java/com/github/theguuholi/rentcars/rentals/RentalService.java) joins the car lock, checks business rules and writes within a transaction.
4. [`CarRentalAccess`](../../apps/api/src/main/java/com/github/theguuholi/rentcars/cars/CarRentalAccess.java) calls `findForUpdate`; the repository uses `PESSIMISTIC_WRITE`.
5. [`V10`](../../apps/api/src/main/resources/db/migration/V10__create_rentals.sql) defines `rentals_open_car` and `rentals_open_account`, unique indexes restricted to `end_date IS NULL`.

## Run two simultaneous HTTP requests

With Docker and Java 25, from the repository root:

```sh
cd apps/api
./mvnw -B -Dtest=RentalControllerTest,RentalConcurrencyTest,RentalRepositoryTest test
```

Find `givenCompetingDriversOrCarsWhenPostingTogetherThenOneCreatedOneConflictAndOneOpenRental`
in [`RentalControllerTest`](../../apps/api/src/test/java/com/github/theguuholi/rentcars/rentals/RentalControllerTest.java).
It starts two requests behind a barrier, uses separate authenticated identities
when competing for one car, and exercises the actual security/controller/service/
PostgreSQL stack through MockMvc. There are no mocks of rental persistence.

| Scenario | Required HTTP outcome | Required committed state |
| --- | --- | --- |
| Two drivers, same car | One 201 and one 409, regardless of winner | Exactly one open rental, with the ID returned by 201; car unavailable |
| One driver, two cars | One 201 and one 409 | One open rental; winning car unavailable and losing car still available |

The barrier coordinates the start, not PostgreSQL's internal statement order.
The assertions accept either winner and check state after both requests complete.
These are HTTP contract tests without a network socket; they complement the
service-level races in `RentalConcurrencyTest`.

## Compare lock and constraint

| Mechanism | What it does | What it does not do |
| --- | --- | --- |
| Car row lock | Makes another writer of that car wait until the holder completes; the waiter then checks the current availability. | Does not serialize the same account renting two different car rows. |
| `rentals_open_car` | Rejects a second open rental for that car even if a caller bypasses the service. | Does not automatically update `cars.available`. |
| `rentals_open_account` | Prevents one account from committing two open rentals, including requests locking different cars. | Does not decide which competing request should win. |
| Transaction | Commits rental and availability together or rolls back both. | Does not make the React cache current or replace the unique constraints. |

For the same car, the loser will commonly fail the availability rule after waiting
for the lock. For different cars and the same account, the business precheck may
see the winner, or both prechecks may pass and the unique index rejects one insert.
The test must not depend on one of those schedules. A business-rule conflict currently
uses ProblemDetail, while a unique-index violation uses the generic `errors.conflict`
shape. The frontend currently treats an unrecognized 409 as a car conflict, even
when the account index caused it. Record this contract limitation when studying the
second race; this lab does not silently redesign the error contract. Inspect `DatabaseGuarantees`
in `RentalRepositoryTest` for direct writes proving the indexes independently.

Read the [PostgreSQL explicit-locking guide](https://www.postgresql.org/docs/17/explicit-locking.html)
and [unique-index guide](https://www.postgresql.org/docs/17/indexes-unique.html).
Locks coordinate access; database constraints protect an invariant across all writers.

## Observe the React outcome

1. Run `make setup` and `make dev`; register two drivers in separate browser profiles.
2. Open the same available car in both profiles before submitting either form.
3. Choose valid return dates and submit both. In Network, inspect the two POST responses.
4. Confirm one page shows an open rental and the other a conflict. No success message
   should be manufactured by a client-side optimistic update.
5. On the losing page, use **Check rental status**. This queries that driver's rentals;
   it does not resubmit the write. The driver may have no rental even though another
   driver took the car. Reopen the car detail to refresh its availability.
6. Return the winning rental to clean up. Historical rows remain; only open rows
   participate in the unique indexes.

The browser steps are exploratory and are not guaranteed to start at exactly the
same instant. Use the barrier-based test for the repeatable simultaneous experiment.
Existing frontend tests cover conflict messages, retained drafts and pending controls:

```sh
npm --prefix apps/web run test:ci -- src/features/rentals
```

## Explain before moving on

- Why is disabling **Rent car** insufficient protection?
- Why can the car lock not replace `rentals_open_account`?
- What rolls back the losing request's change to availability?
- Would removing the precheck make duplicate rentals valid? Which database rule remains?
- How would a response lost after commit differ from a rejected transaction?
  Continue with the [ACID lab](rental-acid-rollback.md).
