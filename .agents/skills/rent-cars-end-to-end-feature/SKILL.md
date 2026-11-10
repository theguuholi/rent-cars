---
name: rent-cars-end-to-end-feature
description: Deliver a user-visible Rent Cars feature across Playwright, React, the Spring API, HTTP Yac, Flyway, and a QA-ready pull request. Use when a request changes a person's journey through the app.
---

Read [the feature workflow](../../../docs/feature-workflow.md) and the repository [AGENTS.md](../../../AGENTS.md). Use the existing [Playwright](../rent-cars-playwright-journey/SKILL.md), [React](../rent-cars-react-feature/SKILL.md), and [Java](../rent-cars-java-feature/SKILL.md) skills for their respective layers.

Start by writing and running the browser acceptance scenario so it describes the user's actions and fails for the missing behavior. Define the HTTP contract and focused React and Java tests before completing the implementation. Build the persistence and API contract, connect the React interface, then make the Playwright journey pass against the running stack.

Finish with the checks and the QA checklist in the feature workflow. Report exactly what ran and what remains unverified. Respect the requested scope; when the user asks for only one layer, do not implement the others.
