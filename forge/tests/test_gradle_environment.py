# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.gradle_environment import (
    FORGE_GRADLE_DISTRIBUTIONS_HOME_ENV,
    FORGE_GRADLE_USER_HOME_ENV,
    gradle_command_environment,
    gradle_user_home_for_repo,
)


class GradleEnvironmentTests(unittest.TestCase):
    def test_uses_temp_gradle_home_scoped_by_repo_path(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            with patch.dict(os.environ, {}, clear=True):
                gradle_home = gradle_user_home_for_repo(repo_path)
                equivalent_gradle_home = gradle_user_home_for_repo(os.path.join(repo_path, "."))

            self.assertTrue(os.path.isabs(gradle_home))
            self.assertEqual(os.path.dirname(os.path.dirname(gradle_home)), tempfile.gettempdir())
            self.assertEqual(os.path.basename(os.path.dirname(gradle_home)), "metadata-forge-gradle")
            self.assertEqual(gradle_home, equivalent_gradle_home)

    def test_command_environment_preserves_base_env_and_sets_gradle_user_home(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            with patch.dict(os.environ, {}, clear=True):
                env = gradle_command_environment(repo_path, {"JAVA_HOME": "/jdk"})
                expected_gradle_user_home = gradle_user_home_for_repo(repo_path)

            self.assertEqual(env["JAVA_HOME"], "/jdk")
            self.assertEqual(env["GRADLE_USER_HOME"], expected_gradle_user_home)
            self.assertTrue(os.path.isdir(env["GRADLE_USER_HOME"]))

    def test_default_gradle_homes_share_wrapper_distributions(self) -> None:
        with tempfile.TemporaryDirectory() as first_repo, tempfile.TemporaryDirectory() as second_repo:
            with patch.dict(os.environ, {}, clear=True):
                first_env = gradle_command_environment(first_repo)
                second_env = gradle_command_environment(second_repo)

            first_dists = os.path.join(first_env["GRADLE_USER_HOME"], "wrapper", "dists")
            second_dists = os.path.join(second_env["GRADLE_USER_HOME"], "wrapper", "dists")
            expected_dists = os.path.join(tempfile.gettempdir(), "metadata-forge-gradle", "wrapper-dists")

            self.assertNotEqual(first_env["GRADLE_USER_HOME"], second_env["GRADLE_USER_HOME"])
            self.assertEqual(os.path.realpath(first_dists), expected_dists)
            self.assertEqual(os.path.realpath(second_dists), expected_dists)

    def test_explicit_gradle_distributions_override_is_honored(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as gradle_dists:
            with patch.dict(os.environ, {}, clear=True):
                env = gradle_command_environment(
                    repo_path,
                    {FORGE_GRADLE_DISTRIBUTIONS_HOME_ENV: gradle_dists},
                )

            dists_path = os.path.join(env["GRADLE_USER_HOME"], "wrapper", "dists")
            self.assertEqual(os.path.realpath(dists_path), gradle_dists)

    def test_explicit_gradle_home_override_is_honored(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as gradle_home:
            with patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: gradle_home}, clear=True):
                env = gradle_command_environment(repo_path)

            self.assertEqual(env["GRADLE_USER_HOME"], gradle_home)
            self.assertFalse(os.path.exists(os.path.join(gradle_home, "wrapper", "dists")))

    def test_base_env_gradle_home_override_is_honored(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as gradle_home:
            with patch.dict(os.environ, {}, clear=True):
                env = gradle_command_environment(repo_path, {FORGE_GRADLE_USER_HOME_ENV: gradle_home})

            self.assertEqual(env["GRADLE_USER_HOME"], gradle_home)

    def test_graalvm_home_with_native_image_drives_java_home(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as graalvm_home:
            os.makedirs(os.path.join(graalvm_home, "bin"))
            native_image_path = os.path.join(graalvm_home, "bin", "native-image")
            with open(native_image_path, "w", encoding="utf-8"):
                pass

            with patch.dict(os.environ, {}, clear=True):
                env = gradle_command_environment(
                    repo_path,
                    {
                        "GRAALVM_HOME": graalvm_home,
                        "JAVA_HOME": "/plain-jdk",
                    },
                )

            self.assertEqual(env["GRAALVM_HOME"], graalvm_home)
            self.assertEqual(env["JAVA_HOME"], graalvm_home)

    def test_java_home_with_native_image_backfills_graalvm_home(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as java_home:
            os.makedirs(os.path.join(java_home, "bin"))
            native_image_path = os.path.join(java_home, "bin", "native-image")
            with open(native_image_path, "w", encoding="utf-8"):
                pass

            with patch.dict(os.environ, {}, clear=True):
                env = gradle_command_environment(repo_path, {"JAVA_HOME": java_home})

            self.assertEqual(env["GRAALVM_HOME"], java_home)
            self.assertEqual(env["JAVA_HOME"], java_home)


if __name__ == "__main__":
    unittest.main()
