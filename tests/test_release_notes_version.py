import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class ReleaseNotesVersion(unittest.TestCase):
    def test_release_notes_match_gradle_and_manual_workflow_example(self):
        gradle = (ROOT / 'app/build.gradle').read_text()
        notes = (ROOT / '.github/release-notes.md').read_text()
        readme = (ROOT / 'README.md').read_text()

        version = re.search(r"versionName '([^']+)'", gradle).group(1)
        code = re.search(r'versionCode (\d+)', gradle).group(1)
        self.assertIn(f'\n{version}\nversionCode: {code}\n', notes)
        self.assertIn(f'\nv{version}\n', readme)
        self.assertIn('به معنی ساخته‌شدن، امضاشدن یا انتشار فایل APK جدید نیست.', notes)


if __name__ == '__main__':
    unittest.main()
