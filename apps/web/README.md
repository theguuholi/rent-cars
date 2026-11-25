# Rent Cars Web

React and TypeScript frontend for the rental fleet, categories and specifications. Follow the [full-stack setup guide](../../README.md#run-the-full-stack-locally) to start PostgreSQL, Spring Boot and Vite with Make.

For frontend-only development, run `make setup` and `make web-dev` from the repository root. The default backend is the Java API on port 8080. To use the Go API, start it separately and run `API_TARGET=http://127.0.0.1:8081 make web-dev`.

The home page (`/`) contains only the navigation menu. Open `/categories` to browse body styles. Select **Specifications** (`/specifications`) to list vehicle features, create a new feature, edit its name and description, or confirm deletion. This catalog uses the Java backend. The Go backend currently serves categories only.

Sign in as a driver and open an available car under `/cars` to choose an expected return time and rent it. The car detail shows **Return car** for your open rental; after returning, the same car can be rented again. The API sets the actual start and return times and calculates the final charge. A competing rental or an account with another open rental receives a conflict message on the form.

See [web quality checks](#web-quality-checks) for formatting, lint, tests, coverage and the production build.

Specification actions have their own browser URLs. Open `/specifications/new` to create a record, `/specifications/:id/edit` to edit it, or `/specifications/:id/delete` to review deletion. Replace `:id` with the record's UUID. Opening or refreshing a deletion URL only shows the confirmation.

## Web quality checks

From this directory, use the Node version in `.node-version` and install dependencies with `npm ci`. Run `npm run format:check`, `npm run lint`, `npm run architecture`, `npm run test:coverage` and `npm run build` to reproduce **Web verification**. `make check-web` runs all five from the repository root. Use `npm run format` to apply Oxfmt fixes or `npm run lint:fix` for automatic Oxlint fixes.

The [domain glossary and React import boundaries](../../docs/domain-language.md#react-names-and-boundaries) explain the dependency-cruiser rules. The check uses SWC to parse the project's TypeScript 7 sources.

Vitest requires at least **95% each** for global line, statement, function and branch coverage across application TypeScript under `src/`. Tests, test helpers and declarations are excluded. Open `coverage/index.html` for the HTML report; `coverage/lcov.info` is available for tooling. Download **web-coverage** from the Web quality workflow to inspect a PR, including one that failed its coverage threshold.

### Writing a test scenario

Use Vitest's `test` (imported as `scenario`) and name each case **Given [context], when [event], then [observable result]**. For example: “Given CategoryList, when listCategories is still pending, then the status reads ‘Loading categories…’.” Keep loading, empty, error and recovery cases separate so a failure identifies the affected behavior. Run `npm run test:ci -- --reporter=verbose` to see the scenario names, and `npm run test:coverage` to inspect unexecuted lines and branches.

Group scenarios by the component, route or API client using `describe`. For larger suites, add one level for a behavior such as deletion or error recovery. Place setup hooks in the narrowest group that needs them. Keep the specific input and expected outcome in the Given/When/Then scenario so failures are understandable in the test report.

### Mutation testing

From `apps/web/`, run `npm ci` and `npm run mutation` for the full application. StrykerJS mutates the application TS/TSX under `src/` and uses the existing Vitest suite to detect behavioral changes. Open `reports/mutation/mutation.html` to inspect each surviving change; the same directory includes a machine-readable JSON report. A mutation score below 100% fails the command. The ordinary web coverage gate remains separate.

GitHub runs **Web mutation tests** on Mondays at 06:37 UTC, on manual dispatch, and on every pull request changing files under `apps/web/` or the workflow itself. Pull requests mutate only changed production TS/TSX files and require 100% of their mutations to be detected. Scheduled and manual runs mutate the full application. Download the **web-mutation** artifact from the workflow run. See [the mutation gate decision](../../docs/decisions/003-web-mutation-pr-scope.md) before changing this scope.

The sandbox excludes the root `tsconfig.json` project-reference aggregator because Stryker 10 uses a compiler API removed in TypeScript 7 to rewrite references. Its two referenced projects live within `apps/web/`. Vitest runs through Vite; the normal web build still type-checks both projects with the original configuration.

## Study: rental transactions and recovery

Follow the [ACID and rollback lab](../../docs/studies/rental-acid-rollback.md) to trace the Spring transaction, force a failure after both database writes, and test recovery from a failed or lost rental response without automatically repeating the request.

## Shared text fields

Use `src/components/TextField/TextField.tsx` for TanStack text inputs. It connects
value, change, blur, field errors and accessible descriptions without repeating
`form.Field` markup:

```tsx
<TextField form={form} name="lastName" autoComplete="family-name" required />
<TextField form={form} name="driveLicense" label="CNH number" maxLength={11} required />
<TextField form={form} name="password" type="password" maxLength={72} aria-describedby="password-hint" />
```

The `name` is checked against string-valued fields in the supplied form. Its
camelCase spelling provides a fallback label (`lastName` becomes `Last name`);
provide `label` for domain terminology, acronyms or translated copy. The default
`maxLength` is 80; override it to match the schema (email 254, username 40, CNH 11,
password 72). Browser limits do not replace schema or backend validation.

Keep validation and API submission in the feature. TanStack schema errors are
read automatically; when parsing at submission, publish the field error map with
`form.setErrorMap({ onSubmit: { fields: errors } })`. The shared field displays
string errors and standard schema issue messages. Request-level failures such as
a duplicate account or network failure remain a form-level alert. Do not attach
an API error to a guessed field when the API does not identify one.

Existing help text can be linked using `aria-describedby`; the component adds
its own error ID without losing that hint. Labels use an explicit `id` when
provided or a unique React-generated ID. Checkboxes, selects, file uploads and
currency controls keep their own behavior; this component is not a form builder.

## Forms: validation and reusable fields

Use TanStack Form for drafts and the existing Zod schemas for domain validation.
See [TanStack Form validation](https://tanstack.com/form/latest/docs/framework/react/guides/validation) for Standard Schema integration.
Keep the request, pending mutation, cancellation and server error in the feature
that owns the operation. A new validation library is not needed.

```tsx
const form = useForm({
  defaultValues: { name: '', description: '' },
  validators: { onSubmit: specificationDraftSchema },
  onSubmit: ({ value }) => onSave(specificationDraftSchema.parse(value)),
})

<TextField form={form} name="name" required maxLength={null} />
```

- Supply Zod directly through `validators`. TanStack associates schema issues with
  the corresponding fields; do not add a second manual error-map adapter.
- Validation does not replace the draft with Zod's transformed output. Parse the
  submitted draft to apply trimming, uppercase conversion and BRL-to-cents conversion.
- `TextField` owns the label, value, blur, accessible error association and deduplicated
  messages. It defaults to text and 80 characters. Override the limit explicitly;
  `maxLength={null}` preserves an unlimited HTML input while the schema validates
  normalized lengths. This matters when whitespace is trimmed before validation.
- `SubmitButton` observes an async form submission. Car and specification editors
  instead keep their parent mutation's `busy` fieldset because `mutate()` returns
  before the network request finishes.
- Registration and password reset validate on submit. Catalog editors pass each
  field schema directly (`schema={carFieldSchemas[name]}` for `TextField`, or
  `validators={{ onChange: schema }}` for a native TanStack field) to preserve
  validation on change and submission without showing errors on untouched fields. Password confirmation issues stay next to that field.
- The shared registration schema lives in `features/auth/registration-schema.ts`.
  Admin creation extends it; admin editing reuses its identity rules and preserves
  legacy null profiles through `userEditDraftSchema`. Login does not enforce the
  password-creation policy.
- Keep file uploads, time-dependent rental checks, checkbox collections and
  URL-backed catalog filters specialized. Extract another control when it removes
  established duplication, not merely to make every tag a component.

Run `make check-web` at the repository root, then `npm --prefix apps/web run mutation`.
The colocated Given/When/Then tests cover invalid drafts, corrections, normalization,
async submission and legacy profiles. Existing browser journeys live in
`e2e/admin-form-validation.pw.ts`, `e2e/driver-registration.pw.ts`,
`e2e/admin-users.pw.ts` and `e2e/password-recovery.pw.ts`.

Rental browser journeys create a dedicated car through the API using
`e2e/support/rental-car.ts`, then find it by its unique name in the catalog.
Do not rent the first seeded car: another parallel journey can rent it between
listing and opening its details. The setup uses `RENT_CARS_ADMIN_EMAIL` and
`RENT_CARS_ADMIN_PASSWORD` (development defaults when unset). Run these journeys
against a development or disposable database: their driver accounts, cars and
rental history remain for inspection, since cars with rental history cannot be deleted.

### Most rented cars

The fleet displays current available cars from the latest popularity snapshot. React calls the main API at `/api/cars/recommendations`; it never contacts Spark or the recommendations service directly. Loading, empty and error states are independent of catalog filtering. See [the end-to-end walkthrough](../../docs/popular-cars.md) to generate a ranking locally or on Kubernetes.

## Visual system

The interface uses the RentX-inspired palette through Tailwind v4 `@theme` tokens
in `src/styles.css`: `brand`, `canvas`, `surface`, `ink`, `muted` and `line`, with white navigation. Archivo headings and Inter body fonts are bundled locally through
Fontsource; the browser does not contact a font CDN. Their OFL licenses ship
with the production assets under `public/licenses/`.

Reuse `Button`, `TextField`, `FormSection`, `CatalogTable`, `AccountPanel` and
`PageSection` for shared presentation. `CarCard` belongs to the cars feature and
is reused by the fleet and popularity ranking. The entire card is one router link;
`group-hover` and `group-focus-visible` synchronize its title, illustration and arrow.
Do not put buttons or other links inside it. Keep query/form state in the
feature. A new visual variation should solve an existing screen requirement,
not introduce a generic component framework.

Use Tailwind utilities beside JSX for layout, responsive breakpoints and states.
The small `action` utility also styles TanStack links as actions without changing
anchor semantics. Set `data-variant="secondary"` or `"danger"` when appropriate.
Native inputs, selects and textareas share `control`; buttons default to
`type="button"`, so form submissions must explicitly use `type="submit"`.
Keep labels, field errors, keyboard focus, disabled states and role permissions.
Transitions use `motion-safe`; selected rows and availability use data variants.

The catalog API intentionally does not include image metadata. Cards therefore
show a neutral, decorative vehicle illustration instead of fetching every car's
details or pretending that a stock photo represents the vehicle. Uploaded photos
remain available on the detail screen. The home remains navigation-only.

To review the UI, run `make dev`, then visit `/cars`, a car detail, `/login` and
`/register`. Sign in as the development administrator for `/cars/new`,
`/categories`, `/specifications` and `/users`. Use a driver account for `/rentals`.
Review at 390px and 1440px, navigate with Tab, submit an empty form, and verify
that errors and retry actions remain visible. Run `make check-web`,
`npm --prefix apps/web run mutation`, and `npm --prefix apps/web run test:e2e`.

### Catalog search and navigation

Use **Search cars** for a model, brand or category fragment. The API applies `q`
to all three fields with OR before pagination. Brand and Category are optional
AND refinements; Sort by stays above the results and Cars per page sits below.
Filters and pagination are URL state, so refresh and browser Back restore them.
Changing a filter or page size resets to page one. Legacy `name` links continue
to filter by name and display a removable Model name chip; editing the unified
search replaces that legacy constraint. Numeric model links such as `?q=208`
are supported.
Brand/category refinements remain text inputs rather than incomplete dropdowns
assembled from one page or protected administrator catalogs.

The softer design uses 24px surfaces, 12px controls, pill filters and light
shadows. Form sections rely on spacing instead of nested borders. The main fleet
comes before the independent recommendation section. The home remains navigation-only.

### Field and rental-panel feedback

The shared `control` utility gives inputs and selects a 48px minimum height and
one soft focus ring. Selects keep native semantics with a consistent chevron;
forced-colors mode restores the system arrow and a visible outline. Use
`surface-hover` for the rental receipt cards and the car's rental-details panel:
hover and focus within adjust the shadow/border without moving the content.
This feedback does not make a panel clickable; its existing links and buttons
remain the actions.
