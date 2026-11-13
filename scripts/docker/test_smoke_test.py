"""Readiness failure-path tests; no Docker daemon or hosted services required."""

import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location("smoke_test", Path(__file__).with_name("smoke-test.py"))
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class ReadinessTest(unittest.TestCase):
    def test_stopped_fixture_fails_before_probe_or_sleep(self):
        probe = Mock()
        with patch.object(smoke, "docker", return_value=SimpleNamespace(
                stdout='{"Running":false,"ExitCode":1}')), patch.object(smoke.time, "sleep") as sleep:
            with self.assertRaisesRegex(RuntimeError, "exit=1"):
                smoke.wait_for("MongoDB", probe, container="fixture")
        probe.assert_not_called()
        sleep.assert_not_called()

    def test_running_fixture_retries_until_probe_succeeds(self):
        probe = Mock(side_effect=[False, True])
        with patch.object(smoke, "docker", return_value=SimpleNamespace(
                stdout='{"Running":true,"ExitCode":0}')), patch.object(smoke.time, "sleep") as sleep:
            smoke.wait_for("MongoDB", probe, container="fixture")
        self.assertEqual(probe.call_count, 2)
        sleep.assert_called_once_with(2)

    def test_unready_fixture_has_bounded_polling(self):
        with patch.object(smoke.time, "monotonic", side_effect=[0, 0, 181]), patch.object(smoke.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "Timed out waiting for MongoDB"):
                smoke.wait_for("MongoDB", lambda: False)


if __name__ == "__main__":
    unittest.main()
