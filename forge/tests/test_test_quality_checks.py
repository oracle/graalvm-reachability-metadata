# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest

from utility_scripts.test_quality_checks import (
    SCAFFOLD_PLACEHOLDER_TEXT,
    cleanup_scaffold_placeholder_tests,
    format_placeholder_occurrence,
)


class ScaffoldPlaceholderCleanupTests(unittest.TestCase):
    def test_removes_untouched_kotlin_scaffold_when_real_test_exists(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            self._init_git_repo(tmp_dir)
            placeholder_file = os.path.join(tmp_dir, "ExampleTest.kt")
            real_test_file = os.path.join(tmp_dir, "RealTest.kt")
            self._write_file(placeholder_file, self._kotlin_placeholder_source())
            scaffold_commit = self._commit_all(tmp_dir, "scaffold")
            self._write_file(
                real_test_file,
                """
package org.example

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RealTest {
    @Test
    fun exercisesLibrary() {
        assertThat("value").isEqualTo("value")
    }
}
""",
            )

            result = cleanup_scaffold_placeholder_tests(tmp_dir, tmp_dir, scaffold_commit)

            self.assertEqual(result.removed_files, [placeholder_file])
            self.assertFalse(os.path.exists(placeholder_file))
            self.assertTrue(os.path.exists(real_test_file))
            self.assertEqual(result.remaining_placeholders, [])

    def test_keeps_and_reports_placeholder_when_no_real_test_exists(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            self._init_git_repo(tmp_dir)
            placeholder_file = os.path.join(tmp_dir, "ExampleTest.java")
            self._write_file(
                placeholder_file,
                """
package org.example;

import org.junit.jupiter.api.Test;

class ExampleTest {
    @Test
    void test() throws Exception {
        System.out.println("This is just a placeholder, implement your test");
    }
}
""",
            )
            scaffold_commit = self._commit_all(tmp_dir, "scaffold")

            result = cleanup_scaffold_placeholder_tests(tmp_dir, tmp_dir, scaffold_commit)

            self.assertEqual(result.removed_files, [])
            self.assertTrue(os.path.exists(placeholder_file))
            self.assertEqual(len(result.remaining_placeholders), 1)
            self.assertEqual(
                format_placeholder_occurrence(result.remaining_placeholders[0], tmp_dir),
                "ExampleTest.java:8",
            )

    def test_reports_placeholder_when_scaffold_file_changed_but_placeholder_remains(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            self._init_git_repo(tmp_dir)
            placeholder_file = os.path.join(tmp_dir, "MixedTest.kt")
            real_test_file = os.path.join(tmp_dir, "RealTest.kt")
            self._write_file(placeholder_file, self._kotlin_placeholder_source())
            scaffold_commit = self._commit_all(tmp_dir, "scaffold")
            self._write_file(
                placeholder_file,
                f"""
package org.example

import org.junit.jupiter.api.Test

class MixedTest {{
    @Test
    fun test() {{
        println("{SCAFFOLD_PLACEHOLDER_TEXT}")
    }}

    @Test
    fun realTest() {{
        check("value".isNotEmpty())
    }}
}}
""",
            )
            self._write_file(real_test_file, "class RealTest\n")

            result = cleanup_scaffold_placeholder_tests(tmp_dir, tmp_dir, scaffold_commit)

            self.assertEqual(result.removed_files, [])
            self.assertTrue(os.path.exists(placeholder_file))
            self.assertEqual(len(result.remaining_placeholders), 1)

    @staticmethod
    def _write_file(file_path: str, content: str) -> None:
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        with open(file_path, "w", encoding="utf-8") as output_file:
            output_file.write(content.lstrip())

    @staticmethod
    def _init_git_repo(repo_path: str) -> None:
        subprocess.run(["git", "init", "-b", "main"], cwd=repo_path, check=True, stdout=subprocess.DEVNULL)
        subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=repo_path, check=True)
        subprocess.run(["git", "config", "user.name", "Test User"], cwd=repo_path, check=True)

    @staticmethod
    def _commit_all(repo_path: str, message: str) -> str:
        subprocess.run(["git", "add", "-A"], cwd=repo_path, check=True)
        subprocess.run(["git", "commit", "-m", message], cwd=repo_path, check=True, stdout=subprocess.DEVNULL)
        return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo_path, text=True).strip()

    @staticmethod
    def _kotlin_placeholder_source() -> str:
        return """
/*
 * Copyright and related rights waived via CC0
 */
package org.example

import org.junit.jupiter.api.Test

class ExampleTest {
    @Test
    fun test() {
        println("This is just a placeholder, implement your test")
    }
}
"""


if __name__ == "__main__":
    unittest.main()
