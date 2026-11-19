# Rent Cars

This monorepo contains the Spring Boot API in `apps/api/`, the React application in `apps/web/`, the Kafka consumer in `apps/recommendations/`, and executable HTTP examples in `requests/`. The Go implementation lives in a separate repository. Work within the requested scope.

## Before editing

- Read [the recommendations guide](apps/recommendations/AGENTS.md) for Kafka consumer work.
- Read [the API guide](apps/api/AGENTS.md) for backend work and [the web guide](apps/web/AGENTS.md) for frontend work. For changes to an HTTP contract, inspect both sides and the corresponding HTTP Yac request. Call out downstream work if the request limits changes to one side.
- Use the [Java feature skill](.agents/skills/rent-cars-java-feature/SKILL.md) or [React feature skill](.agents/skills/rent-cars-react-feature/SKILL.md) when its workflow fits. Follow the nearest existing feature and its tests; verify which domain rules actually apply.
- Use the [Playwright journey skill](.agents/skills/rent-cars-playwright-journey/SKILL.md) when adding or changing browser navigation tests.
- For any user-visible feature, follow [feature delivery](docs/feature-workflow.md) and use the [end-to-end feature skill](.agents/skills/rent-cars-end-to-end-feature/SKILL.md). Write the Playwright acceptance journey before implementation, then verify it against the running stack before handoff. Respect a narrower user-requested scope and record any downstream work.
- Keep code, test descriptions, API examples and repository documentation in English. See the [README](README.md) for prerequisites and local startup.

## Single responsibility and human-readable code

Give each class, component and function one cohesive purpose. Separate responsibilities that change for different reasons, such as HTTP transport, business policy, persistence and presentation. A business operation may still coordinate several steps; single responsibility does not mean one statement or one method per file.

- Make the main flow readable in execution order. Use intention-revealing names and explicit intermediate values or guard clauses when they clarify decisions. Prefer a short sequence of steps over nested conditionals, nested ternaries or chains that hide side effects.
- Extract a helper, component or service when it names a meaningful responsibility, isolates an external boundary, removes established duplication or makes a complex flow easier to follow. Keep related logic together when extraction would force the reader to jump across files without gaining clarity.
- Do not add pass-through layers, generic frameworks, interfaces or configuration options for hypothetical future needs. Do not impose arbitrary line-count limits or split code only to satisfy a size metric. Preserve existing public boundaries unless the requested change requires adjusting them.
- Comments should explain a constraint, trade-off or non-obvious reason. Improve unclear names and structure before adding comments that narrate each statement. Keep transactions, side effects, failure handling and state ownership visible during refactoring.
- Apply these rules to the requested change; they do not authorize unrelated repository-wide refactoring. Readability remains a review responsibility even when Sonar and coverage gates pass.

Before handing off Java or React changes, review these questions:

1. Does the name explain the purpose without reading the implementation?
2. Can a new contributor follow the main operation in execution order?
3. Are responsibilities that change for different reasons separated at useful boundaries?
4. Does each extraction reduce the effort to understand the flow rather than merely move code?
5. Do comments explain decisions rather than compensate for unclear code?
6. Do Given/When/Then tests describe observable behavior and preserve failure semantics?

## Testing requirements

Every new or changed line of executable application code must be exercised by automated tests. Cover each outcome of new or changed conditional logic, including failure paths. Assert an observable result such as the HTTP response, persisted state, rendered UI or an interaction that forms part of the contract. Executing a line alone is insufficient.

Run the relevant check from the repository root and inspect the changed files in the coverage report:

| Scope | Command | Local report |
| --- | --- | --- |
| API (Docker running) | `make check-api` | `apps/api/target/site/jacoco/index.html` |
| React (run `make setup` on a fresh checkout) | `make check-web` | `apps/web/coverage/index.html` |
| Recommendations (Docker running) | `make check-recommendations` | `apps/recommendations/target/site/jacoco/index.html` |
| All Java | `make check-java` | Both Java reports |
| Full repository | `make check` | Java and web reports above |

Add tests for missed executable lines and branches. Document any path that cannot be tested reliably using the exception process below. Existing aggregate CI thresholds are a separate merge safeguard. Preserve the configured checks and coverage scope.

## When a change departs from a convention

1. Identify the convention and the concrete requirement it conflicts with. Check the nearest implementation and the reason behind it. Differences between business rules are expected: category descriptions are optional; specification descriptions are required.
2. Prefer the narrowest change that meets the requirement. In the PR, record the affected scope, rationale, trade-off, verification and any remaining risk. For a testing exception, identify the exact path, why automation is unreliable, what evidence you obtained and the condition for revisiting it.
3. Ask a maintainer to explicitly review the exception before merge. A one-off exception applies only to its recorded scope. If the team adopts a new default, update the relevant scoped guide, skill or executable check in the same PR. For a lasting decision spanning features, record the decision and alternatives in a short architecture decision record under `docs/decisions/`.

## Handoff

Report the behavior changed, the commands actually run and their results. Distinguish local verification from remote checks. Flag unverified paths and pending decisions for the reviewer.
