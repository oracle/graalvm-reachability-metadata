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
    find_native_image_skip_guards,
    format_native_image_skip_occurrence,
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

    def test_removes_untouched_scaffold_when_no_real_test_exists(self) -> None:
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

            self.assertEqual(result.removed_files, [placeholder_file])
            self.assertFalse(os.path.exists(placeholder_file))
            self.assertEqual(result.remaining_placeholders, [])

    def test_removes_changed_scaffold_when_only_test_is_placeholder(self) -> None:
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
            self._write_file(
                placeholder_file,
                """
package org.example;

import org.junit.jupiter.api.Test;

public class ExampleTest {
    @Test
    void test() throws Exception {
        System.out.println("This is just a placeholder, implement your test");
    }
}
""",
            )

            result = cleanup_scaffold_placeholder_tests(tmp_dir, tmp_dir, scaffold_commit)

            self.assertEqual(result.removed_files, [placeholder_file])
            self.assertFalse(os.path.exists(placeholder_file))
            self.assertEqual(result.remaining_placeholders, [])

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

    def test_reports_placeholder_when_only_test_is_not_scaffold_method(self) -> None:
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
            self._write_file(
                placeholder_file,
                """
package org.example;

import org.junit.jupiter.api.Test;

class ExampleTest {
    @Test
    void exercisesLibrary() {
        System.out.println("This is just a placeholder, implement your test");
    }
}
""",
            )

            result = cleanup_scaffold_placeholder_tests(tmp_dir, tmp_dir, scaffold_commit)

            self.assertEqual(result.removed_files, [])
            self.assertTrue(os.path.exists(placeholder_file))
            self.assertEqual(len(result.remaining_placeholders), 1)

    def test_reports_native_image_early_return(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            test_file = os.path.join(tmp_dir, "AgentTest.java")
            self._write_file(
                test_file,
                """
package org.example;

import org.junit.jupiter.api.Test;

class AgentTest {
    @Test
    void exercisesAgent() {
        if (NativeImageSupport.isNativeImageRuntime()) {
            return;
        }
        org.assertj.core.api.Assertions.assertThat("agent").isNotEmpty();
    }
}
""",
            )

            occurrences = find_native_image_skip_guards(tmp_dir)

            self.assertEqual(len(occurrences), 1)
            self.assertIn("returns before exercising assertions", occurrences[0].reason)
            self.assertIn("AgentTest.java", format_native_image_skip_occurrence(occurrences[0], tmp_dir))

    def test_reports_lombok_like_native_image_empty_optional_short_circuit(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            test_file = os.path.join(tmp_dir, "AgentTest.java")
            self._write_file(
                test_file,
                """
package org.example;

import java.nio.file.Path;
import java.util.Optional;
import org.graalvm.nativeimage.ImageInfo;
import org.junit.jupiter.api.Test;

class AgentTest {
    @Test
    void opensLombokJar() {
        Optional<Path> lombokJar = locateLombokJar();
        if (lombokJar.isEmpty()) {
            return;
        }
        org.assertj.core.api.Assertions.assertThat(lombokJar.get()).exists();
    }

    static Optional<Path> locateLombokJar() {
        if (ImageInfo.inImageRuntimeCode()) {
            return Optional.empty();
        }
        return Optional.of(Path.of("lombok.jar"));
    }
}
""",
            )

            occurrences = find_native_image_skip_guards(tmp_dir)

            self.assertEqual(len(occurrences), 1)
            self.assertIn("returns before exercising assertions", occurrences[0].reason)

    def test_reports_native_image_assumption_and_abort(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            test_file = os.path.join(tmp_dir, "ShadowClassLoaderTest.java")
            self._write_file(
                test_file,
                """
package org.example;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ShadowClassLoaderTest {
    @Test
    void enumeratesResources() {
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            Assumptions.assumeTrue(false, "resources unavailable in native image");
        }
        org.assertj.core.api.Assertions.assertThat("resource").isNotEmpty();
    }

    @Test
    void abortsNativeImagePath() {
        boolean nativeImage = NativeImageSupport.isNativeImageRuntime();
        if (nativeImage) {
            throw new org.opentest4j.TestAbortedException("skip native image");
        }
        org.assertj.core.api.Assertions.assertThat("resource").isNotEmpty();
    }
}
""",
            )

            occurrences = find_native_image_skip_guards(tmp_dir)

            self.assertEqual(len(occurrences), 2)
            self.assertTrue(all("aborts or assumes away" in occurrence.reason for occurrence in occurrences))

    def test_reports_disabled_in_native_image_annotation(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            test_file = os.path.join(tmp_dir, "DisabledTest.java")
            self._write_file(
                test_file,
                """
package org.example;

import org.junit.jupiter.api.Test;

class DisabledTest {
    @DisabledInNativeImage
    @Test
    void onlyRunsOnJvm() {
        org.assertj.core.api.Assertions.assertThat("jvm").isNotEmpty();
    }
}
""",
            )

            occurrences = find_native_image_skip_guards(tmp_dir)

            self.assertEqual(len(occurrences), 1)
            self.assertIn("@DisabledInNativeImage", occurrences[0].reason)

    def test_allows_unsupported_feature_assertion(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            test_file = os.path.join(tmp_dir, "UnsupportedFeatureTest.java")
            self._write_file(
                test_file,
                """
package org.example;

import org.junit.jupiter.api.Test;

class UnsupportedFeatureTest {
    @Test
    void dynamicLoadingReportsUnsupportedFeature() {
        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> {
            throw new UnsupportedOperationException("dynamic loading unsupported");
        });
        org.assertj.core.api.Assertions.assertThat(
            NativeImageSupport.isUnsupportedFeatureError(failure)
        ).isTrue();
    }
}
""",
            )

            self.assertEqual(find_native_image_skip_guards(tmp_dir), [])

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
