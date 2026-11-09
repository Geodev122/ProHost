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
import shots  # noqa: E402


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

    def test_changed_files_report_the_bounding_box_of_the_difference(self):
        png(self.g / "a.png")
        img = Image.new("RGBA", (40, 30), (10, 20, 30, 255))
        for xy in ((5, 7), (9, 12)):
            img.putpixel(xy, (255, 255, 255, 255))
        self.c.mkdir(parents=True, exist_ok=True)
        img.save(self.c / "a.png")
        r =diff_shots.run(self.g, self.c, [], 0, 0.0)["results"][0]
        self.assertEqual([5, 7, 5, 6], r["bbox"])
        png(self.c / "b.png")
        png(self.g / "b.png")
        same = [x for x in diff_shots.run(self.g, self.c, [], 0, 0.0)["results"] if x["name"] == "b.png"][0]
        self.assertIsNone(same["bbox"])

    def test_report_and_json_are_written(self):
        png(self.g / "a.png")
        png(self.c / "a.png", dot=(1, 1))
        out = self.tmp / "out"
        self.assertEqual(1, self.run_diff("--report", str(out / "r.html"), "--json", str(out / "r.json")))
        self.assertIn("a.png", (out / "r.html").read_text())
        self.assertEqual(1, json.loads((out / "r.json").read_text())["failed"])


class MaskTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.g, self.c = self.tmp / "g", self.tmp / "c"
        png(self.g / "request_detail_light.png")
        png(self.c / "request_detail_light.png", dot=(10, 10))
        png(self.g / "other.png")
        png(self.c / "other.png", dot=(10, 10))

    def masks(self, data):
        path = self.tmp / "masks.json"
        path.write_text(json.dumps(data))
        return str(path)

    def run_diff(self, masks_path):
        return diff_shots.main(["--golden", str(self.g), "--candidate", str(self.c), "--masks", masks_path])

    def test_masked_region_hides_a_known_dynamic_change_only_for_matching_files(self):
        m = self.masks({"_comment": "docs", "request_detail_*.png": [[8, 8, 6, 6]]})
        self.assertEqual(1, self.run_diff(m))  # other.png still differs
        summary = diff_shots.run(self.g, self.c, [], 0, 0.0, {"request_detail_*.png": [[8, 8, 6, 6]]})
        self.assertEqual({"request_detail_light.png": "same", "other.png": "changed"}, {r["name"]: r["status"] for r in summary["results"]})

    def test_change_outside_the_mask_is_still_caught(self):
        summary = diff_shots.run(self.g, self.c, [], 0, 0.0, {"*.png": [[0, 0, 5, 5]]})
        self.assertEqual(2, summary["failed"])

    def test_all_masked_passes(self):
        self.assertEqual(0, self.run_diff(self.masks({"*.png": [[8, 8, 6, 6]]})))


class BeforeAfterTest(unittest.TestCase):
    def test_sheets_for_changed_and_requested_files_only(self):
        import before_after
        tmp = Path(tempfile.mkdtemp())
        g, c, out = tmp / "g", tmp / "c", tmp / "o"
        for n in ("changed.png", "same.png", "other.png"):
            png(g / n)
        png(c / "changed.png", dot=(2, 2))
        png(c / "same.png")
        png(c / "other.png")
        diff_shots.main(["--golden", str(g), "--candidate", str(c), "--json", str(tmp / "d.json")])
        self.assertEqual(0, before_after.main(["--golden", str(g), "--candidate", str(c), "--out", str(out),
                                               "--from-json", str(tmp / "d.json"), "--show", "same"]))
        self.assertEqual({"changed.png", "same.png"}, {p.name for p in out.glob("*.png")})
        sheet = Image.open(out / "changed.png")
        self.assertGreater(sheet.width, 3 * 40)  # three panels side by side


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


class ShiftToleranceTests(unittest.TestCase):
    def _write(self, arr, path):
        from PIL import Image
        Image.fromarray(arr).save(path)

    def test_a_scrolled_region_matches_after_shifting_but_a_real_change_does_not(self):
        import numpy as np, tempfile, pathlib
        d = pathlib.Path(tempfile.mkdtemp())
        base = (np.random.RandomState(1).rand(200, 100, 3) * 255).astype(np.uint8)
        self._write(base, d / "g.png")
        self._write(np.roll(base, 3, axis=0), d / "c.png")
        self.assertEqual(shots.best_shift_match(d / "g.png", d / "c.png", [0, 0, 100, 200], 6), 3)
        changed = np.roll(base, 3, axis=0).copy()
        changed[100, :, :] = 0
        self._write(changed, d / "c2.png")
        self.assertIsNone(shots.best_shift_match(d / "g.png", d / "c2.png", [0, 0, 100, 200], 6))
        self.assertIsNone(shots.best_shift_match(d / "g.png", d / "c.png", [0, 0, 100, 200], 2))  # offset beyond the allowance
