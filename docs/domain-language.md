# Domain language and boundaries

This glossary records the business meanings confirmed for the Spring API and their current Java names.

| Term in the API | Meaning in this codebase | Java owner |
| --- | --- | --- |
| Car | A rentable vehicle with a plate, category, specifications, price, availability and images. | `cars.Car` |
| Car image | A stored photo of one car; storage bytes are handled through the shared storage port. | `cars.CarImage`, `shared.storage.ImageStorage` |
| Category | The car's body-style classification in the read-only catalog. | `categories.Category` |
| Specification | Any car-related attribute or benefit the car offers to the user. | `specifications.Specification` |
| Rental | A car rental belonging to an account in either state: open or returned. It records start, expected return, and, after return, actual return and charge. | `rentals.Rental` |
| Return | Closing an open rental, calculating the charge and releasing its car. | `rentals.RentalService.returnCar` |
| Account | A registered identity that can sign in and hold sessions; rentals refer to it by ID. | `auth.Account` |
| Session | Authentication state and refresh token for one account. | `auth.SessionController`, `auth.RefreshSessions` |

## Confirmed meanings

- **Specification** covers anything related to a car that benefits the user, including equipment and other features.
- **Rental** represents both an open rental and a returned rental.
- **available** means the car is available to rent. A value of `false` does not by itself specify why it cannot be rented.

## Boundaries enforced by ArchUnit

- Car concepts belong to `cars`; `rentals` reads and changes a car through `CarRentalAccess` and `Car`, without importing `CarRepository`.
- `cars` does not depend on `rentals`; rental orchestration owns the return workflow.
- `categories`, `specifications` and `auth` do not depend on rental internals.
- `shared` contains cross-cutting HTTP, persistence and storage support. The shared HTTP exception handler translates rental errors, so a general ban on `shared` → domain dependencies would contradict the current design.
