# Rent Cars

This monorepo contains the Spring Boot API in `api/`, the React application in `web/`, and executable HTTP examples in `requests/`. The Go implementation lives in a separate repository. Work within the requested scope.

## Before editing

- Read [the API guide](api/AGENTS.md) for backend work and [the web guide](web/AGENTS.md) for frontend work. For changes to an HTTP contract, inspect both sides and the corresponding REST Client file. Call out downstream work if the request limits changes to one side.
- Use the [Java feature skill](.agents/skills/rent-cars-java-feature/SKILL.md) or [React feature skill](.agents/skills/rent-cars-react-feature/SKILL.md) when its workflow fits. Follow the nearest existing feature and its tests; verify which domain rules actually apply.
- Keep code, test descriptions, API examples and repository documentation in English. See the [README](README.md) for prerequisites and local startup.

## Testing requirements

Every new or changed line of executable application code must be exercised by automated tests. Cover each outcome of new or changed conditional logic, including failure paths. Assert an observable result such as the HTTP response, persisted state, rendered UI or an interaction that forms part of the contract. Executing a line alone is insufficient.

Run the relevant check from the repository root and inspect the changed files in the coverage report:

| Scope | Command | Local report |
| --- | --- | --- |
| Java (Docker running) | `make check-api` | `api/target/site/jacoco/index.html` |
| React (run `make setup` on a fresh checkout) | `make check-web` | `web/coverage/index.html` |
| Both | `make check` | Both reports above |

Add tests for missed executable lines and branches. Document any path that cannot be tested reliably using the exception process below. Existing aggregate CI thresholds are a separate merge safeguard. Preserve the configured checks and coverage scope.

## When a change departs from a convention

1. Identify the convention and the concrete requirement it conflicts with. Check the nearest implementation and the reason behind it. Differences between business rules are expected: category descriptions are optional; specification descriptions are required.
2. Prefer the narrowest change that meets the requirement. In the PR, record the affected scope, rationale, trade-off, verification and any remaining risk. For a testing exception, identify the exact path, why automation is unreliable, what evidence you obtained and the condition for revisiting it.
3. Ask a maintainer to explicitly review the exception before merge. A one-off exception applies only to its recorded scope. If the team adopts a new default, update the relevant scoped guide, skill or executable check in the same PR. For a lasting decision spanning features, record the decision and alternatives in a short architecture decision record under `docs/decisions/`.

## Handoff

Report the behavior changed, the commands actually run and their results. Distinguish local verification from remote checks. Flag unverified paths and pending decisions for the reviewer.
