# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import os
import tempfile
import unittest
from contextlib import contextmanager, redirect_stderr
from pathlib import Path
from typing import Iterator
from unittest.mock import patch

from utility_scripts.host_requirements import GRAALVM_SCHEMA_PATH
from utility_scripts.workflow_setup import resolve_graalvm_java_home


@contextmanager
def _graalvm_home(native_image: bool = True, schema: bool = True) -> Iterator[str]:
    """Create a GraalVM home whose Native Image and schema presence can be controlled."""
    with tempfile.TemporaryDirectory() as temp_dir:
        bin_dir = Path(temp_dir) / "bin"
        bin_dir.mkdir()
        for executable in ["java", "native-image"] if native_image else ["java"]:
            executable_path = bin_dir / executable
            executable_path.write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
            executable_path.chmod(0o755)
        if schema:
            schema_path = Path(temp_dir) / GRAALVM_SCHEMA_PATH
            schema_path.parent.mkdir(parents=True)
            schema_path.touch()
        yield temp_dir


class ResolveGraalvmJavaHomeTests(unittest.TestCase):
    def test_a_graalvm_home_without_the_schema_is_not_forge_usable(self) -> None:
        with _graalvm_home(schema=False) as without_schema, _graalvm_home() as complete:
            environment = {
                "GRAALVM_HOME": without_schema,
                "JAVA_HOME": complete,
                "GRAALVM_HOME_25_0": complete,
                "GRADLE_JAVA_HOME": "/agent-less-jdk",
                "GRADLE_OPTS": "-Xmx2g -Dorg.gradle.java.home=/agent-less-jdk",
            }
            with patch.dict(os.environ, environment, clear=True):
                resolved = resolve_graalvm_java_home()

                self.assertEqual(complete, resolved)
                self.assertEqual(complete, os.environ["GRAALVM_HOME"])
                self.assertEqual(complete, os.environ["JAVA_HOME"])
                self.assertEqual(complete, os.environ["GRADLE_JAVA_HOME"])
                self.assertTrue(
                    os.environ["GRADLE_OPTS"].endswith(f"-Dorg.gradle.java.home={complete}")
                )

    def test_no_forge_usable_home_names_every_rejection_once(self) -> None:
        stderr = io.StringIO()
        with _graalvm_home(native_image=False) as without_native_image, \
                _graalvm_home(schema=False) as without_schema:
            environment = {"GRAALVM_HOME": without_native_image, "JAVA_HOME": without_schema}
            with patch.dict(os.environ, environment, clear=True), redirect_stderr(stderr):
                with self.assertRaises(SystemExit) as exit_context:
                    resolve_graalvm_java_home()

        self.assertEqual(1, exit_context.exception.code)
        self.assertIn(os.path.join("bin", "native-image") + " is missing or not executable", stderr.getvalue())
        self.assertIn(f"{GRAALVM_SCHEMA_PATH} is missing", stderr.getvalue())
        self.assertEqual(1, stderr.getvalue().count("Fix:"))


if __name__ == "__main__":
    unittest.main()
