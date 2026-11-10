## What changed?

<!-- Describe what the user can do in one or two sentences. Link the related issue, if any. -->

## Manual QA walkthrough

Environment and startup: <!-- Example: local checkout, make dev, http://localhost:5173 -->
Test data and role: <!-- Say how to create them; do not include passwords or tokens. -->

- [ ] Open `<URL or screen>` as `<role>`. Expected: `<visible starting state>`.
- [ ] `<Click or enter a value>`. Expected: `<visible result>`.
- [ ] `<Complete the main action>`. Expected: `<success state and persisted effect>`.
- [ ] Refresh or revisit `<URL>`. Expected: `<state after reload>`.
- [ ] Try `<important invalid, conflicting, or duplicate action>`. Expected: `<message and unchanged state>`.
- [ ] Clean up test data if the flow creates a lasting record: `<steps or not applicable>`.

HTTP Yac examples: <!-- file.http → request name; use “not applicable” if there is no HTTP change. -->

## Automated verification

- [ ] Playwright: `<scenario and command, or reason no browser journey applies>`
- [ ] React: `<command and result; changed components, routes, and clients covered>`
- [ ] Java: `<command and result; HTTP, business, and persistence cases covered>`
- [ ] HTTP Yac and OpenAPI: `<requests run and generated schema checked, or not applicable>`
- [ ] Coverage and mutation: `<changed-file coverage, relevant mutation result, or documented exception>`

For a business rule, identify the test that would fail if the rule changed. For schema changes, verify a clean install and an upgrade with existing data. For HTTP changes, check MockMvc coverage, OpenAPI, and the executable request. Identify tests for changed behavior and failure paths, then inspect changed-file line and branch coverage.

## Exceptions to project conventions (if applicable)

Name the convention, affected scope, reason, trade-off, and verification. For an untested path, include its location, the blocker, substitute evidence, and a revisit condition. Request explicit maintainer review and link a decision record if this changes the default across features. State downstream work and known limits. Remove placeholders before opening the PR.
