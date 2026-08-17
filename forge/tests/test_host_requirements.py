# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import json
import os
import subprocess
import tempfile
import unittest
from contextlib import ExitStack, contextmanager, redirect_stderr, redirect_stdout
from pathlib import Path
from typing import Iterator
from unittest.mock import Mock, patch

from utility_scripts.gradle_environment import gradle_user_home_for_repo
from utility_scripts.host_requirements import (
    GRAALVM_SCHEMA_PATH,
    HostRequirements,
    QueueRequirements,
    check_graalvm_installation,
    check_pi_model_available,
    codex_doctor_provider_status,
    codex_unattended_policy_status,
    graalvm_ea_version_matches,
    java_version_major,
    main,
    parse_ea_release_version,
    parse_graalvm_runtime_version,
    parse_gradle_version,
    parse_grype_version,
    pi_approve_supported,
    pi_model_listed,
    probe_proxied_host,
    resolve_git_metadata_dir,
    resolve_graalvm_version_check,
    resolve_https_proxy,
    resolve_queue_requirements,
)

PI_LIST_MODELS_OUTPUT = (
    "provider      model                           context  max-out  thinking  images\n"
    "openai-codex  gpt-5.6-terra                   272K     128K     yes       yes   \n"
    "openrouter    openai/gpt-5.6-terra            1.1M     128K     yes       yes   \n"
)


@contextmanager
def _graalvm_home(native_image: bool = True, schema: bool = True) -> Iterator[str]:
    """Create a GraalVM home whose Native Image and schema presence can be controlled."""
    with tempfile.TemporaryDirectory() as temp_dir:
        executables = ["java", "native-image"] if native_image else ["java"]
        bin_dir = Path(temp_dir) / "bin"
        bin_dir.mkdir()
        for executable in executables:
            (bin_dir / executable).touch(mode=0o755)
        if schema:
            schema_path = Path(temp_dir) / GRAALVM_SCHEMA_PATH
            schema_path.parent.mkdir(parents=True)
            schema_path.touch()
        yield temp_dir


def _codex_bundle_cache(contents: str) -> dict:
    """Build the nested `cloud-config-bundle-cache.json` payload Codex actually writes."""
    return {
        "signature": "signature",
        "signed_payload": {
            "version": 1,
            "bundle": {
                "config_toml": {},
                "requirements_toml": {
                    "enterprise_managed": [{
                        "id": "8a9cace4-0b4b-4c93-a8d5-b33253d2d6e4",
                        "name": "Codex Developers",
                        "contents": contents,
                    }],
                },
            },
        },
    }


def _fake_pi_command(command: list[str], *_args, **_kwargs) -> subprocess.CompletedProcess:
    """Answer the deterministic Pi probes without invoking the real CLI."""
    if "--list-models" in command:
        return subprocess.CompletedProcess(command, 0, PI_LIST_MODELS_OUTPUT, "")
    if "--help" in command:
        return subprocess.CompletedProcess(
            command,
            0,
            "  --approve, -a                  Trust project-local files for this run\n",
            "",
        )
    return subprocess.CompletedProcess(
        command,
        0,
        '{"status":"ready","provider":"openai-codex","authType":"oauth"}\n',
        "",
    )


def _fake_git_and_gradle_command(command: list[str], *_args, **_kwargs) -> subprocess.CompletedProcess:
    """Answer the git and Gradle probes of a plain checkout without running either tool."""
    if "rev-parse" in command:
        return subprocess.CompletedProcess(command, 0, ".git\n", "")
    if "remote" in command:
        return subprocess.CompletedProcess(command, 0, "git@github.com:acme/repo.git\n", "")
    if "ls-remote" in command:
        return subprocess.CompletedProcess(command, 0, "0123456789abcdef\trefs/heads/branch\n", "")
    return subprocess.CompletedProcess(command, 0, "Gradle 8.14\n", "")


def _github_permission_response(push_slug: str | None, push_permission: str | None) -> str:
    """Build the GraphQL permission payload, with the push target when its origin resolved."""
    data: dict = {
        "viewer": {"login": "automation-user"},
        "repository": {
            "nameWithOwner": "oracle/graalvm-reachability-metadata",
            "viewerPermission": "MAINTAIN",
        },
        "organization": {"projectV2": {"title": "Reachability Metadata", "viewerCanUpdate": True}},
    }
    if push_slug is not None:
        data["pushTarget"] = {"nameWithOwner": push_slug, "viewerPermission": push_permission}
    return json.dumps({"data": data})


def _codex_policy_status(bundle: dict) -> tuple[bool, str]:
    """Evaluate a Codex config bundle cache written to a throwaway `CODEX_HOME`."""
    with tempfile.TemporaryDirectory() as codex_home:
        bundle_path = os.path.join(codex_home, "cloud-config-bundle-cache.json")
        with open(bundle_path, "w", encoding="utf-8") as bundle_file:
            json.dump(bundle, bundle_file)
        return codex_unattended_policy_status(codex_home)


class HostRequirementsTests(unittest.TestCase):
    def test_invalid_queue_limit_prints_one_actionable_fix(self) -> None:
        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch.dict(os.environ, {"FORGE_WORK_LIMIT": "invalid"}, clear=True), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            result = main(["--forge-dir", "/repo/forge"])

        self.assertEqual(1, result)
        self.assertEqual("", stdout.getvalue())
        self.assertIn("FORGE_WORK_LIMIT must be a non-negative integer", stderr.getvalue())
        self.assertEqual(1, stderr.getvalue().count("Fix:"))

    def test_empty_queue_limits_mean_unset_instead_of_invalid(self) -> None:
        environment = {name: "" for name in ("FORGE_JAVAC_WORK_LIMIT", "FORGE_REVIEW_LIMIT")}

        requirements = resolve_queue_requirements(environment)

        self.assertTrue(requirements.issue_work)
        self.assertTrue(requirements.review_work)

    def test_queue_requirements_follow_effective_limits(self) -> None:
        environment = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_BULK_UPDATE_REVIEW_LIMIT": "2",
        }

        requirements = resolve_queue_requirements(environment)

        self.assertFalse(requirements.issue_work)
        self.assertTrue(requirements.review_work)

    def test_explicit_review_label_ignores_default_queue_overrides(self) -> None:
        environment = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LABEL": "library-new-request",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_BULK_UPDATE_REVIEW_LIMIT": "2",
        }

        requirements = resolve_queue_requirements(environment)

        self.assertFalse(requirements.issue_work)
        self.assertFalse(requirements.review_work)

    def test_codex_doctor_provider_status_requires_reachable_provider(self) -> None:
        output = json.dumps({
            "checks": {
                "network.provider_reachability": {
                    "status": "fail",
                    "summary": "provider endpoint is unreachable",
                },
            },
        })

        passed, detail = codex_doctor_provider_status(output)

        self.assertFalse(passed)
        self.assertIn("unreachable", detail)

    def test_java_version_major_supports_current_and_legacy_version_lines(self) -> None:
        self.assertEqual(25, java_version_major('openjdk version "25.0.2" 2026-01-20'))
        self.assertEqual(8, java_version_major('java version "1.8.0_402"'))
        self.assertIsNone(java_version_major("java version unavailable"))

    def test_native_image_and_ea_version_parsing(self) -> None:
        native_output = (
            "native-image 25.0.4 2026-07-21\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 "
            "(build 25.0.4+7-jvmci-25.2-b20)\n"
        )

        self.assertEqual("25.2.4+7.1", parse_graalvm_runtime_version(native_output))
        self.assertEqual(
            ("25.3", "25.0.4.1", 2),
            parse_ea_release_version("25i3-25.0.4.1-ea.02"),
        )
        self.assertTrue(graalvm_ea_version_matches(
            "25i3-25.0.4.1-ea.02",
            "25.3.4.1-dev+0.1",
            "25.0.4.1+0-LTS-jvmci-25.3-b21",
        ))
        self.assertFalse(graalvm_ea_version_matches(
            "25i3-25.0.4.1-ea.02",
            "25.3.4.1-dev+0.0",
            "25.0.4.1+0-LTS-jvmci-25.3-b20",
        ))

    def test_grype_version_parsing(self) -> None:
        self.assertEqual("0.104.0", parse_grype_version("Application: grype\nVersion: 0.104.0\n"))
        self.assertEqual("0.104.0", parse_grype_version("Version: v0.104.0\n"))
        self.assertEqual("9.1.0", parse_gradle_version("\nWelcome to Gradle 9.1.0!\n\nGradle 9.1.0\n"))

    @patch("utility_scripts.host_requirements.run_command")
    def test_pi_project_local_trust_support_is_checked_from_help(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["pi", "--help"],
            0,
            "Usage: pi [options]\n"
            "  --approve, -a                  Trust project-local files for this run\n"
            "  --no-approve, -na              Ignore project-local files for this run\n",
            "",
        )

        self.assertTrue(pi_approve_supported({}))

    @patch("utility_scripts.host_requirements.resolve_executable", return_value="/usr/bin/pi")
    @patch("utility_scripts.host_requirements.run_command")
    def test_review_model_availability_is_listed_not_inferred_from_authentication(
            self,
            command: Mock,
            _resolve: Mock,
    ) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["pi", "--list-models", "gpt-5.6-terra"],
            0,
            PI_LIST_MODELS_OUTPUT,
            "",
        )

        available, detail = check_pi_model_available("gpt-5.6-terra", {})

        self.assertEqual(["pi", "--list-models", "gpt-5.6-terra"], command.call_args.args[0])
        self.assertTrue(available)
        self.assertIn("model=gpt-5.6-terra", detail)

    @patch("utility_scripts.host_requirements.resolve_executable", return_value="/usr/bin/pi")
    @patch("utility_scripts.host_requirements.run_command")
    def test_unknown_review_model_fails_even_when_authentication_is_ready(
            self,
            command: Mock,
            _resolve: Mock,
    ) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["pi", "--list-models", "definitely-not-a-model-xyz"],
            0,
            'No models matching "definitely-not-a-model-xyz"\n',
            "",
        )

        available, detail = check_pi_model_available("definitely-not-a-model-xyz", {})

        self.assertFalse(available)
        self.assertIn("No models matching", detail)

    def test_review_model_matches_every_documented_pi_model_pattern(self) -> None:
        # `pi --model` accepts "provider/id" and an optional ":<thinking>" suffix.
        self.assertTrue(pi_model_listed(PI_LIST_MODELS_OUTPUT, "gpt-5.6-terra"))
        self.assertTrue(pi_model_listed(PI_LIST_MODELS_OUTPUT, "openai-codex/gpt-5.6-terra"))
        self.assertTrue(pi_model_listed(PI_LIST_MODELS_OUTPUT, "gpt-5.6-terra:high"))
        self.assertTrue(pi_model_listed(PI_LIST_MODELS_OUTPUT, "openai-codex/gpt-5.6-terra:xhigh"))
        self.assertFalse(pi_model_listed(PI_LIST_MODELS_OUTPUT, "definitely-not-a-model-xyz"))
        # Another provider's row must never satisfy the Forge provider.
        self.assertFalse(pi_model_listed(PI_LIST_MODELS_OUTPUT, "openai/gpt-5.6-terra"))

    @patch("utility_scripts.host_requirements.resolve_executable", return_value="/usr/bin/pi")
    @patch("utility_scripts.host_requirements.run_command", side_effect=_fake_pi_command)
    def test_review_model_is_only_required_by_review_work(self, _command: Mock, _resolve: Mock) -> None:
        issue_only = HostRequirements(
            "/repo/forge",
            "python3",
            "gpt-5.6-terra",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=False),
        )

        issue_only._check_pi()

        result_by_name = {result.name: result for result in issue_only.results}
        self.assertEqual("SKIP", result_by_name["Pi review model"].status)
        self.assertEqual("SKIP", result_by_name["Pi project-local trust"].status)
        self.assertEqual("PASS", result_by_name["Pi authentication"].status)

    def test_pinned_graalvm_25_version_is_recorded_in_repository(self) -> None:
        forge_dir = Path(__file__).resolve().parents[1]
        with (forge_dir / "graalvm-versions.json").open(encoding="utf-8") as version_file:
            version = json.load(version_file)["GRAALVM_HOME_25_0"]

        self.assertRegex(version, r"^25\.0\.\d+$")

    @patch("utility_scripts.host_requirements.run_command")
    def test_graalvm_check_requires_repository_schema(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n",
            "",
        )
        with _graalvm_home(schema=False) as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME": graalvm_home},
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.2.4")

        self.assertFalse(host_requirements.results[-1].passed)
        self.assertIn(GRAALVM_SCHEMA_PATH, host_requirements.results[-1].detail)
        self.assertIn("is missing", host_requirements.results[-1].detail)

    @patch("utility_scripts.host_requirements.run_command")
    def test_strict_version_check_stops_work_on_a_mismatched_graalvm(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 (build 25.0.4+7-jvmci-25.2-b20)\n",
            "",
        )
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME": graalvm_home},
                graalvm_version_check="strict",
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.3.0")

        installation, version = host_requirements.results
        self.assertEqual("PASS", installation.status)
        self.assertEqual("FAIL", version.status)
        self.assertTrue(version.blocks_work)

    @patch("utility_scripts.host_requirements.run_command")
    def test_warn_version_check_keeps_native_image_and_schema_mandatory(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.2.4 2026-07-28\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 (build 25.0.4+7-jvmci-25.2-b20)\n",
            "",
        )
        with _graalvm_home() as patched_graalvm, _graalvm_home(native_image=False) as broken_graalvm:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME": patched_graalvm, "GRAALVM_HOME_25_0": broken_graalvm},
                graalvm_version_check="warn",
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME", True, "25.3.0")
            host_requirements._check_graalvm_home("GRAALVM_HOME_25_0", True, "25.0.4")

        mismatched_version = host_requirements.results[1]
        self.assertEqual("WARN", mismatched_version.status)
        self.assertFalse(mismatched_version.blocks_work)
        self.assertTrue(host_requirements.results[2].blocks_work)

    @patch("utility_scripts.host_requirements.run_command")
    def test_pinned_lane_compares_the_graalvm_product_version_not_the_jdk_release(self, command: Mock) -> None:
        # Both builds report `native-image 25.0.4`; only the product version separates them.
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.0.4 2026-07-21\n"
            "GraalVM Runtime Environment GraalVM CE 25.2.4+7.1 (build 25.0.4+7-jvmci-25.2-b20)\n",
            "",
        )
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME_25_0": graalvm_home},
                graalvm_version_check="strict",
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME_25_0", True, "25.0.4")

        latest_ga_build = host_requirements.results[-1]
        self.assertEqual("FAIL", latest_ga_build.status)
        self.assertIn("installed=25.2.4+7.1", latest_ga_build.detail)
        self.assertIn("required=25.0.4", latest_ga_build.detail)

    @patch("utility_scripts.host_requirements.run_command")
    def test_pinned_lane_accepts_the_pinned_oracle_graalvm_build(self, command: Mock) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["native-image", "--version"],
            0,
            "native-image 25.0.4 2026-07-21\n"
            "GraalVM Runtime Environment Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS-jvmci-b01)\n",
            "",
        )
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME_25_0": graalvm_home},
                graalvm_version_check="strict",
            )

            host_requirements._check_graalvm_home("GRAALVM_HOME_25_0", True, "25.0.4")

        self.assertEqual("PASS", host_requirements.results[-1].status)
        self.assertIn("installed=25.0.4+7.1", host_requirements.results[-1].detail)

    def test_disabled_version_check_skips_version_resolution_and_comparison(self) -> None:
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                "/repo/forge",
                "python3",
                "gpt-5.6-terra",
                {"GRAALVM_HOME": graalvm_home, "FORGE_WORK_LIMIT": "1"},
                graalvm_version_check="off",
            )

            with patch("utility_scripts.host_requirements.run_command") as command:
                host_requirements._check_graalvm_home("GRAALVM_HOME", True, None)

        command.assert_not_called()
        installation, version = host_requirements.results
        self.assertEqual("PASS", installation.status)
        self.assertEqual("SKIP", version.status)
        self.assertIn("version match disabled", version.detail)

    def test_version_check_mode_is_read_from_the_environment_and_validated(self) -> None:
        self.assertEqual("warn", resolve_graalvm_version_check(None, {"FORGE_GRAALVM_VERSION_CHECK": "warn"}))
        self.assertEqual("off", resolve_graalvm_version_check("off", {"FORGE_GRAALVM_VERSION_CHECK": "strict"}))
        self.assertEqual("strict", resolve_graalvm_version_check(None, {}))
        with self.assertRaises(ValueError):
            resolve_graalvm_version_check("lenient", {})

    def test_graalvm_installation_problems_are_named_once(self) -> None:
        with _graalvm_home(native_image=False, schema=False) as graalvm_home:
            problems = check_graalvm_installation(graalvm_home)

        self.assertEqual(
            [
                os.path.join("bin", "native-image") + " is missing or not executable",
                f"{GRAALVM_SCHEMA_PATH} is missing",
            ],
            problems,
        )

    def test_codex_managed_policy_rejects_unattended_recovery(self) -> None:
        # Codex writes the managed requirements under `signed_payload.bundle`.
        passed, detail = _codex_policy_status(_codex_bundle_cache(
            'allowed_approval_policies = ["on-request", "untrusted"]\n'
            'allowed_sandbox_modes = ["read-only", "workspace-write"]\n'
        ))

        self.assertFalse(passed)
        self.assertIn("Codex Developers", detail)
        self.assertIn("`never` is disallowed", detail)
        self.assertIn("`danger-full-access` is disallowed", detail)

    def test_codex_managed_policy_accepts_unattended_recovery(self) -> None:
        passed, detail = _codex_policy_status(_codex_bundle_cache(
            'allowed_approval_policies = ["never", "on-request"]\n'
            'allowed_sandbox_modes = ["danger-full-access", "workspace-write"]\n'
        ))

        self.assertTrue(passed)
        self.assertIn("Codex Developers", detail)
        self.assertIn("compatible with unattended recovery", detail)

    def test_codex_managed_policy_reads_the_flat_requirements_shape(self) -> None:
        contents = (
            'allowed_approval_policies = ["on-request"]\n'
            'allowed_sandbox_modes = ["workspace-write"]\n'
        )
        flat_bundle = {
            "requirements_toml": {
                "enterprise_managed": [{"name": "Codex Developers", "contents": contents}],
            },
        }

        passed, detail = _codex_policy_status(flat_bundle)

        self.assertFalse(passed)
        self.assertIn("`never` is disallowed", detail)

    def test_github_permission_results_name_each_required_mutation_boundary(self) -> None:
        environment = {
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "1",
        }
        host_requirements = HostRequirements("/repo/forge", "python3", "gpt-5.6-terra", environment)

        host_requirements._record_github_permissions(
            _github_permission_response("oracle/graalvm-reachability-metadata", "MAINTAIN"),
            "oracle/graalvm-reachability-metadata",
            "git@github.com:oracle/graalvm-reachability-metadata.git",
        )

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertTrue(result_by_name["oracle repository mutations"].passed)
        self.assertTrue(result_by_name["oracle project 30 updates"].passed)
        push_target = result_by_name["generated-branch push target"]
        self.assertTrue(push_target.passed)
        self.assertIn("repository=oracle/graalvm-reachability-metadata", push_target.detail)

    def test_generated_branch_push_target_is_the_selected_repository_origin(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            "gpt-5.6-terra",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=False),
        )

        host_requirements._record_github_permissions(
            _github_permission_response("acme/graalvm-reachability-metadata", "WRITE"),
            "acme/graalvm-reachability-metadata",
            "git@github.com:acme/graalvm-reachability-metadata.git",
        )

        push_target = {result.name: result for result in host_requirements.results}["generated-branch push target"]
        self.assertTrue(push_target.passed)
        self.assertIn("repository=acme/graalvm-reachability-metadata", push_target.detail)
        self.assertIn("acme/graalvm-reachability-metadata", push_target.remediation)

    def test_push_target_check_resolves_the_origin_of_the_selected_repository(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            "gpt-5.6-terra",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=False),
            repo_dir="/selected/repo",
        )

        with patch("utility_scripts.host_requirements.run_command") as command:
            command.return_value = subprocess.CompletedProcess(
                ["git"],
                0,
                "https://github.com/acme/graalvm-reachability-metadata.git\n",
                "",
            )
            push_slug, push_remote = host_requirements._resolve_push_target()

        self.assertEqual(
            ["git", "-C", "/selected/repo", "remote", "get-url", "origin"],
            command.call_args.args[0],
        )
        self.assertEqual("acme/graalvm-reachability-metadata", push_slug)
        self.assertEqual("https://github.com/acme/graalvm-reachability-metadata.git", push_remote)

    def test_non_github_origin_fails_the_push_target_with_one_fix(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            "gpt-5.6-terra",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=False),
        )

        host_requirements._record_github_permissions(
            _github_permission_response(None, None),
            None,
            "/srv/mirrors/graalvm-reachability-metadata.git",
        )

        push_target = {result.name: result for result in host_requirements.results}["generated-branch push target"]
        self.assertEqual("FAIL", push_target.status)
        self.assertIn("is not a GitHub remote", push_target.detail)
        self.assertIn("`origin`", push_target.remediation)

    def test_required_failure_stops_before_work_and_prints_remediation(self) -> None:
        environment = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "1",
        }
        host_requirements = HostRequirements("/repo/forge", "python3", "gpt-5.6-terra", environment)

        def fail_tools() -> None:
            host_requirements._add(
                "tool",
                "GitHub CLI",
                True,
                False,
                "gh was not found",
                "Install gh.",
            )

        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch.object(host_requirements, "_check_tools", side_effect=fail_tools), \
                patch.object(host_requirements, "_check_environment"), \
                patch.object(host_requirements, "_check_write_permissions"), \
                patch.object(host_requirements, "_check_network"), \
                patch.object(host_requirements, "_check_github"), \
                patch.object(host_requirements, "_check_pi"), \
                patch.object(host_requirements, "_check_codex"), \
                patch.object(host_requirements, "_check_docker"), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            passed = host_requirements.run()

        self.assertFalse(passed)
        self.assertIn("[FAIL] tool: GitHub CLI", stdout.getvalue())
        self.assertIn("Fix: Install gh.", stdout.getvalue())
        self.assertIn("No work was started", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())

    def _run_with_only(self, host_requirements: HostRequirements, *kept: str) -> tuple[bool, str]:
        """Run the gate with every check but the named ones stubbed out."""
        stubbed = [
            name for name in (
                "_check_tools",
                "_check_environment",
                "_check_write_permissions",
                "_check_network",
                "_check_github",
                "_check_pi",
                "_check_codex",
                "_check_docker",
            )
            if name not in kept
        ]
        stdout = io.StringIO()
        with ExitStack() as stack:
            for name in stubbed:
                stack.enter_context(patch.object(host_requirements, name))
            stack.enter_context(redirect_stdout(stdout))
            passed = host_requirements.run()
        return passed, stdout.getvalue()

    def test_disabled_version_check_queries_no_upstream_release(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            "gpt-5.6-terra",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=False),
            graalvm_version_check="off",
        )

        with patch("utility_scripts.host_requirements.run_command") as command:
            passed, _output = self._run_with_only(host_requirements)

        command.assert_not_called()
        self.assertTrue(passed)

    def test_runs_without_live_github_skip_upstream_version_lookups(self) -> None:
        forge_dir = str(Path(__file__).resolve().parents[1])
        with _graalvm_home() as graalvm_home:
            host_requirements = HostRequirements(
                forge_dir,
                "python3",
                "gpt-5.6-terra",
                {variable: graalvm_home for variable in (
                    "GRAALVM_HOME",
                    "GRAALVM_HOME_25_0",
                    "GRAALVM_HOME_LATEST_EA",
                )},
                requirements=QueueRequirements(issue_work=True, review_work=False, github_work=False),
                graalvm_version_check="strict",
            )

            with patch("utility_scripts.host_requirements.run_command") as command:
                command.return_value = subprocess.CompletedProcess(
                    ["native-image", "--version"],
                    0,
                    "native-image 25.0.4 2026-07-21\n"
                    "GraalVM Runtime Environment Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS-jvmci-b01)\n",
                    "",
                )
                passed, _output = self._run_with_only(host_requirements, "_check_environment")

        issued = [call.args[0] for call in command.call_args_list]
        self.assertEqual([], [entry for entry in issued if entry[:2] == ["gh", "api"]])
        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertEqual("SKIP", result_by_name["latest GraalVM GA"].status)
        self.assertEqual("SKIP", result_by_name["latest Oracle GraalVM EA"].status)
        self.assertIn("live GitHub access", result_by_name["latest GraalVM GA"].detail)
        self.assertEqual("SKIP", result_by_name["GRAALVM_HOME version"].status)
        self.assertEqual("SKIP", result_by_name["GRAALVM_HOME_LATEST_EA version"].status)
        # The locally pinned lane and every installation check stay mandatory.
        self.assertEqual("PASS", result_by_name["pinned GraalVM 25.0.x"].status)
        self.assertEqual("PASS", result_by_name["GRAALVM_HOME_25_0 version"].status)
        self.assertEqual("PASS", result_by_name["GRAALVM_HOME"].status)
        self.assertEqual("INFO", result_by_name["JAVA_HOME alignment"].status)
        self.assertTrue(passed)

    @patch("utility_scripts.host_requirements.resolve_executable", return_value="/usr/bin/docker")
    @patch("utility_scripts.host_requirements.run_command")
    def test_docker_daemon_check_does_not_require_docker_specific_template_fields(
            self,
            command: Mock,
            _resolve: Mock,
    ) -> None:
        command.return_value = subprocess.CompletedProcess(
            ["docker", "info"],
            0,
            "host:\n  arch: amd64\n",
            "",
        )
        host_requirements = HostRequirements("/repo/forge", "python3", "gpt-5.6-terra", {"FORGE_WORK_LIMIT": "1"})

        host_requirements._check_docker()

        self.assertEqual(["docker", "info"], command.call_args.args[0])
        self.assertGreaterEqual(command.call_args.kwargs["timeout"], 60)
        self.assertTrue(host_requirements.results[-1].passed)

    def test_runs_without_live_github_skip_github_permissions_and_api_hosts(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            "gpt-5.6-terra",
            {},
            requirements=QueueRequirements(issue_work=True, review_work=False, github_work=False),
            repo_dir="/selected/repo",
        )

        with patch("utility_scripts.host_requirements.probe_tcp_host", return_value=(True, "reachable")), \
                patch("utility_scripts.host_requirements.run_command") as command:
            host_requirements._check_github()
            host_requirements._check_network()

        command.assert_not_called()
        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertEqual("SKIP", result_by_name["authentication and permissions"].status)
        self.assertEqual("SKIP", result_by_name["github.com"].status)
        self.assertEqual("SKIP", result_by_name["api.github.com"].status)
        self.assertEqual("SKIP", result_by_name["Forge git self-update"].status)
        self.assertEqual("SKIP", result_by_name["Selected repository git remote"].status)
        self.assertEqual("PASS", result_by_name["chatgpt.com"].status)

    def test_selected_repository_paths_are_checked_instead_of_the_forge_parent_checkout(self) -> None:
        with tempfile.TemporaryDirectory() as target_repo:
            gradlew = Path(target_repo) / "gradlew"
            gradlew.touch(mode=0o755)
            host_requirements = HostRequirements(
                "/parent/forge",
                "python3",
                "gpt-5.6-terra",
                {"FORGE_MONITORED_BRANCH": "origin/forge/experiment"},
                requirements=QueueRequirements(issue_work=True, review_work=False),
                repo_dir=target_repo,
            )

            with patch("utility_scripts.host_requirements.run_command") as command, \
                    patch.dict(os.environ, {}, clear=True):
                command.side_effect = _fake_git_and_gradle_command
                host_requirements._check_gradle_wrapper(True)
                gradle_probe_environment = command.call_args.args[1]
                host_requirements._check_write_permissions()
                host_requirements._check_git_remote_access()
                expected_gradle_home = gradle_user_home_for_repo(target_repo)
                ls_remote_commands = [
                    call.args[0] for call in command.call_args_list if "ls-remote" in call.args[0]
                ]

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertIn(str(gradlew), result_by_name["Gradle wrapper"].detail)
        # The probe must exercise the per-repo Gradle home real Forge work uses.
        self.assertEqual(expected_gradle_home, gradle_probe_environment["GRADLE_USER_HOME"])
        self.assertIn(expected_gradle_home, result_by_name["Gradle wrapper"].detail)
        self.assertIn("/parent/.git", result_by_name["Forge git metadata"].detail)
        self.assertIn(
            os.path.join(target_repo, ".git"),
            result_by_name["Selected repository git metadata"].detail,
        )
        self.assertTrue(result_by_name["Selected repository git metadata"].required)
        self.assertIn(f"checkout={target_repo}", result_by_name["Selected repository git remote"].detail)
        self.assertIn("checkout=/parent/forge", result_by_name["Forge git self-update"].detail)
        # Each checkout is asked for the branch it actually needs.
        self.assertEqual(
            ["refs/heads/forge/experiment", "refs/heads/master"],
            [remote_command[-1] for remote_command in ls_remote_commands],
        )

    def test_git_metadata_probe_follows_a_linked_worktree_to_the_real_git_directory(self) -> None:
        with patch("utility_scripts.host_requirements.run_command") as command:
            command.return_value = subprocess.CompletedProcess(["git"], 0, ".git\n", "")
            plain = resolve_git_metadata_dir("/checkout/main", {})

            # A linked worktree reports the main checkout's git directory, relative or absolute.
            command.return_value = subprocess.CompletedProcess(["git"], 0, "../../.git\n", "")
            relative = resolve_git_metadata_dir("/checkout/main/.worktrees/review", {})

            command.return_value = subprocess.CompletedProcess(["git"], 0, "/checkout/main/.git\n", "")
            absolute = resolve_git_metadata_dir("/elsewhere/review", {})

            command.return_value = subprocess.CompletedProcess(["git"], 128, "", "not a git repository\n")
            fallback = resolve_git_metadata_dir("/checkout/plain", {})

        self.assertEqual(["git", "-C", "/checkout/plain", "rev-parse", "--git-common-dir"], command.call_args.args[0])
        self.assertEqual("/checkout/main/.git", plain)
        self.assertEqual("/checkout/main/.git", relative)
        self.assertEqual("/checkout/main/.git", absolute)
        self.assertEqual("/checkout/plain/.git", fallback)

    def test_selected_repository_checks_collapse_when_forge_owns_the_checkout(self) -> None:
        host_requirements = HostRequirements(
            "/repo/forge",
            "python3",
            "gpt-5.6-terra",
            {},
            requirements=QueueRequirements(issue_work=False, review_work=True),
        )

        with patch("utility_scripts.host_requirements.run_command") as command:
            command.side_effect = _fake_git_and_gradle_command
            host_requirements._check_write_permissions()
            host_requirements._check_git_remote_access()

        result_by_name = {result.name: result for result in host_requirements.results}
        self.assertEqual("SKIP", result_by_name["Selected repository git metadata"].status)
        self.assertEqual("SKIP", result_by_name["Selected repository git remote"].status)
        self.assertIn("checkout that contains Forge", result_by_name["Selected repository git remote"].detail)

    def test_network_probes_follow_the_configured_proxy(self) -> None:
        proxied = {
            "https_proxy": "http://10.0.0.1:80",
            "no_proxy": "localhost,.internal.example,*.wildcard.example,ported.example:443",
        }

        self.assertEqual(("10.0.0.1", 80), resolve_https_proxy(proxied, "github.com"))
        self.assertIsNone(resolve_https_proxy(proxied, "build.internal.example"))
        self.assertIsNone(resolve_https_proxy(proxied, "build.wildcard.example"))
        self.assertIsNone(resolve_https_proxy(proxied, "wildcard.example"))
        self.assertIsNone(resolve_https_proxy(proxied, "ported.example"))
        self.assertIsNone(resolve_https_proxy({}, "github.com"))
        self.assertEqual(("proxy.example", 80), resolve_https_proxy({"HTTPS_PROXY": "proxy.example"}, "github.com"))

    def test_proxy_tunnel_result_reports_both_hops(self) -> None:
        connection = Mock()
        connection.__enter__ = Mock(return_value=connection)
        connection.__exit__ = Mock(return_value=False)
        connection.recv.return_value = b"HTTP/1.1 200 Connection established\r\n\r\n"

        with patch("utility_scripts.host_requirements.socket.create_connection", return_value=connection):
            passed, detail = probe_proxied_host("github.com", 443, ("10.0.0.1", 80))

        self.assertTrue(passed)
        self.assertIn("via proxy 10.0.0.1:80", detail)
        connection.sendall.assert_called_once_with(
            b"CONNECT github.com:443 HTTP/1.1\r\nHost: github.com:443\r\n\r\n"
        )

    def test_proxy_refusal_is_reported_as_unreachable(self) -> None:
        connection = Mock()
        connection.__enter__ = Mock(return_value=connection)
        connection.__exit__ = Mock(return_value=False)
        connection.recv.return_value = b"HTTP/1.1 403 Forbidden\r\n\r\n"

        with patch("utility_scripts.host_requirements.socket.create_connection", return_value=connection):
            passed, detail = probe_proxied_host("registry-1.docker.io", 443, ("10.0.0.1", 80))

        self.assertFalse(passed)
        self.assertIn("403 Forbidden", detail)

    def test_worker_validates_host_requirements_before_first_cycle(self) -> None:
        worker_path = Path(__file__).resolve().parents[1] / "do_up_to_date_work.sh"
        worker = worker_path.read_text(encoding="utf-8")
        cycle = worker[worker.index("\nrun_cycle() {"):]

        gate = cycle.index("if ! run_host_requirements; then")
        rate_limits = cycle.index("if ! display_github_rate_limits; then")

        # An exhausted GitHub API limit must skip the cycle instead of failing the gate,
        # and the gate must still stop the worker before self-update or queue work.
        self.assertLess(rate_limits, gate)
        self.assertLess(gate, cycle.index("update_metadata_forge"))
        self.assertLess(gate, cycle.index("process_work_queues"))
        # The gate no longer runs at top level, where a rate-limit failure would kill the loop.
        self.assertNotIn("\nrun_host_requirements\n", worker)


if __name__ == "__main__":
    unittest.main()
