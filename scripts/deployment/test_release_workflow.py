"""Exercise the exact publisher snippet with fake Docker commands; never push."""

import contextlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest
from unittest.mock import patch


class ReleaseWorkflowTest(unittest.TestCase):
    repository = "ghcr.io/tlavu2004/moodly-backend"
    commit = "a" * 40
    digest = repository + "@sha256:" + "b" * 64

    @classmethod
    def setUpClass(cls):
        workflow = (Path(__file__).resolve().parents[2] / ".github/workflows/release.yml").read_text()
        step = workflow.split("      - name: Push the tested image and record its digest", 1)[1].split("      - name:", 1)[0]
        cls.publisher = textwrap.dedent(step.split("          python3 - <<'PY'\n", 1)[1].split("\n          PY", 1)[0])

    def exercise(self, revision=None, architecture="amd64", foreign=False):
        tags = [self.repository + ":sha-" + self.commit, self.repository + ":latest"]
        if foreign:
            tags[0] = "ghcr.io/another/project:latest"
        image = {"Id": "synthetic-image", "Os": "linux", "Architecture": architecture,
                 "Config": {"Labels": {"org.opencontainers.image.revision": revision or self.commit}},
                 "RepoDigests": [self.digest]}
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.output = Path(self.directory.name)
        environment = {"REGISTRY": "ghcr.io", "IMAGE_NAME": "tlavu2004/moodly-backend",
                       "GITHUB_SHA": self.commit, "IMAGE_METADATA": json.dumps({"tags": tags}),
                       "GITHUB_STEP_SUMMARY": str(self.output / "summary.md")}
        with contextlib.chdir(self.output), patch.dict(os.environ, environment), \
                patch.object(subprocess, "check_output", return_value=json.dumps([image])), \
                patch.object(subprocess, "run") as push:
            self.push = push
            exec(compile(self.publisher, "release.yml publisher fixture", "exec"), {})
        return tags

    def test_records_the_same_verified_image_digest_after_push(self):
        tags = self.exercise()
        record = json.loads((self.output / "release-image.json").read_text())
        self.assertEqual(record["commit"], self.commit)
        self.assertEqual(record["images"], [self.digest])
        self.assertEqual(record["repository"], self.repository)
        self.assertEqual([call.args[0] for call in self.push.call_args_list],
                         [["docker", "push", tag] for tag in tags])

    def test_revision_mismatch_fails_before_any_push(self):
        with self.assertRaisesRegex(RuntimeError, "revision"):
            self.exercise(revision="c" * 40)
        self.push.assert_not_called()

    def test_wrong_platform_fails_before_any_push(self):
        with self.assertRaisesRegex(RuntimeError, "linux/amd64"):
            self.exercise(architecture="arm64")
        self.push.assert_not_called()

    def test_foreign_repository_tag_fails_before_any_push(self):
        with self.assertRaisesRegex(RuntimeError, "expected repository"):
            self.exercise(foreign=True)
        self.push.assert_not_called()


if __name__ == "__main__":
    unittest.main()
