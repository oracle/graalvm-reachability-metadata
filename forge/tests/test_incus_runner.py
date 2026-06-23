# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import unittest
from unittest.mock import patch

from utility_scripts import incus_runner
from utility_scripts.incus_runner import (
    DEFAULT_INCUS_IMAGE,
    DEFAULT_INCUS_PROFILE,
    DEFAULT_VM_REPO_PATH,
    VM_CODEX_DIR,
    VM_LOGS_MOUNT_PATH,
    VM_PI_AGENT_DIR,
    agent_config_files,
    build_incus_exec_command,
    build_inner_forge_command,
    incus_image_name,
    incus_profile_name,
    IncusPreflightError,
    preflight,
    vm_repo_path,
)
from utility_scripts.task_logs import FORGE_LOGS_DIR_ENV, resolve_logs_root


class AgentConfigFilesTests(unittest.TestCase):
    def test_maps_small_codex_and_pi_files_to_vm_paths(self) -> None:
        with patch.dict(os.environ, {"FORGE_INCUS_CODEX_DIR": "/h/cx", "FORGE_INCUS_PI_DIR": "/h/pi"}, clear=True):
            pairs = agent_config_files()
        self.assertEqual(
            pairs,
            [
                ("/h/cx/auth.json", f"{VM_CODEX_DIR}/auth.json"),
                ("/h/cx/config.toml", f"{VM_CODEX_DIR}/config.toml"),
                ("/h/pi/agent/models.json", f"{VM_PI_AGENT_DIR}/models.json"),
                ("/h/pi/agent/settings.json", f"{VM_PI_AGENT_DIR}/settings.json"),
            ],
        )

    def test_defaults_expand_user_home(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            host_paths = [host for host, _ in agent_config_files()]
        self.assertTrue(all(os.path.isabs(p) for p in host_paths))
        self.assertTrue(any(p.endswith("/.codex/auth.json") for p in host_paths))
        self.assertTrue(any(p.endswith("/.pi/agent/models.json") for p in host_paths))


class LogsRootOverrideTests(unittest.TestCase):
    def test_logs_root_defaults_under_repo(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            self.assertTrue(resolve_logs_root().endswith(os.path.join("forge", "logs")))

    def test_logs_root_honors_override(self) -> None:
        with patch.dict(os.environ, {FORGE_LOGS_DIR_ENV: "~/forge-out"}, clear=True):
            resolved = resolve_logs_root()
        self.assertTrue(os.path.isabs(resolved))
        self.assertEqual(resolved, os.path.abspath(os.path.expanduser("~/forge-out")))


class SettingsResolutionTests(unittest.TestCase):
    def test_defaults(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(incus_image_name(), DEFAULT_INCUS_IMAGE)
            self.assertEqual(incus_profile_name(), DEFAULT_INCUS_PROFILE)
            self.assertEqual(vm_repo_path(), DEFAULT_VM_REPO_PATH)

    def test_overrides(self) -> None:
        env = {
            "FORGE_INCUS_IMAGE": "custom-base",
            "FORGE_INCUS_PROFILE": "custom-profile",
            "FORGE_INCUS_REPO_PATH": "/srv/reachability",
        }
        with patch.dict(os.environ, env, clear=True):
            self.assertEqual(incus_image_name(), "custom-base")
            self.assertEqual(incus_profile_name(), "custom-profile")
            self.assertEqual(vm_repo_path(), "/srv/reachability")


class InnerCommandTests(unittest.TestCase):
    def test_minimal_inner_command(self) -> None:
        command = build_inner_forge_command(
            9101,
            strategy_name=None,
            keep_tests_without_dynamic_access=False,
        )
        self.assertEqual(command, ["python3", "forge_metadata.py", "--issue-number", "9101"])

    def test_inner_command_forwards_strategy_and_keep_flag(self) -> None:
        command = build_inner_forge_command(
            42,
            strategy_name="dynamic_access_main_sources_pi_gpt-5.5",
            keep_tests_without_dynamic_access=True,
        )
        self.assertEqual(
            command,
            [
                "python3",
                "forge_metadata.py",
                "--issue-number",
                "42",
                "--strategy-name",
                "dynamic_access_main_sources_pi_gpt-5.5",
                "--keep-tests-without-dynamic-access",
            ],
        )

    def test_inner_command_never_carries_incus(self) -> None:
        command = build_inner_forge_command(
            7,
            strategy_name="s",
            keep_tests_without_dynamic_access=True,
        )
        self.assertNotIn("--incus", command)


class ExecCommandTests(unittest.TestCase):
    def test_exec_command_sources_env_then_runs_inner_command(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            exec_command = build_incus_exec_command(
                "forge-run-7-abcd",
                ["python3", "forge_metadata.py", "--issue-number", "7"],
                {"FORGE_LOGS_DIR": VM_LOGS_MOUNT_PATH, "FORGE_STRATEGY_NAME": "s"},
            )

        self.assertEqual(exec_command[0], "exec")
        self.assertEqual(exec_command[1], "forge-run-7-abcd")
        self.assertIn("--cwd", exec_command)
        self.assertEqual(
            exec_command[exec_command.index("--cwd") + 1],
            f"{DEFAULT_VM_REPO_PATH}/forge",
        )
        # The inner run is wrapped in bash that sources the baked /etc/environment
        # (GraalVM homes + PATH), then applies the forwarded settings after so they
        # win over anything forge.env baked in.
        separator_index = exec_command.index("--")
        self.assertEqual(exec_command[separator_index + 1], "bash")
        self.assertEqual(exec_command[separator_index + 2], "-c")
        script = exec_command[separator_index + 3]
        self.assertIn(". /etc/environment", script)
        self.assertIn(f"FORGE_LOGS_DIR={VM_LOGS_MOUNT_PATH}", script)
        self.assertIn("FORGE_STRATEGY_NAME=s", script)
        self.assertLess(script.index(". /etc/environment"), script.index("FORGE_LOGS_DIR="))
        # Inner command follows the bash arg0 sentinel as positional args.
        self.assertEqual(
            exec_command[separator_index + 5:],
            ["python3", "forge_metadata.py", "--issue-number", "7"],
        )


class ForwardedEnvironmentTests(unittest.TestCase):
    def test_forwards_forge_settings_and_pins_logs_dir(self) -> None:
        env = {
            "FORGE_STRATEGY_NAME": "s",
            "FORGE_PARALLELISM": "3",
            "FORGE_INCUS_IMAGE": "custom-base",
            "FORGE_LOGS_DIR": "/host/logs",
            "PATH": "/usr/bin",
        }
        with patch.dict(os.environ, env, clear=True):
            forwarded = incus_runner._forwarded_run_environment()

        self.assertEqual(forwarded["FORGE_STRATEGY_NAME"], "s")
        self.assertEqual(forwarded["FORGE_PARALLELISM"], "3")
        # Incus-runner-only and non-FORGE settings are not forwarded.
        self.assertNotIn("FORGE_INCUS_IMAGE", forwarded)
        self.assertNotIn("PATH", forwarded)
        # Logs are always pinned to the in-VM mount target.
        self.assertEqual(forwarded["FORGE_LOGS_DIR"], VM_LOGS_MOUNT_PATH)


class PreflightTests(unittest.TestCase):
    def test_preflight_fails_when_incus_missing(self) -> None:
        with patch.object(incus_runner.shutil, "which", return_value=None):
            with self.assertRaises(IncusPreflightError) as caught:
                preflight()
        self.assertIn("incus", str(caught.exception).lower())

    def test_preflight_fails_when_daemon_unreachable(self) -> None:
        with patch.object(incus_runner.shutil, "which", return_value="/usr/bin/incus"), \
                patch.object(incus_runner, "_incus_daemon_reachable", return_value=False):
            with self.assertRaises(IncusPreflightError) as caught:
                preflight()
        self.assertIn("daemon", str(caught.exception).lower())

    def test_preflight_fails_when_image_missing(self) -> None:
        with patch.object(incus_runner.shutil, "which", return_value="/usr/bin/incus"), \
                patch.object(incus_runner, "_incus_daemon_reachable", return_value=True), \
                patch.object(incus_runner, "_image_exists", return_value=False):
            with self.assertRaises(IncusPreflightError) as caught:
                preflight()
        self.assertIn("base image", str(caught.exception).lower())

    def test_preflight_passes_when_ready(self) -> None:
        with patch.object(incus_runner.shutil, "which", return_value="/usr/bin/incus"), \
                patch.object(incus_runner, "_incus_daemon_reachable", return_value=True), \
                patch.object(incus_runner, "_image_exists", return_value=True), \
                patch.object(incus_runner, "_resolve_github_token", return_value="t"):
            preflight()


if __name__ == "__main__":
    unittest.main()
