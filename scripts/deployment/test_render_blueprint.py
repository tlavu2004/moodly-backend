import copy
from pathlib import Path
import unittest

from render_blueprint import render


class RenderBlueprintTest(unittest.TestCase):
    repository = "ghcr.io/tlavu2004/moodly-backend"

    def setUp(self):
        self.record = {"commit": "a" * 40, "repository": self.repository,
                       "images": [self.repository + "@sha256:" + "b" * 64]}
        self.template = (Path(__file__).resolve().parents[2] / "render.yaml").read_text(encoding="utf-8")

    def test_changes_only_the_reviewed_image_identity(self):
        result = render(self.record, self.template, self.repository)
        self.assertIn("# Image source commit: " + "a" * 40, result)
        self.assertIn("url: " + self.record["images"][0], result)
        before = [line for line in self.template.splitlines() if not line.startswith("# Image source commit:") and "url: ghcr.io/" not in line]
        after = [line for line in result.splitlines() if not line.startswith("# Image source commit:") and "url: ghcr.io/" not in line]
        self.assertEqual(before, after)

    def test_rejects_mutable_foreign_and_ambiguous_images(self):
        for images in [[], [self.repository + ":latest"],
                       ["ghcr.io/another/project@sha256:" + "b" * 64],
                       self.record["images"] * 2]:
            record = copy.deepcopy(self.record)
            record["images"] = images
            with self.assertRaises(ValueError):
                render(record, self.template, self.repository)

    def test_rejects_incomplete_commit_and_repository_mismatch(self):
        for change in [{"commit": "abcdef0"}, {"repository": "ghcr.io/another/project"}]:
            with self.assertRaises(ValueError):
                render(dict(self.record, **change), self.template, self.repository)

    def test_rejects_missing_or_duplicate_template_targets(self):
        for template in ["services: []\n", self.template + "\n      url: " + self.record["images"][0]]:
            with self.assertRaises(ValueError):
                render(self.record, template, self.repository)


if __name__ == "__main__":
    unittest.main()
