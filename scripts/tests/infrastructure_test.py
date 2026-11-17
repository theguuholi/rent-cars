"""Given/When/Then deployment contracts; no registry or live cluster is modified."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def run(*command, **kwargs):
    return subprocess.run(command, cwd=ROOT, text=True, capture_output=True, **kwargs)


class PublishImagesTest(unittest.TestCase):
    def publish(self, namespace=None, fail=None):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            docker = path / "docker"
            docker.write_text(
                '#!/bin/sh\n'
                'printf "%s\\n" "$*" >> "$DOCKER_CALLS"\n'
                '[ "$*" != "${FAIL_COMMAND:-}" ]\n'
            )
            docker.chmod(0o755)
            env = dict(os.environ, PATH=f"{path}:{os.environ['PATH']}",
                       DOCKER_CALLS=str(path / "calls"))
            env.pop("IMAGE_NAMESPACE", None)
            env.pop("FAIL_COMMAND", None)
            if namespace is not None:
                env["IMAGE_NAMESPACE"] = namespace
            if fail is not None:
                env["FAIL_COMMAND"] = fail
            result = run("sh", "scripts/push-images.sh", env=env)
            return result, (path / "calls").read_text().splitlines()

    def test_given_default_registry_when_publishing_then_builds_all_before_any_push(self):
        result, calls = self.publish()
        self.assertEqual(result.returncode, 0, result.stderr)
        apps = ["api", "web", "recommendations"]
        self.assertEqual(calls,
                         [f"build --tag docker.io/guuhworship/rent-cars-{a}:latest apps/{a}" for a in apps]
                         + [f"push docker.io/guuhworship/rent-cars-{a}:latest" for a in apps])

    def test_given_custom_namespace_when_publishing_then_all_tags_use_it_without_double_slash(self):
        result, calls = self.publish("registry.example/team/")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(calls), 6)
        self.assertTrue(all("registry.example/team/rent-cars-" in call for call in calls))
        self.assertFalse(any("//" in call for call in calls))

    def test_given_any_failed_build_when_publishing_then_nothing_is_pushed(self):
        for index, app in enumerate(["api", "web", "recommendations"]):
            with self.subTest(app=app):
                result, calls = self.publish(fail=f"build --tag docker.io/guuhworship/rent-cars-{app}:latest apps/{app}")
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(len(calls), index + 1)
                self.assertFalse(any(call.startswith("push") for call in calls))

    def test_given_failed_push_when_publishing_then_stops_and_reports_failure(self):
        for index, app in enumerate(["api", "web", "recommendations"]):
            with self.subTest(app=app):
                result, calls = self.publish(fail=f"push docker.io/guuhworship/rent-cars-{app}:latest")
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(len(calls), 4 + index)


class HelmConsumerTest(unittest.TestCase):
    def render(self, *arguments):
        result = run("helm", "template", "fleet", "./helm", *arguments)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def test_given_default_chart_when_rendered_then_consumer_has_image_security_and_kafka_settings(self):
        rendered = self.render()
        consumer = next(doc for doc in rendered.split("\n---") if "name: fleet-recommendations" in doc)
        for expected in ["kind: Deployment", "replicas: 1", "rent-cars-recommendations:latest",
                         "imagePullPolicy: Always", "runAsNonRoot: true", "readOnlyRootFilesystem: true",
                         "automountServiceAccountToken: false", "rent-kafka-bootstrap.rent-kafka.svc.cluster.local:9092",
                         'value: "rentals.events"', 'value: "rent-cars-recommendations-v1"', "mountPath: /tmp"]:
            self.assertIn(expected, consumer)
        self.assertNotIn("containerPort", consumer)
        self.assertNotIn("kind: Service", consumer)

    def test_given_disabled_messaging_or_consumer_when_rendered_then_consumer_is_absent(self):
        for setting in ["messaging.enabled=false", "recommendations.enabled=false"]:
            with self.subTest(setting=setting):
                rendered = self.render("--set", setting)
                self.assertNotIn("name: fleet-recommendations", rendered)
                self.assertIn("name: fleet-api", rendered)
                self.assertIn("name: fleet-web", rendered)

    def test_given_custom_settings_when_rendered_then_image_group_and_broker_are_overridden(self):
        rendered = self.render("--set", "recommendations.image.repository=registry.example/team/consumer",
                               "--set-string", "recommendations.image.tag=review",
                               "--set", "recommendations.groupId=study",
                               "--set", "messaging.bootstrapServers=broker:9092",
                               "--set", "messaging.topic=study.events")
        for expected in ["registry.example/team/consumer:review", 'value: "study"',
                         'value: "broker:9092"', 'value: "study.events"']:
            self.assertIn(expected, rendered)

    def test_given_k8s_up_when_planned_then_kafka_precedes_application_and_consumer_restarts(self):
        result = run("make", "-n", "k8s-up")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertLess(result.stdout.index("strimzi-kafka-operator"), result.stdout.index("helm upgrade --install fleet"))
        self.assertIn("rollout restart deployment/fleet-recommendations", result.stdout)
        result = run("make", "-n", "k8s-up", "KAFKA_ENABLED=false")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("strimzi-kafka-operator", result.stdout)
        self.assertIn("--set messaging.enabled=false", result.stdout)
