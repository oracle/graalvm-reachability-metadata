# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import contextlib
import io
import os
import tempfile
import unittest
from unittest.mock import patch

from tests.test_gradle_environment import _init_git_repo_with_worktree
from utility_scripts.gradle_dependency_cache import (
    GRADLE_WARM_TIMEOUT_SECONDS,
    discard_worktree_gradle_home,
    ensure_shared_dependency_cache,
    prepare_shared_dependency_cache,
    prune_orphaned_worktree_gradle_homes,
    publish_read_only_dependency_cache,
    warm_checkout_gradle_home,
)
from utility_scripts.gradle_environment import (
    FORGE_GRADLE_USER_HOME_ENV,
    WORKTREE_MARKER_FILENAME,
    gradle_command_environment,
    gradle_user_home_for_repo,
    worktree_gradle_homes_root,
)
from utility_scripts.logged_command import LoggedCommandResult


def _result(returncode: int, timed_out: bool = False) -> LoggedCommandResult:
    return LoggedCommandResult(["./gradlew"], returncode, "", "/tmp/gradle.log", timed_out, 0.5)


def _write(path: str, content: str = "x") -> str:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)
    return path


def _seed_modules_cache(checkout_home: str) -> str:
    modules = os.path.join(checkout_home, "caches", "modules-2")
    _write(os.path.join(modules, "files-2.1", "org.example", "lib", "1.0", "abc", "lib-1.0.jar"), "jar")
    _write(os.path.join(modules, "metadata-2.107", "module-metadata.bin"), "index")
    _write(os.path.join(modules, "modules-2.lock"), "lock")
    _write(os.path.join(modules, "gc.properties"), "gc")
    _write(os.path.join(modules, "metadata-2.107", "descriptors.lock"), "lock")
    return modules


class SharedDependencyCacheTests(unittest.TestCase):
    """Per-worktree homes share dependencies read-only (§FS-forge-run-requirements.4)."""

    def test_publish_copies_the_dependency_cache_without_its_volatile_files(self) -> None:
        with tempfile.TemporaryDirectory() as checkout_home, tempfile.TemporaryDirectory() as repo_path, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True):
            modules = _seed_modules_cache(checkout_home)

            published = publish_read_only_dependency_cache(repo_path)

            self.assertEqual(published, os.path.join(checkout_home, "ro-dep-cache"))
            published_modules = os.path.join(published, "modules-2")
            jar_entry = os.path.join("files-2.1", "org.example", "lib", "1.0", "abc", "lib-1.0.jar")
            index_entry = os.path.join("metadata-2.107", "module-metadata.bin")
            published_jar = os.path.join(published_modules, jar_entry)
            published_index = os.path.join(published_modules, index_entry)
            self.assertTrue(os.path.isfile(published_jar))
            self.assertTrue(os.path.isfile(published_index))
            # Artifacts are never rewritten in place, so they are hard-linked; indexes are copied.
            self.assertTrue(os.path.samefile(published_jar, os.path.join(modules, jar_entry)))
            self.assertFalse(os.path.samefile(published_index, os.path.join(modules, index_entry)))
            self.assertFalse(os.path.exists(os.path.join(published_modules, "modules-2.lock")))
            self.assertFalse(os.path.exists(os.path.join(published_modules, "gc.properties")))
            self.assertFalse(os.path.exists(os.path.join(published_modules, "metadata-2.107", "descriptors.lock")))

    def test_publish_replaces_an_earlier_copy_whole(self) -> None:
        with tempfile.TemporaryDirectory() as checkout_home, tempfile.TemporaryDirectory() as repo_path, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True):
            modules = _seed_modules_cache(checkout_home)
            first = publish_read_only_dependency_cache(repo_path)
            stale_entry = os.path.join(first, "modules-2", "files-2.1", "stale.jar")
            _write(stale_entry)
            _write(os.path.join(modules, "files-2.1", "org.example", "lib", "1.1", "def", "lib.jar"), "jar")

            second = publish_read_only_dependency_cache(repo_path)

            self.assertEqual(first, second)
            self.assertFalse(os.path.exists(stale_entry))
            newer_jar = os.path.join(second, "modules-2", "files-2.1", "org.example", "lib", "1.1", "def", "lib.jar")
            self.assertTrue(os.path.isfile(newer_jar))
            self.assertFalse(os.path.exists(f"{second}.staging"))
            self.assertFalse(os.path.exists(f"{second}.retired"))

    def test_publish_without_a_dependency_cache_reports_and_returns_none(self) -> None:
        terminal = io.StringIO()
        with tempfile.TemporaryDirectory() as checkout_home, tempfile.TemporaryDirectory() as repo_path, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True), \
                contextlib.redirect_stdout(terminal):
            self.assertIsNone(publish_read_only_dependency_cache(repo_path))

        self.assertIn("No dependency cache to publish", terminal.getvalue())

    def test_warm_builds_the_worktree_with_a_single_use_daemon_into_the_checkout_home(self) -> None:
        with tempfile.TemporaryDirectory() as checkout_home, tempfile.TemporaryDirectory() as repo_path, \
                tempfile.TemporaryDirectory() as worktree_path, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True), \
                patch("utility_scripts.gradle_dependency_cache.run_logged_command") as run_command:
            run_command.return_value = _result(0)

            self.assertTrue(warm_checkout_gradle_home(repo_path, worktree_path))

        command_args, command_kwargs = run_command.call_args
        self.assertEqual(command_args[0], ["./gradlew", "help", "--quiet", "--no-daemon"])
        self.assertEqual(command_kwargs["cwd"], worktree_path)
        self.assertEqual(command_kwargs["env"]["GRADLE_USER_HOME"], checkout_home)
        self.assertEqual(command_kwargs["timeout_seconds"], GRADLE_WARM_TIMEOUT_SECONDS)

    def test_failed_warm_is_reported_and_returns_false(self) -> None:
        terminal = io.StringIO()
        with tempfile.TemporaryDirectory() as checkout_home, tempfile.TemporaryDirectory() as repo_path, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True), \
                patch("utility_scripts.gradle_dependency_cache.run_logged_command", return_value=_result(1)), \
                contextlib.redirect_stdout(terminal):
            self.assertFalse(warm_checkout_gradle_home(repo_path, os.path.join(repo_path, "worktree")))

        self.assertIn("Could not warm the checkout Gradle home", terminal.getvalue())

    def test_prepare_prunes_then_warms_then_publishes(self) -> None:
        calls: list[str] = []
        with patch("utility_scripts.gradle_dependency_cache.prune_orphaned_worktree_gradle_homes",
                   side_effect=lambda _path: calls.append("prune") or []), \
                patch("utility_scripts.gradle_dependency_cache.warm_checkout_gradle_home",
                      side_effect=lambda _path, _build_dir: calls.append("warm") or False), \
                patch("utility_scripts.gradle_dependency_cache.publish_read_only_dependency_cache",
                      side_effect=lambda _path: calls.append("publish") or "/ro"):
            self.assertEqual(prepare_shared_dependency_cache("/checkout", "/checkout/worktree"), "/ro")

        self.assertEqual(calls, ["prune", "warm", "publish"])

    def test_ensure_prepares_a_checkout_home_once_per_process(self) -> None:
        with tempfile.TemporaryDirectory() as checkout_home, tempfile.TemporaryDirectory() as repo_path, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True), \
                patch("utility_scripts.gradle_dependency_cache._prepared_checkout_homes", set()), \
                patch("utility_scripts.gradle_dependency_cache.prepare_shared_dependency_cache",
                      return_value="/ro") as prepare:
            self.assertEqual(ensure_shared_dependency_cache(repo_path, "/wt/first"), "/ro")
            self.assertIsNone(ensure_shared_dependency_cache(repo_path, "/wt/second"))

        prepare.assert_called_once_with(repo_path, "/wt/first")

    def test_discard_stops_the_worktree_daemons_and_removes_only_the_worktree_home(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir, tempfile.TemporaryDirectory() as checkout_home, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True):
            repo_path = os.path.join(temp_dir, "repo")
            worktree_path = os.path.join(temp_dir, "linked")
            _init_git_repo_with_worktree(repo_path, worktree_path)
            _write(os.path.join(worktree_path, "gradlew"), "#!/bin/sh\n")
            worktree_home = gradle_command_environment(worktree_path)["GRADLE_USER_HOME"]
            self.assertTrue(os.path.isdir(worktree_home))

            with patch("utility_scripts.gradle_dependency_cache.stop_gradle_daemons_of_home") as stop_daemons:
                discard_worktree_gradle_home(worktree_path, repo_path)
                discard_worktree_gradle_home(repo_path, repo_path)

            stop_daemons.assert_called_once()
            stop_args = stop_daemons.call_args.args
            self.assertEqual(stop_args[0], worktree_home)
            self.assertEqual(stop_args[1], worktree_path)
            self.assertEqual(stop_args[4], "worktree removal")
            self.assertFalse(os.path.exists(worktree_home))
            self.assertTrue(os.path.isdir(checkout_home))
            self.assertEqual(gradle_user_home_for_repo(repo_path), checkout_home)

    def test_discard_waits_for_the_stopped_daemon_to_exit_before_deleting_the_home(self) -> None:
        """`--stop` returns before the daemon JVM exits and rewrites the registry."""
        with tempfile.TemporaryDirectory() as temp_dir, tempfile.TemporaryDirectory() as checkout_home, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True):
            repo_path = os.path.join(temp_dir, "repo")
            worktree_path = os.path.join(temp_dir, "linked")
            _init_git_repo_with_worktree(repo_path, worktree_path)
            worktree_home = gradle_command_environment(worktree_path)["GRADLE_USER_HOME"]
            _write(os.path.join(worktree_home, "daemon", "9.1.0", "daemon-424242.out.log"))
            _write(os.path.join(worktree_home, "daemon", "9.1.0", "daemon-notapid.out.log"))
            alive_polls: list[int] = []

            def fake_alive(pid: int) -> bool:
                alive_polls.append(pid)
                return len(alive_polls) < 3

            with patch("utility_scripts.gradle_dependency_cache.stop_gradle_daemons_of_home"), \
                    patch("utility_scripts.gradle_dependency_cache._is_process_alive", side_effect=fake_alive), \
                    patch("utility_scripts.gradle_dependency_cache.time.sleep") as sleep:
                discard_worktree_gradle_home(worktree_path, repo_path)

            self.assertEqual(alive_polls, [424242, 424242, 424242])
            self.assertGreaterEqual(sleep.call_count, 2)
            self.assertFalse(os.path.exists(worktree_home))

    def test_prune_removes_homes_whose_worktree_is_gone_and_keeps_the_rest(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir, tempfile.TemporaryDirectory() as checkout_home, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True):
            repo_path = os.path.join(temp_dir, "repo")
            worktree_path = os.path.join(temp_dir, "linked")
            _init_git_repo_with_worktree(repo_path, worktree_path)
            live_home = gradle_command_environment(worktree_path)["GRADLE_USER_HOME"]
            homes_root = worktree_gradle_homes_root(checkout_home)
            orphan_home = os.path.join(homes_root, "gone-12345678")
            _write(os.path.join(orphan_home, WORKTREE_MARKER_FILENAME), os.path.join(temp_dir, "gone") + "\n")
            unmarked_home = os.path.join(homes_root, "unmarked-12345678")
            os.makedirs(unmarked_home)

            with patch("utility_scripts.gradle_dependency_cache.stop_gradle_daemons_of_home") as stop_daemons:
                pruned = prune_orphaned_worktree_gradle_homes(repo_path)

            self.assertEqual(pruned, [orphan_home])
            self.assertFalse(os.path.exists(orphan_home))
            self.assertTrue(os.path.isdir(live_home))
            self.assertTrue(os.path.isdir(unmarked_home))
            # An orphan has no worktree wrapper left, so the checkout's wrapper stops its daemons.
            self.assertEqual(stop_daemons.call_args.args[:2], (orphan_home, repo_path))

    def test_prune_without_worktree_homes_is_a_no_op(self) -> None:
        with tempfile.TemporaryDirectory() as checkout_home, tempfile.TemporaryDirectory() as repo_path, \
                patch.dict(os.environ, {FORGE_GRADLE_USER_HOME_ENV: checkout_home}, clear=True):
            self.assertEqual(prune_orphaned_worktree_gradle_homes(repo_path), [])


if __name__ == "__main__":
    unittest.main()
