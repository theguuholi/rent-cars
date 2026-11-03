## What changed?

## How was it verified?

- For a business rule, identify the test that would fail if the rule changed.
- For schema changes, verify a clean install and an upgrade with existing data.
- For HTTP changes, check MockMvc coverage, OpenAPI and the executable request.

- Identify tests covering each changed behavior and its failure paths. Inspect changed-file line and branch coverage in the relevant report.

## Exceptions to project conventions (if applicable)

Name the convention, affected scope, reason, trade-off and verification. For an untested path, include its location, the blocker, substitute evidence and a revisit condition. Request explicit maintainer review and link a decision record if this changes the default across features.
