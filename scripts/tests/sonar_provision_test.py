"""Provisioning is tested with a fake HTTP boundary; real Sonar is untouched."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class SonarProvisionTest(unittest.TestCase):
    def provision(self, existing=False, fail="", omit=None, empty_profiles=False, matching=False):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            curl = path / "curl"
            curl.write_text('''#!/usr/bin/env python3
import json, os, sys
args=sys.argv[1:]
endpoint=next(a for a in args if a.startswith("https://sonarcloud.io/api/"))
with open(os.environ["CALLS"], "a") as output:
    output.write(json.dumps(args)+"\\n")
if endpoint.endswith(os.environ.get("FAIL_ENDPOINT", "NEVER_MATCH")):
    sys.exit(22)
if endpoint.endswith("projects/search"):
    print(json.dumps({"components": [{"key": "consumer"}] if os.environ["EXISTING"] == "true" else []}))
elif endpoint.endswith("qualitygates/get_by_project"):
    print(json.dumps({"qualityGate": {"id": "api-gate" if "project=api" in args or os.environ["MATCHING"] == "true" else "other-gate"}}))
elif endpoint.endswith("qualityprofiles/search"):
    print(json.dumps({"profiles": [] if os.environ["EMPTY_PROFILES"] == "true" or ("project=consumer" in args and os.environ["MATCHING"] != "true") else [{"key": "java-policy"}, {"key": "yaml-policy"}]}))
else:
    print("{}")
''')
            curl.chmod(0o755)
            env = dict(os.environ, PATH=f"{path}:{os.environ['PATH']}", CALLS=str(path / "calls"),
                       SONAR_TOKEN="test-token", SONAR_ORGANIZATION="organization",
                       SONAR_API_PROJECT_KEY="api", SONAR_PROJECT_KEY="consumer",
                       EXISTING=str(existing).lower(), EMPTY_PROFILES=str(empty_profiles).lower(), MATCHING=str(matching).lower())
            env.pop("FAIL_ENDPOINT", None)
            if fail:
                env["FAIL_ENDPOINT"] = fail
            if omit:
                env.pop(omit)
            result = subprocess.run(["sh", "scripts/ci/provision-recommendations-sonar.sh"], cwd=ROOT,
                                    env=env, capture_output=True, text=True)
            calls = [json.loads(line) for line in (path / "calls").read_text().splitlines()] if (path / "calls").exists() else []
            return result, calls

    def test_given_new_project_when_provisioned_then_reuses_api_gate_and_profiles_without_changing_api(self):
        result, calls = self.provision()
        self.assertEqual(result.returncode, 0, result.stderr)
        endpoints = [next(a for a in call if a.startswith("https:")) for call in calls]
        self.assertEqual([e.rsplit("/api/", 1)[1] for e in endpoints], ["projects/search", "projects/create", "qualitygates/get_by_project", "qualitygates/get_by_project", "qualitygates/select", "qualityprofiles/search", "qualityprofiles/search", "qualityprofiles/add_project", "qualityprofiles/add_project"])
        self.assertIn("project=api", calls[2])
        self.assertIn("gateId=api-gate", calls[4])
        self.assertIn("projectKey=consumer", calls[4])
        for call in calls[-2:]:
            self.assertIn("project=consumer", call)
        self.assertIn("key=java-policy", calls[-2])
        self.assertIn("key=yaml-policy", calls[-1])

    def test_given_existing_project_when_provisioned_then_does_not_create_it_again(self):
        result, calls = self.provision(existing=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(any("https://sonarcloud.io/api/projects/create" in c for c in calls))

    def test_given_missing_configuration_when_provisioning_then_stops_before_http(self):
        for name in ["SONAR_TOKEN", "SONAR_ORGANIZATION", "SONAR_API_PROJECT_KEY", "SONAR_PROJECT_KEY"]:
            with self.subTest(name=name):
                result, calls = self.provision(omit=name)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(calls, [])

    def test_given_failed_http_operation_when_provisioning_then_fails_without_suppressing_it(self):
        for endpoint in ["projects/search", "projects/create", "qualitygates/get_by_project", "qualitygates/select", "qualityprofiles/search", "qualityprofiles/add_project"]:
            with self.subTest(endpoint=endpoint):
                result, _ = self.provision(fail=endpoint)
                self.assertNotEqual(result.returncode, 0)

    def test_given_missing_profiles_when_copying_policy_then_fails_instead_of_using_weaker_defaults(self):
        result, _ = self.provision(empty_profiles=True)
        self.assertNotEqual(result.returncode, 0)


    def test_given_matching_policy_when_checked_then_no_admin_mutation_is_required(self):
        result, calls = self.provision(existing=True, matching=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(any("POST" in call for call in calls))
