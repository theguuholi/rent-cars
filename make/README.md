# Make commands by responsibility

Run commands from the repository root, for example `make dev`, `make check-api`
or `make k8s-up`. The root Makefile includes these files in one dependency graph;
these fragments are not standalone Makefiles and do not change directories.

| File | Responsibility |
| --- | --- |
| `development.mk` | Start the complete local application stack. |
| `api.mk` | Start and verify the Spring API. |
| `web.mk` | Install, start and verify React. |
| `recommendations.mk` | Start and verify the Kafka consumer. |
| `infrastructure.mk` | Compose services, database operations and local environment guard. |
| `kafka.mk` | Local broker/topic initialization and message inspection. |
| `k8s.mk` | Kubernetes deployment, safe stop, destructive reset and environment guard. |

The root Makefile owns help and aggregate verification (`check` and `check-java`).
Public command names are unchanged. `KAFKA_ENABLED=false` still disables Kafka
preparation in `make k8s-up`. Database and Kubernetes reset commands remain
explicit destructive operations; splitting files does not change their behavior.

Keep each target in its owning file. Dependencies may refer to targets in another
included file, so shared prerequisites run once per Make invocation. Add new
category files explicitly to the root includes instead of relying on wildcards.
