# Navigate Rent Cars as a developer

## Run the application

1. Start Docker. From the repository root run `make setup` once, then `make dev`. Open [the web app](http://localhost:5173).
2. Follow **Cars** and open any seeded car. In the browser Network panel, filter by Fetch/XHR. Opening the detail runs one `GET /api/cars/{id}`. Expand its response to see the category and equipment descriptions. The browser does not need an admin catalog request to display their names.
3. Open `/categories` directly in a new tab. The router takes you to **Sign in** before the protected page loads. In the development profile use `test@test` and `test@tesT@dev`. On success you return to Categories. Check the three requests below in the Network panel.
4. Navigate to **Specifications** or **Cars → New car** as the admin. The editor needs all available catalog options, so it requests the protected catalogs with the Bearer token. The public detail only needs the features assigned to one vehicle.
5. Sign out. Private data is discarded. To try a regular driver, use the optional signup request in [the REST Client walkthrough](../requests/rent-cars.http), then sign in with that email and password. Open `/categories` again. A driver sees **Access denied** and can still browse cars.

## Trace one login

| Browser request | Where to look | Expected result |
| --- | --- | --- |
| GET /api/csrf | Network → response headers | 204, an XSRF cookie and an X-XSRF-TOKEN header. An empty body is expected. |
| POST /api/sessions | Network → request headers and payload | The browser sends the cookie. React adds the matching header and submits the credentials. The response contains an access token. |
| GET /api/sessions/me | Network → Authorization and response | The Bearer JWT identifies the account. Only after this request succeeds does the UI publish its profile and role. |

The Vite proxy forwards `/api` to Spring, where these paths are `/csrf`, `/sessions` and `/sessions/me`. On reload, the browser restores the session with its HTTP-only refresh cookie before routing. Protected requests that receive a 401 share a refresh and retry once. Only a rejected refresh requires a new login. Network failures keep the current editor open. Signing out revokes this browser's refresh session. Issued JWTs expire after 15 minutes. A 403 from a protected resource preserves the session. A 403 during login means CSRF validation failed; submitting again obtains a fresh CSRF token.

## Follow a change through the code

1. **Find the URL.** Start at [the web entry point](../apps/web/src/main.tsx) and [router](../apps/web/src/router.ts). Each feature owns its route definitions. [Car routes](../apps/web/src/features/cars/routes.tsx) connect URLs to their list, detail or editor. The same [admin guard](../apps/web/src/features/auth/access.ts) runs on direct visits and link navigation.
2. **Find the interaction.** [LoginPage](../apps/web/src/features/auth/pages/LoginPage/LoginPage.tsx) owns email, password, submission and errors. [The auth client](../apps/web/src/features/auth/auth-api.ts) implements the three requests above. [The session store](../apps/web/src/features/auth/session.ts) keeps the verified identity in memory. The router and React read the same session. [SessionLayout](../apps/web/src/features/auth/components/SessionLayout/SessionLayout.tsx) clears the Query client and rechecks routes when the account changes.
3. **Trace the data.** [CarRecord](../apps/web/src/features/cars/pages/CarRecord/CarRecord.tsx) uses TanStack Query to load a car and forwards its AbortSignal to [the car client](../apps/web/src/features/cars/cars-api.ts). [CarDetails](../apps/web/src/features/cars/components/CarDetails/CarDetails.tsx) renders the expanded result. Writes return summaries, so a successful save invalidates the detail cache.
4. **Find the server rule.** [SecurityConfiguration](../apps/api/src/main/java/com/github/theguuholi/rentcars/auth/SecurityConfiguration.java) enforces permissions before a controller runs. [SessionController](../apps/api/src/main/java/com/github/theguuholi/rentcars/auth/SessionController.java) handles login and profile lookup. [JwtConfiguration](../apps/api/src/main/java/com/github/theguuholi/rentcars/auth/JwtConfiguration.java) configures signing and verification. Follow a fleet request from [CarController](../apps/api/src/main/java/com/github/theguuholi/rentcars/cars/CarController.java) to the service and repository in the same directory. Shared SQL migrations live in [db/migration](../apps/api/src/main/resources/db/migration); development seeds live in [db/dev](../apps/api/src/main/resources/db/dev).
5. **Find the regression test.** Look next to a React component for its Given/When/Then suite. Start with [session transition tests](../apps/web/src/features/auth/components/SessionLayout/SessionLayout.test.tsx). Run `make check-web` from the root, or `npm --prefix apps/web run test:ci -- src/features/auth` for auth alone. Run `npm --prefix apps/web run mutation` for the web mutation gate. Backend tests mirror the Java package under [src/test/java](../apps/api/src/test/java); run `make check-api` with Docker running.

Session renewal requires a secure browser context (HTTPS or localhost) for Web Locks. Tabs on the same origin serialize cookie changes, and a sign-in or sign-out in one tab updates the others. Sign in once after upgrading from an access-token-only session.
