# Rent Cars Web

React and TypeScript frontend for the vehicle category and specification catalogs. Follow the [full-stack setup guide](../README.md#run-the-full-stack-locally) to start PostgreSQL, Spring Boot and Vite with Make.

For frontend-only development, run `make setup` and `make web-dev` from the repository root. The default backend is the Java API on port 8080. To use the Go API, start it separately and run `API_TARGET=http://127.0.0.1:8081 make web-dev`.

The home page (`/`) contains only the navigation menu. Open `/categories` to browse body styles. Select **Specifications** (`/specifications`) to list vehicle features, create a new feature, edit its name and description, or confirm deletion. This catalog uses the Java backend. The Go backend currently serves categories only.

See [web quality checks](../README.md#web-quality-checks) for formatting, lint, tests, coverage and the production build.

Specification actions have their own browser URLs. Open `/specifications/new` to create a record, `/specifications/:id/edit` to edit it, or `/specifications/:id/delete` to review deletion. Replace `:id` with the record's UUID. Opening or refreshing a deletion URL only shows the confirmation.

### Writing a test scenario

Use Vitest's `test` (imported as `scenario`) and name each case **Given [context], when [event], then [observable result]**. For example: “Given CategoryList, when listCategories is still pending, then the status reads ‘Loading categories…’.” Keep loading, empty, error and recovery cases separate so a failure identifies the affected behavior. Run `npm run test:ci -- --reporter=verbose` to see the scenario names, and `npm run test:coverage` to inspect unexecuted lines and branches.

Group scenarios by the component, route or API client using `describe`. For larger suites, add one level for a behavior such as deletion or error recovery. Place setup hooks in the narrowest group that needs them. Keep the specific input and expected outcome in the Given/When/Then scenario so failures are understandable in the test report.
