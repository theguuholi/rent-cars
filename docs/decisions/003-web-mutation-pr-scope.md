# Mutation scope for Web pull requests

The Web mutation check originally mutated all application source on every pull request. The current full run takes about 34 minutes with static mutations skipped, then reports an 85% score. Several hundred surviving mutations are in unchanged modules. With static mutations included, the run reaches its 45-minute timeout. That makes the existing 100% full-source PR gate unable to pass for an unrelated change.

For pull requests, mutate production TypeScript and TSX files changed against the base commit. Keep the 100% mutation threshold for those files. Keep the existing full-source run for weekly and manually dispatched analysis, and keep the separate full-source Vitest coverage and build checks on every pull request. This lets a reviewer distinguish new behavior from existing mutation debt while retaining a report of the whole codebase.

This changes the former PR check scope. A change to an unchanged file that affects behavior elsewhere might leave a surviving mutation there undetected by the PR mutation check. The full Vitest suite, coverage gate, Playwright journeys and scheduled full mutation run reduce that risk. Revisit this decision after the full-source mutation score reaches 100% and the full run fits the CI time budget. A maintainer must explicitly review this change before merging the PR that introduces it.
