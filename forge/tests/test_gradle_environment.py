# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts.gradle_environment import (
    FORGE_GRADLE_DISTRIBUTIONS_HOME_ENV,
    FORGE_GRADLE_USER_HOME_ENV,
    _resolve_git_common_dir,
    gradle_command_environment,
    gradle_user_home_for_repo,
)


def _init_git_repo_with_worktree(repo_path: str, worktree_path: str) -> None:
    os.makedirs(repo_path)
    _run_git(repo_path, "init", "-b", "main")
    _run_git(repo_path, "config", "user.email", "test@example.com")
    _run_git(repo_path, "config", "user.name", "Test User")
    with open(os.path.join(repo_path, "README.md"), "w", encoding="utf-8") as readme_file:
        readme_file.write("test\n")
    _run_git(repo_path, "add", "README.md")
    _run_git(repo_path, "commit", "-m", "initial")
    _run_git(repo_path, "worktree", "add", worktree_path)


def _run_git(cwd: str, *args: str) -> None:
    subprocess.run(
        ["git", *args],
        cwd=cwd,
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def _write_host_gradle_properties(home: str) -> str:
    """Write `gradle.properties` into the default `~/.gradle` of a fake home."""
    return _write_gradle_properties(os.path.join(home, ".gradle"))


def _write_gradle_properties(gradle_home: str) -> str:
    """Write a `gradle.properties` carrying the settings Forge must inherit."""
    os.makedirs(gradle_home, exist_ok=True)
    properties_path = os.path.join(gradle_home, "gradle.properties")
    with open(properties_path, "w", encoding="utf-8") as properties_file:
        properties_file.write("systemProp.https.proxyHost=proxy.test\nsystemProp.https.proxyPort=80\n")
    return properties_path


def _git_common_dir(cwd: str) -> str:
    """Return `git rev-parse --git-common-dir` as the oracle for the filesystem walk."""
    result = subprocess.run(
        ["git", "rev-parse", "--git-common-dir"],
        cwd=cwd,
        check=True,
        capture_output=True,
        text=True,
    )
    common_dir = result.stdout.strip()
    if not os.path.isabs(common_dir):
        common_dir = os.path.join(cwd, common_dir)
    return os.path.realpath(common_dir)


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

    def test_linked_worktrees_share_gradle_home_with_main_checkout(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_path = os.path.join(temp_dir, "repo")
            worktree_path = os.path.join(temp_dir, "linked")
            _init_git_repo_with_worktree(repo_path, worktree_path)

            with patch.dict(os.environ, {}, clear=True):
                main_gradle_home = gradle_user_home_for_repo(repo_path)
                worktree_gradle_home = gradle_user_home_for_repo(worktree_path)

            self.assertEqual(main_gradle_home, worktree_gradle_home)

    def test_common_dir_resolution_matches_git_rev_parse(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_path = os.path.join(temp_dir, "repo")
            worktree_path = os.path.join(temp_dir, "linked")
            nested_path = os.path.join(worktree_path, "nested", "dir")
            _init_git_repo_with_worktree(repo_path, worktree_path)
            os.makedirs(nested_path)

            for path in (repo_path, worktree_path, nested_path):
                self.assertEqual(_resolve_git_common_dir(path), _git_common_dir(path), path)

    def test_paths_outside_a_checkout_fall_back_to_the_path_itself(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            self.assertIsNone(_resolve_git_common_dir(temp_dir))

    def test_unrelated_checkouts_do_not_share_gradle_home(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            first_repo = os.path.join(temp_dir, "first")
            second_repo = os.path.join(temp_dir, "second")
            _init_git_repo_with_worktree(first_repo, os.path.join(temp_dir, "first-linked"))
            _init_git_repo_with_worktree(second_repo, os.path.join(temp_dir, "second-linked"))

            with patch.dict(os.environ, {}, clear=True):
                first_gradle_home = gradle_user_home_for_repo(first_repo)
                second_gradle_home = gradle_user_home_for_repo(second_repo)

            self.assertNotEqual(first_gradle_home, second_gradle_home)

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

    def test_isolated_home_inherits_host_gradle_properties(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as host_home:
            host_properties = _write_host_gradle_properties(host_home)

            with patch.dict(os.environ, {"HOME": host_home}, clear=True):
                env = gradle_command_environment(repo_path)

            linked_properties = os.path.join(env["GRADLE_USER_HOME"], "gradle.properties")
            self.assertTrue(os.path.islink(linked_properties))
            self.assertEqual(os.path.realpath(linked_properties), os.path.realpath(host_properties))

    def test_host_gradle_properties_sharing_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as host_home:
            _write_host_gradle_properties(host_home)

            with patch.dict(os.environ, {"HOME": host_home}, clear=True):
                first_env = gradle_command_environment(repo_path)
                second_env = gradle_command_environment(repo_path)

            self.assertEqual(first_env["GRADLE_USER_HOME"], second_env["GRADLE_USER_HOME"])
            self.assertTrue(os.path.islink(os.path.join(second_env["GRADLE_USER_HOME"], "gradle.properties")))

    def test_missing_host_gradle_properties_is_not_an_error(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as host_home:
            with patch.dict(os.environ, {"HOME": host_home}, clear=True):
                env = gradle_command_environment(repo_path)

            self.assertFalse(os.path.exists(os.path.join(env["GRADLE_USER_HOME"], "gradle.properties")))

    def test_inherited_gradle_user_home_supplies_host_properties(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, \
                tempfile.TemporaryDirectory() as inherited_home, \
                tempfile.TemporaryDirectory() as empty_home:
            host_properties = _write_gradle_properties(inherited_home)

            with patch.dict(os.environ, {"HOME": empty_home}, clear=True):
                env = gradle_command_environment(repo_path, {"GRADLE_USER_HOME": inherited_home})

            linked_properties = os.path.join(env["GRADLE_USER_HOME"], "gradle.properties")
            self.assertEqual(os.path.realpath(linked_properties), os.path.realpath(host_properties))

    def test_forge_home_inherited_from_an_outer_call_is_not_treated_as_host_config(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, tempfile.TemporaryDirectory() as host_home:
            host_properties = _write_host_gradle_properties(host_home)

            with patch.dict(os.environ, {"HOME": host_home}, clear=True):
                outer_env = gradle_command_environment(repo_path)
                inner_env = gradle_command_environment(repo_path, dict(outer_env))

            linked_properties = os.path.join(inner_env["GRADLE_USER_HOME"], "gradle.properties")
            self.assertEqual(os.path.realpath(linked_properties), os.path.realpath(host_properties))

    def test_explicit_gradle_home_override_skips_host_properties_sharing(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path, \
                tempfile.TemporaryDirectory() as host_home, \
                tempfile.TemporaryDirectory() as gradle_home:
            _write_host_gradle_properties(host_home)

            with patch.dict(os.environ, {"HOME": host_home, FORGE_GRADLE_USER_HOME_ENV: gradle_home}, clear=True):
                env = gradle_command_environment(repo_path)

            self.assertEqual(env["GRADLE_USER_HOME"], gradle_home)
            self.assertFalse(os.path.exists(os.path.join(gradle_home, "gradle.properties")))

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
