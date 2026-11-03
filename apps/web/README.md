# Rent Cars Web

React and TypeScript frontend for the rental fleet, categories and specifications. Follow the [full-stack setup guide](../../README.md#run-the-full-stack-locally) to start PostgreSQL, Spring Boot and Vite with Make.

For frontend-only development, run `make setup` and `make web-dev` from the repository root. The default backend is the Java API on port 8080. To use the Go API, start it separately and run `API_TARGET=http://127.0.0.1:8081 make web-dev`.

The home page (`/`) contains only the navigation menu. Open `/categories` to browse body styles. Select **Specifications** (`/specifications`) to list vehicle features, create a new feature, edit its name and description, or confirm deletion. This catalog uses the Java backend. The Go backend currently serves categories only.

See [web quality checks](#web-quality-checks) for formatting, lint, tests, coverage and the production build.

Specification actions have their own browser URLs. Open `/specifications/new` to create a record, `/specifications/:id/edit` to edit it, or `/specifications/:id/delete` to review deletion. Replace `:id` with the record's UUID. Opening or refreshing a deletion URL only shows the confirmation.

## Web quality checks

From this directory, use the Node version in `.node-version` and install dependencies with `npm ci`. Run `npm run format:check`, `npm run lint`, `npm run test:coverage` and `npm run build` to reproduce **Web verification**. Use `npm run format` to apply Oxfmt fixes or `npm run lint:fix` for automatic Oxlint fixes.

Vitest requires at least **95% each** for global line, statement, function and branch coverage across application TypeScript under `src/`. Tests, test helpers and declarations are excluded. Open `coverage/index.html` for the HTML report; `coverage/lcov.info` is available for tooling. Download **web-coverage** from the Web quality workflow to inspect a PR, including one that failed its coverage threshold.

### Writing a test scenario

Use Vitest's `test` (imported as `scenario`) and name each case **Given [context], when [event], then [observable result]**. For example: “Given CategoryList, when listCategories is still pending, then the status reads ‘Loading categories…’.” Keep loading, empty, error and recovery cases separate so a failure identifies the affected behavior. Run `npm run test:ci -- --reporter=verbose` to see the scenario names, and `npm run test:coverage` to inspect unexecuted lines and branches.

Group scenarios by the component, route or API client using `describe`. For larger suites, add one level for a behavior such as deletion or error recovery. Place setup hooks in the narrowest group that needs them. Keep the specific input and expected outcome in the Given/When/Then scenario so failures are understandable in the test report.

### Mutation testing

From `apps/web/`, run `npm ci` and `npm run mutation`. StrykerJS mutates the application TS/TSX under `src/` and uses the existing Vitest suite to detect behavioral changes. Open `reports/mutation/mutation.html` to inspect each surviving change; the same directory includes a machine-readable JSON report. A mutation score below 100% fails the command locally and in CI. The ordinary web coverage gate remains separate.

GitHub runs **Web mutation tests** on Mondays at 06:37 UTC, on manual dispatch, and on every pull request changing files under `apps/web/` or the workflow itself. Download the **web-mutation** artifact from the workflow run.

The sandbox excludes the root `tsconfig.json` project-reference aggregator because Stryker 10 uses a compiler API removed in TypeScript 7 to rewrite references. Its two referenced projects live within `apps/web/`. Vitest runs through Vite; the normal web build still type-checks both projects with the original configuration.
