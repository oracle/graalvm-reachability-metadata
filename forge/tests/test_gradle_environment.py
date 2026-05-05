# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.gradle_environment import (
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

    def test_explicit_gradle_home_override_is_honored(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as gradle_home:
            with patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: gradle_home}, clear=True):
                env = gradle_command_environment(repo_path)

            self.assertEqual(env["GRADLE_USER_HOME"], gradle_home)

    def test_base_env_gradle_home_override_is_honored(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as gradle_home:
            with patch.dict(os.environ, {}, clear=True):
                env = gradle_command_environment(repo_path, {FORGE_GRADLE_USER_HOME_ENV: gradle_home})

            self.assertEqual(env["GRADLE_USER_HOME"], gradle_home)


if __name__ == "__main__":
    unittest.main()
