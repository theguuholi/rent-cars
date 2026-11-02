# Rent Cars Web

React and TypeScript frontend for the vehicle category and specification catalogs. Follow the [full-stack setup guide](../README.md#run-the-full-stack-locally) to start PostgreSQL, Spring Boot and Vite with Make.

For frontend-only development, run `make setup` and `make web-dev` from the repository root. The default backend is the Java API on port 8080. To use the Go API, start it separately and run `API_TARGET=http://127.0.0.1:8081 make web-dev`.

Select **Specifications** to list vehicle features, create a new feature, edit its name and description, or confirm deletion. This catalog uses the Java backend. The Go backend currently serves categories only.

See [web quality checks](../README.md#web-quality-checks) for formatting, lint, tests, coverage and the production build.
