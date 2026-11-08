"""Tests for the migration screenshot tools. Run: python3 -m unittest scripts/migration/test_diff_shots.py"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import compare_android_ios  # noqa: E402
import diff_shots  # noqa: E402


def png(path: Path, color=(10, 20, 30), size=(40, 30), dot=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGBA", size, color + (255,))
    if dot:
        img.putpixel(dot, (255, 255, 255, 255))
    img.save(path)


class DiffShotsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.g, self.c = self.tmp / "g", self.tmp / "c"

    def run_diff(self, *extra):
        return diff_shots.main(["--golden", str(self.g), "--candidate", str(self.c), *extra])

    def test_identical_sets_pass(self):
        for root in (self.g, self.c):
            png(root / "light/Explore.png")
            png(root / "dark/Explore.png", (1, 2, 3))
        self.assertEqual(0, self.run_diff())

    def test_single_changed_pixel_fails_and_tolerance_allows_it(self):
        png(self.g / "light/Explore.png")
        png(self.c / "light/Explore.png", dot=(3, 3))
        self.assertEqual(1, self.run_diff())
        self.assertEqual(0, self.run_diff("--max-ratio", "0.01"))

    def test_channel_tolerance_ignores_antialiasing_noise(self):
        png(self.g / "a.png", (100, 100, 100))
        png(self.c / "a.png", (102, 100, 99))
        self.assertEqual(1, self.run_diff())
        self.assertEqual(0, self.run_diff("--channel-tol", "3"))

    def test_missing_and_resized_fail(self):
        png(self.g / "a.png")
        png(self.g / "b.png")
        png(self.c / "a.png", size=(41, 30))
        self.assertEqual(1, self.run_diff())
        summary = diff_shots.run(self.g, self.c, [], 0, 0.0)
        self.assertEqual({"a.png": "resized", "b.png": "missing"}, {r["name"]: r["status"] for r in summary["results"]})

    def test_only_limits_the_comparison_and_empty_selection_fails(self):
        png(self.g / "light/Explore.png")
        png(self.g / "light/Profile.png")
        png(self.c / "light/Explore.png")
        self.assertEqual(0, self.run_diff("--only", "light/Explore"))
        self.assertEqual(1, self.run_diff("--only", "nothing/"))

    def test_report_and_json_are_written(self):
        png(self.g / "a.png")
        png(self.c / "a.png", dot=(1, 1))
        out = self.tmp / "out"
        self.assertEqual(1, self.run_diff("--report", str(out / "r.html"), "--json", str(out / "r.json")))
        self.assertIn("a.png", (out / "r.html").read_text())
        self.assertEqual(1, json.loads((out / "r.json").read_text())["failed"])


class CompareAndroidIosTest(unittest.TestCase):
    def test_sheets_and_missing_lists(self):
        tmp = Path(tempfile.mkdtemp())
        png(tmp / "a/x.png")
        png(tmp / "a/y.png")
        png(tmp / "i/x.png", size=(50, 100))
        self.assertEqual(0, compare_android_ios.main(["--android", str(tmp / "a"), "--ios", str(tmp / "i"), "--out", str(tmp / "o")]))
        self.assertTrue((tmp / "o/x.png").exists())
        index = (tmp / "o/index.html").read_text()
        self.assertIn("y.png", index)
        self.assertIn("1 missing on iOS", index)


if __name__ == "__main__":
    unittest.main()
