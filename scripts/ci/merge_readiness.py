"""Wait for all PR workflows before GitHub may squash an opted-in PR."""

import json
import os
import subprocess
import time

BASELINE = {
    ".github/workflows/api-quality.yml",
    ".github/workflows/api-mutation.yml",
    ".github/workflows/frontend-quality.yml",
    ".github/workflows/frontend-mutation.yml",
    ".github/workflows/frontend-e2e.yml",
    ".github/workflows/recommendations-quality.yml",
    ".github/workflows/recommendations-mutation.yml",
}


def readiness(runs, own_run_id):
    latest = {}
    for run in runs:
        if run["id"] == own_run_id or run["path"] == ".github/workflows/merge-readiness.yml":
            continue
        path = run["path"]
        if path not in latest or run["id"] > latest[path]["id"]:
            latest[path] = run
    if not BASELINE.issubset(latest):
        return "waiting", "Waiting for API, frontend and recommendations workflows to register"
    pending = [r["name"] for r in latest.values() if r["status"] != "completed"]
    if pending:
        return "waiting", "Waiting for: " + ", ".join(sorted(pending))
    failed = [r["name"] for r in latest.values() if r["conclusion"] != "success"]
    if failed:
        return "failed", "Workflows did not succeed: " + ", ".join(sorted(failed))
    return "ready", "All PR workflows succeeded for this commit"


def fetch_runs(repository, sha):
    result = subprocess.run(
        ["gh", "api", "--method", "GET", "--paginate", "--slurp",
         f"repos/{repository}/actions/runs", "-f", f"head_sha={sha}",
         "-f", "event=pull_request", "-f", "per_page=100"],
        check=True, text=True, capture_output=True,
    )
    return [run for page in json.loads(result.stdout) for run in page["workflow_runs"]]


def wait_for_workflows(repository, sha, own_run_id, attempts=120):
    for _ in range(attempts):
        state, message = readiness(fetch_runs(repository, sha), own_run_id)
        print(message, flush=True)
        if state == "ready":
            return 0
        if state == "failed":
            return 1
        time.sleep(20)
    print("Timed out waiting for PR workflows", flush=True)
    return 1


if __name__ == "__main__":
    raise SystemExit(wait_for_workflows(
        os.environ["GITHUB_REPOSITORY"], os.environ["PR_HEAD_SHA"], int(os.environ["GITHUB_RUN_ID"])
    ))
