# CI responsibilities

The entry point is [../workflows/ci.yml](../workflows/ci.yml).

- The workflow owns triggers, runners, permissions, job timeouts, dependencies and the explicit PIT matrix.
- When adding an API package, add its target to the API mutation matrix in ci.yml. Include root-level application classes too; do not leave production classes outside the mutation scope.
- Each application folder owns its quality and mutation actions.
- Frontend E2E owns starting the local stack and running browser journeys.
- Quality and mutation actions run Maven or npm directly in their application directory. Root Make targets are optional local shortcuts, not CI dependencies.
- Each Java quality job analyzes only its own Sonar project. Provision projects and manage their gates/profiles in Sonar, not in CI. Neither application reads or changes the other application's Sonar configuration.
- E2E is intentionally an integration check: it starts the API and frontend together. Unit, coverage and mutation jobs remain independent.
- Java quality actions receive Sonar credentials and project settings as inputs. The caller allows analysis only for trusted runs; secrets are not available to fork PRs.
- The final Merge readiness job requires every dependency to succeed, preserving the required check names.

Do not introduce a generic action with flags to switch between applications or responsibilities.
Small duplication in setup steps keeps each pipeline readable and independent.
Keep the pinned external action versions and existing test/mutation thresholds.
A new CI job must also be listed in Merge readiness's needs.
