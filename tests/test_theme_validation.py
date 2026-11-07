"""Theme JSON must be an object before a script contacts the TV."""
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("validate_theme_config", ROOT / "tools" / "validate_theme_config.py")
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class ThemeValidationTest(unittest.TestCase):
    def check_config(self, content):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "config.json"
            path.write_text(content, encoding="utf-8-sig")
            with contextlib.redirect_stderr(io.StringIO()) as errors:
                result = validator.main(str(path))
            return result, errors.getvalue()

    def test_objects_are_accepted(self):
        for data in ('{}', '{"dock_apps": []}'):
            self.assertEqual(0, self.check_config(data)[0])

    def test_non_objects_and_malformed_json_are_rejected(self):
        for data in ('[]', 'null', 'true', '3', '"text"'):
            result, error = self.check_config(data)
            self.assertEqual(1, result)
            self.assertIn("not a JSON object", error)
        result, error = self.check_config('{')
        self.assertEqual(1, result)
        self.assertIn("not valid JSON", error)
