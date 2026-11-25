# Developer onboarding implementation plan

**Goal:** Make first use, environment diagnosis and independent service debugging understandable.
**Architecture:** Root Make targets prepare infrastructure once and launch shared app commands. Foreground execution remains default; tmux is optional. Product tutorials link to separate infrastructure labs.
**Tech stack:** Bash, Make, existing concurrently dependency, optional tmux, Node built-in test runner.
**Spec:** The approved review: correct stale docs, shorten onboarding, move orchestration out of web, add doctor and optional tmux. Do not change application contracts or quality thresholds.

- [x] Move the long walkthrough intact to a named advanced guide; update inbound anchors and replace getting started with six product steps.
- [x] Correct recommendations/HDFS/Spark responsibilities and four-image publication documentation.
- [x] Extract shared application launch environment and foreground orchestration; preserve startup guards and manual Spark execution.
- [x] Add optional named tmux session with conditional preparation, reattachment and independent service windows.
- [x] Add read-only prerequisite diagnosis with corrective messages and port inspection.
- [x] Exercise launch commands, clean/failing prerequisites, session reuse and failed preparation with Given/When/Then tests; run shell syntax and Make dry runs.
- [x] Keep tooling checks separate from application quality workflows; explain reasons and manual acceptance in the PR.

Review focus: preserve existing user environments and data; no duplicate tmux sessions on reattach; HDFS flags must reach both launch modes; printed URLs are not readiness claims; detaching leaves applications alive.
