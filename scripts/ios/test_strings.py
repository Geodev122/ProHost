import json
import sys
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).parent))
import strings_convert as sc  # noqa: E402
import check_strings_parity as cp  # noqa: E402


def text(xml):
    return sc.android_text(ET.fromstring(xml))


class Convert(unittest.TestCase):
    def test_escapes_and_spaces(self):
        self.assertEqual(text(r'<string>You\'re   the owner</string>'), "You're the owner")
        bs = chr(92)
        self.assertEqual(text("<string>Allow" + bs + "u0020</string>"), "Allow ")  # an escaped trailing space survives
        self.assertEqual(text(r'<string>a\nb</string>'), "a\nb")
        self.assertEqual(text('<string>"  kept  "</string>'), "  kept  ")
        self.assertEqual(text("<string>Accept &amp; Share</string>"), "Accept & Share")

    def test_formats(self):
        self.assertEqual(sc.to_ios_format("%1$s of %2$d"), "%1$@ of %2$lld")
        self.assertEqual(sc.to_ios_format("%s and %d"), "%@ and %lld")
        self.assertEqual(sc.to_ios_format("100%% %02d %1$.1f"), "100%% %02lld %1$.1f")
        self.assertEqual(sc.placeholders("%1$d%% VS %2$lld %@"), ["%1$d", "%2$lld", "%@"])

    def test_plurals_and_missing(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t)
            for folder, one in (("values", "One date"), ("values-ar", "تاريخ واحد")):
                (r / folder).mkdir()
                (r / folder / "strings.xml").write_text('<resources><string name="hi">Hi %1$s</string></resources>', encoding="utf-8")
                (r / folder / "plurals.xml").write_text(
                    f'<resources><plurals name="dates"><item quantity="one">{one}</item><item quantity="other">%d dates</item></plurals></resources>',
                    encoding="utf-8")
            cat, _ = sc.build(r)
            self.assertEqual(cat["strings"]["hi"]["localizations"]["en"]["stringUnit"]["value"], "Hi %1$@")
            plural = cat["strings"]["dates"]["localizations"]["ar"]["variations"]["plural"]
            self.assertEqual(plural["other"]["stringUnit"]["value"], "%lld dates")
            self.assertEqual(cp.problems(cat), [])
            del cat["strings"]["hi"]["localizations"]["ar"]
            self.assertEqual(cp.problems(cat), ["hi: missing ar"])
            cat["strings"]["hi"]["localizations"]["ar"] = sc.unit("مرحبا")
            self.assertTrue(any("placeholders differ" in p for p in cp.problems(cat)))


if __name__ == "__main__":
    unittest.main()
