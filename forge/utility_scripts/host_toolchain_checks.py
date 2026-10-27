# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Installed-tooling checks: required executables, grype, the Gradle wrapper, selected agent backends, and Docker."""

from __future__ import annotations

import os
from typing import Sequence

from ai_workflows.agents.agent_runtime import default_agent_for_backend
from utility_scripts.host_probes import (
    check_pi_authentication,
    codex_doctor_provider_status,
    codex_unattended_policy_status,
    first_output_line,
    parse_gradle_version,
    parse_grype_version,
    resolve_codex_state_root,
    resolve_executable,
    resolve_gradle_state_root,
    run_command,
)

REQUIRED_GRYPE_VERSION = "0.104.0"


class ToolchainChecks:
    """Tool, agent-backend, and Docker checks for the selected run capabilities.

    Mixed into ``HostRequirements``; validates only the backends selected for
    enabled runtime roles (§FS-forge-agent-runtime-selection).
    """

    def _check_tools(self) -> None:
        any_work = self.requirements.any_work
        selected_agent_commands = [(self.analysis_family, self.analysis_agent)]
        if self.requirements.issue_work:
            setup_command = (self.setup_family, self.setup_agent)
            if setup_command not in selected_agent_commands:
                selected_agent_commands.append(setup_command)
        if self.requirements.issue_work:
            for requirement in self.test_requirements:
                test_command = (requirement.family, requirement.agent)
                if test_command not in selected_agent_commands:
                    selected_agent_commands.append(test_command)
        tool_specs: tuple[tuple[str, str, bool, tuple[str, ...]], ...] = (
            ("Python", self.python_bin, True, ("--version",)),
            ("git", "git", True, ("--version",)),
            ("GitHub CLI", "gh", self.requirements.github_work, ("--version",)),
            *tuple(
                (
                    f"Agent backend {family}",
                    command,
                    any_work,
                    ("--version",),
                )
                for family, command in selected_agent_commands
            ),
            ("Docker CLI", "docker", self.requirements.build_work, ("--version",)),
        )
        for name, command, required, version_args in tool_specs:
            self._check_tool(name, command, required, version_args)

        self._check_grype()
        self._check_gradle_wrapper(any_work)

    def _check_grype(self) -> None:
        required = self.requirements.issue_work
        resolved = resolve_executable("grype")
        if not required:
            self._add("tool", "grype", False, None, resolved or "not required by this run")
            return
        if resolved is None:
            self._add(
                "tool",
                "grype",
                True,
                False,
                f"current=missing; required={REQUIRED_GRYPE_VERSION}",
                "Install grype 0.104.0 and ensure `grype` is on PATH.",
            )
            return
        result = run_command([resolved, "version"], self.environment)
        installed = parse_grype_version(f"{result.stdout}\n{result.stderr}")
        self._add(
            "tool",
            "grype",
            True,
            result.returncode == 0 and installed == REQUIRED_GRYPE_VERSION,
            f"path={resolved}; current={installed or '<unresolved>'}; required={REQUIRED_GRYPE_VERSION}",
            "Install grype 0.104.0 and ensure that version is first on PATH.",
        )

    def _check_gradle_wrapper(self, required: bool) -> None:
        gradlew = os.path.join(self.repo_dir, "gradlew")
        gradle_ok = os.path.isfile(gradlew) and os.access(gradlew, os.X_OK)
        if not required or not gradle_ok:
            self._add(
                "tool",
                "Gradle wrapper",
                required,
                gradle_ok if required else None,
                gradlew,
                f"Restore the executable repository wrapper and run `chmod +x {gradlew}`.",
            )
            return
        gradle_environment = dict(self.environment)
        gradle_environment["GRADLE_USER_HOME"] = resolve_gradle_state_root(self.environment)
        result = run_command(
            [gradlew, "--version", "--no-daemon"],
            gradle_environment,
            timeout=120,
        )
        installed_version = parse_gradle_version(f"{result.stdout}\n{result.stderr}")
        self._add(
            "tool",
            "Gradle wrapper",
            True,
            result.returncode == 0,
            f"path={gradlew}; version={installed_version or '<unresolved>'}",
            "Allow wrapper execution, Gradle-state writes, and services.gradle.org access; then run "
            f"`{gradlew} --version --no-daemon` successfully.",
        )

    def _check_tool(
            self,
            name: str,
            command: str,
            required: bool,
            version_args: Sequence[str],
    ) -> None:
        resolved = resolve_executable(command)
        if not required:
            self._add("tool", name, False, None, resolved or f"{command} is not required by this run")
            return
        if resolved is None:
            self._add(
                "tool",
                name,
                True,
                False,
                f"`{command}` was not found on PATH",
                f"Install {name} and ensure `{command}` is on PATH.",
            )
            return
        result = run_command([resolved, *version_args], self.environment)
        version = first_output_line(result) or f"exit={result.returncode}"
        self._add(
            "tool",
            name,
            True,
            result.returncode == 0,
            f"{resolved} ({version})",
            f"Repair the `{command}` installation; `{command} {' '.join(version_args)}` must exit 0.",
        )

    def _check_selected_agents(self) -> None:
        """Validate only the backends selected for enabled runtime roles.

        §FS-forge-agent-runtime-selection
        """
        roles: list[tuple[str, str, str, str, bool, str | None]] = [
            (
                "analysis",
                self.analysis_family,
                self.analysis_agent,
                self.analysis_model,
                self.requirements.any_work,
                self.analysis_provider,
            ),
            (
                "setup",
                self.setup_family,
                self.setup_agent,
                self.setup_model,
                self.requirements.issue_work,
                self.setup_provider,
            ),
        ]
        roles.extend(
            (
                (
                    f"test ({requirement.strategy_name})"
                    if requirement.strategy_name
                    else "test"
                ),
                requirement.family,
                requirement.agent,
                requirement.model,
                self.requirements.issue_work,
                requirement.provider,
            )
            for requirement in self.test_requirements
        )
        for role, backend, agent, model, required, provider in roles:
            if not required:
                self._add("agent", f"{role} role", False, None, "not required by this run")
                continue
            executable = agent or default_agent_for_backend(backend)
            if resolve_executable(executable) is None:
                self._add(
                    "agent",
                    f"{role} role authentication",
                    True,
                    False,
                    f"backend={backend}; executable={executable}; model={model}; unavailable",
                    f"Install and authenticate `{executable}`, or select another {role} agent.",
                )
                continue
            ready, detail = self._selected_agent_authentication(backend, agent, model, provider)
            self._add(
                "agent",
                f"{role} role authentication",
                True,
                ready,
                f"backend={backend}; model={model}; {detail}",
                f"Authenticate `{executable}` for model `{model}` before starting Forge.",
            )

    def _selected_agent_authentication(
            self,
            backend: str,
            agent: str,
            model: str,
            provider: str | None = None,
    ) -> tuple[bool, str]:
        """Run a backend-specific authentication probe that never invokes a model."""
        if backend == "pi":
            return check_pi_authentication(model, self.environment, agent, provider)
        if backend == "codex":
            result = run_command([agent, "login", "status"], self.environment)
            return result.returncode == 0, first_output_line(result) or "codex login status failed"
        if backend == "claude-code":
            result = run_command([agent, "auth", "status", "--json"], self.environment)
            return result.returncode == 0, first_output_line(result) or "claude auth status failed"
        result = run_command([agent, "auth", "list"], self.environment)
        return result.returncode == 0, first_output_line(result) or "opencode auth list failed"

    def _check_codex(self) -> None:
        if not self.requirements.issue_work:
            self._add("agent", "Codex recovery", False, None, "not required by this run")
            return
        if resolve_executable("codex") is None:
            self._add("agent", "Codex recovery", True, False, "Codex is unavailable", "Install and authenticate Codex.")
            return

        login = run_command(["codex", "login", "status"], self.environment)
        self._add(
            "agent",
            "Codex authentication",
            True,
            login.returncode == 0,
            first_output_line(login) or "codex login status returned no output",
            "Run `codex login` before starting Forge.",
        )

        doctor = run_command(["codex", "doctor", "--json"], self.environment, timeout=30)
        provider_ok, doctor_detail = codex_doctor_provider_status(doctor.stdout)
        self._add(
            "agent",
            "Codex provider network",
            True,
            provider_ok,
            doctor_detail,
            "Allow Codex provider HTTPS/WebSocket access; run `codex doctor --json` for the complete diagnosis.",
        )

        compatible, policy_detail = codex_unattended_policy_status(resolve_codex_state_root(self.environment))
        self._add(
            "agent",
            "Codex unattended command permissions",
            True,
            compatible,
            policy_detail,
            "Forge Codex recovery requires approval policy `never` and sandbox `workspace-write`; "
            "change the managed Codex requirements or replace the remaining Codex recovery lane.",
        )

    def _check_docker(self) -> None:
        if not self.requirements.build_work:
            self._add("docker", "daemon access", False, None, "not required by this run")
            return
        if resolve_executable("docker") is None:
            self._add("docker", "daemon access", True, False, "Docker CLI is unavailable", "Install Docker.")
            return
        result = run_command(["docker", "info"], self.environment)
        detail = "docker info succeeded" if result.returncode == 0 else (
            first_output_line(result) or f"docker info exited {result.returncode}"
        )
        self._add(
            "docker",
            "daemon access",
            True,
            result.returncode == 0,
            detail,
            "Start Docker and grant the current user access to the Docker daemon socket without sudo.",
        )
