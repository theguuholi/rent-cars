# React web conventions

- Keep pages, routes, API calls and their tests with the feature under `src/features/`. Register feature routes in the shared router. Use a URL for state that must survive refresh or participate in browser history.
- Call the API through the Vite proxy at `/api`; keep backend host selection in the development configuration. Pass the query's AbortSignal to fetch. Match the API's response shape and explicitly handle failed responses and empty 204 bodies where applicable.
- Use TanStack Query for server data, TanStack Router for navigation and TanStack Form for editable drafts. Preserve input after a failed save, prevent duplicate submissions and refresh the relevant query after successful writes.
- Use the shared PageSection for catalog pages and Tailwind utilities near the markup. Keep accessible names, field labels, focus and field/error associations. Check affected layouts at desktop and mobile widths.
- Keep a colocated suite for each production component, client and feature route. Use Vitest `describe` for the subject or shared behavior and Given/When/Then scenario names for observable outcomes. Test the HTTP client against fetch; test screens with a real query provider and a controlled API boundary. Use memory history for navigation scenarios.
- Check the affected behavior, including loading, empty data, error recovery and the requested record on direct URLs when applicable. Run the root web check and inspect the coverage report for the files changed.
