# Delivering a user-visible feature

Use this workflow for a feature that changes what a person can do in the React application. The existing [Playwright](../.agents/skills/rent-cars-playwright-journey/SKILL.md), [React](../.agents/skills/rent-cars-react-feature/SKILL.md), and [Java](../.agents/skills/rent-cars-java-feature/SKILL.md) skills provide the implementation details. Follow the user's requested scope; if one side is excluded, identify its downstream work in the PR.

## 1. Describe the user's journey

Write the entry URL, user role, starting data, actions, visible success, and at least one important failure or boundary. Include what should happen after a refresh or a second attempt when relevant. Check the current route and accessible controls before naming them. Keep the journey small enough to test and explain.

## 2. Write the Playwright acceptance test first

Add a Given/When/Then scenario under `apps/web/e2e/`. Use `test.step` for setup, real navigation and clicks, and visible assertions. Prefer role and label locators, create independent test data where needed, and avoid fixed sleeps or fixed database IDs. Run it locally and record the expected failure before implementation. Do not leave an intentionally failing test as the final PR state. For an API-only change with no user journey, start with an HTTP contract test and explain why no browser scenario applies.

## 3. Define the HTTP contract and the focused tests

- Add or update the matching file under `requests/http/` so a developer can execute the successful request and its important failure with HTTP Yac. Reuse collection state and IDs instead of pasting tokens. Keep secrets out of the repository.
- Define request fields, response body, status codes, authorization, and validation. Add or update OpenAPI annotations and verify the generated `/v3/api-docs` schema against the HTTP contract tests.
- Write colocated Vitest scenarios for every new React component, route, and API client. Assert loading, empty, success, error, retry, disabled or pending state, and cache refresh when applicable. These tests may use a controlled API boundary while the backend is being built.
- Write Java tests for the business rule and HTTP contract before the implementation. Use unit tests for calculations, MockMvc for request and response behavior, and PostgreSQL/Testcontainers for migrations, constraints, transactions, or competing writes.

## 4. Implement through the smallest responsible layers

For persistence changes, add a versioned Flyway migration first, including constraints and the effect on existing rows. Align the JPA entity and repository with that schema. Put business decisions in the domain operation or service, keep the transaction around the complete write, and expose it through the controller. Then implement the React API client and the visible components. Reuse the nearest feature patterns; do not add layers just to follow this sequence.

## 5. Verify observable behavior

Every new or changed executable line must be exercised by an automated test, and every changed conditional outcome must have a meaningful assertion. This means behavior coverage, not one test per source line. Check the changed files in JaCoCo and Vitest coverage reports. Keep the existing coverage and mutation gates; do not lower thresholds or exclude code to make a check pass. If a path cannot be tested reliably, use the exception process in [AGENTS.md](../AGENTS.md).

Run `make check` from the repository root for cross-stack changes; use `make check-web` or `make check-api` when the user requested only that layer. With `make dev` running, execute the relevant HTTP Yac requests and the targeted Playwright scenario, then the browser suite (`npm --prefix apps/web run test:browser`) when navigation or shared state changed. Run PIT for mutation-sensitive Java domain changes as described in the [API guide](../apps/api/README.md). Inspect failures and rerun affected checks after the last edit.

## 6. Write a PR that a tester can follow

Use the [PR template](../.github/pull_request_template.md). Give a short explanation of the behavior, a step-by-step manual walkthrough with checkboxes, the expected result after each action, and an error or boundary path. Identify the environment, setup commands, test data creation, relevant HTTP Yac request names, and any cleanup. Record commands actually run, their results, coverage of changed files, Playwright scenario names, and any remaining risk. A reviewer should be able to test the feature from the PR description without reading its source code.
