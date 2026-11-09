---
name: rent-cars-playwright-journey
description: Add or change a Rent Cars Playwright browser journey under apps/web/e2e/. Use for visible navigation and user interaction; use API request tests for HTTP-only contracts.
---

Read the Playwright convention in [apps/web/AGENTS.md](../../../apps/web/AGENTS.md) and the local commands in [docs/getting-started.md](../../../docs/getting-started.md).

1. Trace the actual React route and accessible controls before writing the scenario. Choose one observable journey and avoid fixed database IDs; use existing development data or create the record through the public interface when the journey needs it.
2. Write one Given/When/Then test name and `test.step` blocks for real setup, clicks, and visible outcomes. Use Playwright role or label locators and web-first assertions. Keep direct API checks in a separate test file.
3. Run the browser scenario against `make dev`, inspect any failing step, then run `make check-web`. Update the onboarding instructions when a new browser prerequisite or command is introduced.
