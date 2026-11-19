# React web conventions

- Keep pages, routes, API calls and their tests with the feature under `src/features/`. Register feature routes in the shared router. Use a URL for state that must survive refresh or participate in browser history.
- Give each new React component its own named folder. Keep its implementation and tests together, for example `features/cars/components/CarFacts/{CarFacts.tsx,CarFacts.test.tsx}`. Use `pages/CarsPage/CarsPage.tsx` for a feature page and `src/components/<Name>/` for UI shared across features.
- Prefer named component files and direct imports so editor tabs, stack traces and test reports identify the component. Use an `index.ts` only when a feature needs a deliberate public export surface. The current TanStack Router uses code-based routing in each feature's `routes.tsx`; page filenames do not define URLs.
- Call the API through the Vite proxy at `/api`; keep backend host selection in the development configuration. Pass the query's AbortSignal to fetch. Match the API's response shape and explicitly handle failed responses and empty 204 bodies where applicable.
- Use TanStack Query for server data, TanStack Router for navigation and TanStack Form for editable drafts. Preserve input after a failed save, prevent duplicate submissions and refresh the relevant query after successful writes.
- For forms with several related constraints, keep a colocated Zod schema responsible for validation and normalization. Pass it directly to TanStack Form validators (Standard Schema) and use its parsed output when submitting; validation alone does not apply transforms to the draft. Let TanStack route schema issues to fields instead of adding manual error-map adapters; keep form state, server requests and server errors in the component. Keep simple or time-dependent field checks local when a schema adds no clarity. The Spring API remains authoritative.
- Infer draft and submitted types from the colocated Zod schema (`z.input` and `z.output`) instead of repeating the same field list in a handwritten type. Parse once for each submission path and send the parsed value. When a form repeats labelled input/error markup, extract a small, named field component with its own focused test; keep form state and submission in the page. Do not add a generic form framework for one feature.
- Use the shared PageSection for catalog pages and Tailwind utilities near the markup. Keep accessible names, field labels, focus and field/error associations. Check affected layouts at desktop and mobile widths.
- Keep a colocated suite for each production component, client and feature route. Use Vitest `describe` for the subject or shared behavior and Given/When/Then scenario names for observable outcomes. Test the HTTP client against fetch; test screens with a real query provider and a controlled API boundary. Use memory history for navigation scenarios.
- Check the affected behavior, including loading, empty data, error recovery and the requested record on direct URLs when applicable. Run the root web check and inspect the coverage report for the files changed.
- In Playwright scenarios, use a Given/When/Then name and `test.step` for real setup, actions and observable checks. For browser journeys, locate controls by accessible role or label, assert what the user sees after navigation or clicks, and avoid fixed sleeps. Keep each scenario focused on one business journey; keep API-only tests separate from browser tests.

## Apply responsibility boundaries without fragmenting the screen

Follow the root [readability review](../../AGENTS.md#single-responsibility-and-human-readable-code). Keep ownership clear:

| Responsibility                                                | Usual owner                                            |
| ------------------------------------------------------------- | ------------------------------------------------------ |
| Compose the screen and coordinate its route/query/form states | Feature page                                           |
| Render a meaningful UI section and its local interactions     | Named component                                        |
| Encapsulate reused behavior or complex React coordination     | Focused hook, when extraction helps                    |
| Send HTTP requests and interpret transport responses          | Feature API client                                     |
| Transform data without side effects                           | Plain function                                         |
| Validate and normalize related form fields                    | Colocated schema, following the form conventions above |

For example, a car editor coordinates loading, its editable draft, save and conflict feedback. Its API client sends the PATCH request; a pure transformation can derive changed fields; a field component handles repeated labelled markup. Keep draft ownership and the submission sequence easy to locate. Do not extract a hook for every `useState`, move every JSX fragment into another file or create a generic editor for unrelated domain rules.

Render loading, error, empty and success states explicitly when nested ternaries obscure them. Avoid hidden fetches in presentation helpers or effects that duplicate query/form state. When extracting a component or hook, preserve accessible labels, cancellation, pending-state behavior, cache updates and the user's draft after a failed save. Test these observable outcomes rather than the number of hooks or components used.
