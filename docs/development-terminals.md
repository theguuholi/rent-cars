# Development terminals

`make dev` is the default: one terminal with named logs. Root-owned scripts coordinate
services; the web package only owns web commands. A service failure stops the foreground
stack; a clean exit does not terminate siblings. Ctrl+C ends the stack.

## Optional tmux

Install tmux with your OS package manager, then run:

```sh
make dev-tmux
# Or, for the HDFS lab:
make dev-tmux HDFS_ENABLED=true
```

Do not run this alongside `make dev` or separate application terminals.
The same local/Kubernetes guard runs before infrastructure preparation.
A new `rent-cars` session prepares infrastructure once and opens `api`, `web`,
`recommendations` and `tools` windows. Repeating the command attaches to that
session without starting another stack. Changing HDFS mode requires stopping
and recreating the session; attaching does not change an existing environment.

- `Ctrl+b`, then `w`: choose a window.
- `Ctrl+b`, then `d`: detach. **Applications remain running.**
- `tmux attach -t rent-cars`: return.
- To restart only the consumer, choose its window, press Ctrl+C, then use
  `Ctrl+b`, then `:` and enter `respawn-pane`. A terminated pane remains visible
  for diagnosis; respawn reruns its original command and HDFS setting.

To stop: press Ctrl+C in each application window, then run from another terminal:

```sh
tmux kill-session -t rent-cars
make infra-stop
```

This removes the terminal session and stops infrastructure without deleting volumes.
Do not use `tmux kill-server`: that would affect unrelated sessions.
Spark is never started automatically; run `make recommendations-build` from tools
only after the HDFS ingestion checkpoints pass.

The terminal session is not a service supervisor and does not survive a machine reboot.
See the [official tmux guide](https://github.com/tmux/tmux/wiki/Getting-Started).

## Diagnose before changing configuration

`make doctor` checks Java/Javac, Node, Docker, Compose and installed development
dependencies. It reports listening development ports when lsof is available.
It does not kill processes, reset data, or prove application readiness.
Use `make check-local-environment` for the Kubernetes conflict check and the
health request in [getting started](getting-started.md) after startup.

`make check-development` exercises orchestration with fake commands. It does not
start Docker, Kubernetes, Java or a real tmux session.
