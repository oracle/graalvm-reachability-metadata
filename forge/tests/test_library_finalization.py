# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.library_finalization import run_library_finalization


def _git(repo_path: str, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def _commit_all(repo_path: str, message: str) -> str:
    _git(repo_path, "add", "-A")
    _git(repo_path, "-c", "user.name=Forge Test", "-c", "user.email=forge@example.com", "commit", "-m", message)
    return _git(repo_path, "rev-parse", "HEAD")


class LibraryFinalizationTests(unittest.TestCase):
    def test_rejects_legacy_test_resource_native_image_config_after_split(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _git(repo_path, "init")
            with open(os.path.join(repo_path, "README.md"), "w", encoding="utf-8") as file:
                file.write("base\n")
            _commit_all(repo_path, "base")
            native_image_dir = os.path.join(
                repo_path,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(native_image_dir)
            with open(os.path.join(native_image_dir, "proxy-config.json"), "w", encoding="utf-8") as file:
                file.write("[]\n")

            with patch("utility_scripts.library_finalization._run_gradle_command", return_value=True), \
                    patch("utility_scripts.library_finalization._run_check_metadata_files_with_allowed_packages_fix") as check_metadata, \
                    patch("utility_scripts.library_finalization.run_style_fix_and_checks") as style_checks, \
                    patch("sys.stderr", new_callable=io.StringIO) as stderr:
                result = run_library_finalization(
                    repo_path=repo_path,
                    library="org.example:demo:1.0.0",
                    group="org.example",
                    artifact="demo",
                    library_version="1.0.0",
                )

        self.assertFalse(result)
        self.assertIn("proxy-config.json", stderr.getvalue())
        self.assertIn("reachability-metadata.json", stderr.getvalue())
        check_metadata.assert_not_called()
        style_checks.assert_not_called()

    def test_rejects_committed_legacy_test_resource_native_image_config_after_base_commit(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _git(repo_path, "init")
            with open(os.path.join(repo_path, "README.md"), "w", encoding="utf-8") as file:
                file.write("base\n")
            base = _commit_all(repo_path, "base")
            native_image_dir = os.path.join(
                repo_path,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(native_image_dir)
            with open(os.path.join(native_image_dir, "proxy-config.json"), "w", encoding="utf-8") as file:
                file.write("[]\n")
            _commit_all(repo_path, "generated legacy config")

            with patch("utility_scripts.library_finalization._run_gradle_command", return_value=True), \
                    patch("utility_scripts.library_finalization._run_check_metadata_files_with_allowed_packages_fix") as check_metadata, \
                    patch("utility_scripts.library_finalization.run_style_fix_and_checks") as style_checks, \
                    patch("sys.stderr", new_callable=io.StringIO) as stderr:
                result = run_library_finalization(
                    repo_path=repo_path,
                    library="org.example:demo:1.0.0",
                    group="org.example",
                    artifact="demo",
                    library_version="1.0.0",
                    base_commit=base,
                )

        self.assertFalse(result)
        self.assertIn("proxy-config.json", stderr.getvalue())
        self.assertIn("reachability-metadata.json", stderr.getvalue())
        check_metadata.assert_not_called()
        style_checks.assert_not_called()

    def test_allows_pre_existing_legacy_test_resource_native_image_config(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _git(repo_path, "init")
            native_image_dir = os.path.join(
                repo_path,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(native_image_dir)
            with open(os.path.join(native_image_dir, "proxy-config.json"), "w", encoding="utf-8") as file:
                file.write("[]\n")
            base = _commit_all(repo_path, "existing legacy config")

            with patch("utility_scripts.library_finalization._run_gradle_command", return_value=True), \
                    patch("utility_scripts.library_finalization._run_check_metadata_files_with_allowed_packages_fix", return_value=True) as check_metadata, \
                    patch("utility_scripts.library_finalization.run_style_fix_and_checks", return_value=True) as style_checks:
                result = run_library_finalization(
                    repo_path=repo_path,
                    library="org.example:demo:1.0.0",
                    group="org.example",
                    artifact="demo",
                    library_version="1.0.0",
                    base_commit=base,
                )

        self.assertTrue(result)
        check_metadata.assert_called_once()
        style_checks.assert_called_once()


if __name__ == "__main__":
    unittest.main()
