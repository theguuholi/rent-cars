"""Given/When/Then contracts for the required auto-merge barrier."""

import importlib.util
import json
import os
import runpy
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "merge_readiness", Path(__file__).resolve().parents[1] / "ci" / "merge_readiness.py"
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def workflow(identifier, path, status="completed", conclusion="success"):
    return dict(id=identifier, path=path, name=path, status=status, conclusion=conclusion)


def baseline():
    return [workflow(20 + index, path) for index, path in enumerate(sorted(module.BASELINE))]


class ReadinessTest(unittest.TestCase):
    def test_given_missing_baseline_when_checked_then_waits_instead_of_merging_early(self):
        self.assertEqual(module.readiness([], 99)[0], "waiting")

    def test_given_pending_mutation_when_baseline_passes_then_waits_for_mutation(self):
        state, message = module.readiness(baseline() + [workflow(3, "mutation", "in_progress", None)], 99)
        self.assertEqual(state, "waiting")
        self.assertIn("mutation", message)

    def test_given_failed_cancelled_or_skipped_workflow_when_complete_then_blocks_merge(self):
        for conclusion in ["failure", "cancelled", "skipped", "timed_out", "action_required"]:
            with self.subTest(conclusion=conclusion):
                self.assertEqual(module.readiness(baseline() + [workflow(3, "mutation", conclusion=conclusion)], 99)[0], "failed")

    def test_given_successful_workflows_and_self_when_checked_then_does_not_wait_for_itself(self):
        self.assertEqual(module.readiness(baseline() + [workflow(99, "self", "in_progress", None)], 99)[0], "ready")

    def test_given_new_successful_run_when_old_run_failed_then_uses_latest_regardless_of_order(self):
        old = workflow(3, "mutation", conclusion="failure")
        new = workflow(4, "mutation")
        for runs in [[old, new], [new, old]]:
            self.assertEqual(module.readiness(baseline() + runs, 99)[0], "ready")

    @patch.object(module.subprocess, "run")
    def test_given_paginated_api_when_fetched_then_checks_all_runs_for_exact_pr_commit(self, run):
        run.return_value.stdout = json.dumps([{"workflow_runs": baseline()}, {"workflow_runs": [workflow(3, "mutation")]}])
        self.assertEqual(len(module.fetch_runs("owner/repo", "abc")), len(module.BASELINE) + 1)
        args = run.call_args.args[0]
        self.assertIn("head_sha=abc", args)
        self.assertIn("event=pull_request", args)
        self.assertIn("--paginate", args)
        self.assertTrue(run.call_args.kwargs["check"])

    @patch.object(module.subprocess, "run", side_effect=subprocess.CalledProcessError(1, "gh"))
    def test_given_api_failure_when_fetched_then_does_not_report_success(self, _run):
        with self.assertRaises(subprocess.CalledProcessError):
            module.fetch_runs("owner/repo", "abc")

    @patch.object(module.time, "sleep")
    @patch.object(module, "fetch_runs")
    def test_given_pending_then_passed_when_waiting_then_returns_success(self, fetch, sleep):
        fetch.side_effect = [[], baseline()]
        self.assertEqual(module.wait_for_workflows("owner/repo", "abc", 99, attempts=2), 0)
        sleep.assert_called_once_with(20)

    @patch.object(module, "fetch_runs", return_value=baseline() + [workflow(3, "mutation", conclusion="failure")])
    def test_given_failed_pipeline_when_waiting_then_returns_failure(self, _fetch):
        self.assertEqual(module.wait_for_workflows("owner/repo", "abc", 99), 1)

    @patch.object(module.time, "sleep")
    @patch.object(module, "fetch_runs", return_value=[])
    def test_given_pipeline_never_finishes_when_timeout_then_blocks_merge(self, _fetch, sleep):
        self.assertEqual(module.wait_for_workflows("owner/repo", "abc", 99, attempts=1), 1)
        sleep.assert_called_once_with(20)


    def test_given_previous_readiness_run_when_reopened_then_does_not_wait_on_old_gate(self):
        runs = baseline() + [workflow(3, ".github/workflows/merge-readiness.yml", conclusion="cancelled")]
        self.assertEqual(module.readiness(runs, 99)[0], "ready")

    @patch("subprocess.run")
    def test_given_workflow_environment_when_script_runs_then_checks_that_head_and_exits_success(self, run):
        run.return_value.stdout = json.dumps([{"workflow_runs": baseline()}])
        with patch.dict(os.environ, GITHUB_REPOSITORY="owner/repo", PR_HEAD_SHA="abc", GITHUB_RUN_ID="99"):
            with self.assertRaises(SystemExit) as result:
                runpy.run_path(str(Path(module.__file__)), run_name="__main__")
        self.assertEqual(result.exception.code, 0)
        self.assertIn("head_sha=abc", run.call_args.args[0])
