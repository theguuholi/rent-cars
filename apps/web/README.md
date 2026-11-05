# Rent Cars Web

React and TypeScript frontend for the rental fleet, categories and specifications. Follow the [full-stack setup guide](../../README.md#run-the-full-stack-locally) to start PostgreSQL, Spring Boot and Vite with Make.

For frontend-only development, run `make setup` and `make web-dev` from the repository root. The default backend is the Java API on port 8080. To use the Go API, start it separately and run `API_TARGET=http://127.0.0.1:8081 make web-dev`.

The home page (`/`) contains only the navigation menu. Open `/categories` to browse body styles. Select **Specifications** (`/specifications`) to list vehicle features, create a new feature, edit its name and description, or confirm deletion. This catalog uses the Java backend. The Go backend currently serves categories only.

See [web quality checks](#web-quality-checks) for formatting, lint, tests, coverage and the production build.

Specification actions have their own browser URLs. Open `/specifications/new` to create a record, `/specifications/:id/edit` to edit it, or `/specifications/:id/delete` to review deletion. Replace `:id` with the record's UUID. Opening or refreshing a deletion URL only shows the confirmation.

## Avatars across local, cloud and Kubernetes environments

Sign in and open **My avatar** (`/account/avatar`). Choose a JPEG or PNG, inspect the preview, select **Save avatar**, then reload to verify persistence. **Remove avatar** clears the saved image. The API accepts up to 5 MiB and 4 million pixels and normalizes the result to a PNG fitting within 512 × 512 pixels.

The browser always calls `/api/users/me/avatar`. It does not choose the storage provider or talk directly to S3/GCS. Keep storage settings and cloud credentials on the API; never put them in `VITE_*` variables or the frontend bundle. See [the Java storage walkthrough](../api/README.md#avatar-storage-choose-your-environment) for local, S3/GCS and Kubernetes setup.

| Environment               | Start/open the frontend                                                                 | API routing                                                             |
| ------------------------- | --------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| Local development         | `make setup`, then `make dev`; open http://localhost:5173                               | Vite forwards `/api` to the Java API on port 8080                       |
| Local API using S3/GCS    | Start the configured API, then `make web-dev`                                           | The same Vite proxy and upload requests                                 |
| Docker Compose            | `docker compose -f compose.stack.yaml up --build -d --wait`; open http://localhost:8081 | Nginx forwards `/api` inside the Compose network                        |
| Docker Desktop Kubernetes | Follow the Java/Helm walkthrough; open http://localhost:8082                            | The web Service exposes Nginx, which forwards `/api` to the API Service |

Run these commands from the repository root. `API_TARGET` configures the **Vite development proxy only**; it does not reconfigure a built React image or select a storage provider. In Compose and Kubernetes, use the web application's single origin so the authentication cookies and API requests follow the existing proxy setup. Prefer the deployed web application when testing Kubernetes rather than pointing Vite at the cluster.

In browser developer tools, inspect the avatar requests after saving: PUT returns 204, GET returns the PNG, and DELETE returns 204. A subsequent GET returns 404 when there is no avatar; this is the expected empty state. A 413 means the file is too large, and 422 means the content is invalid or exceeds the pixel limit. For 401, sign in again if session renewal fails. For a server/storage error, inspect API logs and its storage configuration; changing React's storage variables cannot fix it.

The preview before saving is local to the browser. Verify persistence by saving successfully and reloading; a preview alone does not prove the upload reached storage.

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

## Car photo gallery

Open a car from the fleet to view its photos. The gallery is public; signed-in administrators also see **Add car photo**, **Upload photo** and **Remove photo** controls. Select one JPEG or PNG at a time, upload it, and wait for the gallery to refresh. Failed uploads retain the selected file for retry. Car photos fit within 1600 × 1600 pixels after server normalization; avatars keep their 512-pixel limit. All local, Compose, S3/GCS and Kubernetes settings described above also apply to car photos. See [the car photo guide](../../docs/car-images.md).
