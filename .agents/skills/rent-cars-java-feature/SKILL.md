---
name: rent-cars-java-feature
description: Implement or change a Rent Cars Java API resource, domain rule, persistence operation or HTTP contract. Use for Spring Boot feature work in apps/api/.
---

Read the [backend conventions](../../../apps/api/AGENTS.md) and the [repository testing and exception policy](../../../AGENTS.md).

1. Trace the nearest HTTP example from `requests/` through its controller to persistence and tests. Write down the change in terms of one successful request and its important failure modes. For updates, distinguish a missing record, omitted field, explicit null and conflicting value where relevant.
2. Choose the smallest layer that owns the change. For a schema change, inspect existing migrations, distinguish sample data from shared data and consider existing rows. For a multi-step mutation, determine which writes must commit or roll back together and which requests might race.
3. Implement through the real entry point. Align validation, database guarantees, response status and OpenAPI with the agreed contract. Adjust the executable request when it changes.
4. Add regression tests that would fail with the old behavior. For concurrency, coordinate competing transactions and verify the final persisted state. Run `make check-api` from the root; inspect the JaCoCo source report. For mutation-sensitive domain changes, use the PIT command in the README.
