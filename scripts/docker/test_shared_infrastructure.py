"""Negative safety and cross-process lease tests; no Docker or data reset required."""

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

MODULE_PATH = Path(__file__).with_name("shared-infrastructure.py")
SPEC = importlib.util.spec_from_file_location("shared_infrastructure", MODULE_PATH)
shared = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(shared)


class IsolationGuardsTest(unittest.TestCase):
    def test_distinct_local_resources_are_accepted(self):
        shared.validate_resources("moodly_local", "daily_entries_search")

    def test_collisions_and_unknown_targets_are_rejected(self):
        for database, index in (("moodly_test", "daily_entries_search"),
                                ("moodly_local", "daily_entries_search_test"),
                                ("", "daily_entries_search"), ("moodly_local", ""), ("moodly_local", "*"),
                                ("moodly_local", "_all"), ("moodly_local", "local,test")):
            with self.subTest(database=database, index=index), self.assertRaises(ValueError):
                shared.validate_resources(database, index)
        for database, index in (("moodly_local", "daily_entries_search_test"),
                                ("moodly_test_backup", "daily_entries_search_test"),
                                ("moodly_test", "*"), ("moodly_test", "_all"),
                                ("moodly_test", "daily_entries_search_test,local")):
            with self.subTest(database=database, index=index), self.assertRaises(ValueError):
                shared.validate_resources("moodly_local", "daily_entries_search", database, index)

    def test_caller_settings_cannot_reconfigure_shared_services(self):
        with patch.dict(os.environ, {"MONGODB_PORT": "9999", "ELASTICSEARCH_PORT": "9998",
                                     "MOODLY_SEARCH_INDEX_NAME": "wrong", "MOODLY_CDC_ENABLED": "true"}):
            environment = shared.compose_environment()
        self.assertNotIn("MONGODB_PORT", environment)
        self.assertNotIn("ELASTICSEARCH_PORT", environment)
        self.assertNotIn("MOODLY_SEARCH_INDEX_NAME", environment)
        self.assertEqual("true", environment["MOODLY_CDC_ENABLED"])

    def test_configuration_rejects_public_bindings_before_returning_targets(self):
        services = {
            "mongodb": {"labels": {"io.moodly.local-database": "moodly_local"},
                        "environment": {"MONGODB_CONTAINER_PORT": "27017", "MONGODB_REPLICA_SET": "rs0"},
                        "ports": [{"host_ip": "0.0.0.0", "published": "27017"}]},
            "elasticsearch": {"labels": {"io.moodly.local-index": "daily_entries_search"},
                              "ports": [{"host_ip": "127.0.0.1", "published": "9200"}]},
        }
        with patch.object(shared, "compose", return_value=json.dumps({"services": services})):
            with self.assertRaises(ValueError):
                shared.configuration()

    def test_lease_rejects_another_process_and_releases_after_exception(self):
        with tempfile.TemporaryDirectory() as temporary:
            lock = Path(temporary) / "test.lock"
            probe = (
                "import importlib.util,pathlib; "
                f"s=importlib.util.spec_from_file_location('shared',{str(MODULE_PATH)!r}); "
                "m=importlib.util.module_from_spec(s);s.loader.exec_module(m);"
                f"m.LOCK_FILE=pathlib.Path({str(lock)!r}); "
                "\nwith m.test_lease(): print('acquired')"
            )
            with patch.object(shared, "LOCK_FILE", lock):
                with self.assertRaisesRegex(RuntimeError, "synthetic failure"):
                    with shared.test_lease():
                        result = subprocess.run([sys.executable, "-c", probe], capture_output=True, text=True)
                        self.assertNotEqual(0, result.returncode)
                        self.assertIn("Test resources are in use", result.stderr)
                        raise RuntimeError("synthetic failure")
                result = subprocess.run([sys.executable, "-c", probe], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual("acquired", result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
