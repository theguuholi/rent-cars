---
name: rent-cars-react-feature
description: Implement or change a Rent Cars React catalog screen, form, route or API integration under web/. Use for feature work involving user-visible states and the Java HTTP contract.
---

Read the [frontend conventions](../../../web/AGENTS.md) and the [repository testing and exception policy](../../../AGENTS.md).

1. Start with a user action and its API example. Identify the URL, record identity, nullable fields and expected response. Decide what belongs in route history, component state or server cache.
2. Follow the feature's route to its page and API client. Reuse existing layout and form patterns. When introducing an edit or delete URL, open it directly for a record other than the first in the list; loading a confirmation must leave the record intact.
3. Exercise success and failure at the user-facing boundary. For edits, verify invalid input, preserved drafts on conflict and the visible state after retry or cancel. For a client change, assert the HTTP method, path, body and error behavior.
4. Run `make check-web` from the root and inspect coverage for changed modules. For layout or navigation work, verify the affected screen in a browser, including narrow width and keyboard access. Report which states you actually checked.
