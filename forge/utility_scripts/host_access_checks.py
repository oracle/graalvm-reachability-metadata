# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Filesystem, network, git-remote, and GitHub permission checks for a Forge run."""

from __future__ import annotations

import json
import os
import subprocess
import urllib.parse
import uuid

from git_scripts.common_git import find_remote_for_github_repo
from utility_scripts.host_probes import (
    GITHUB_RATE_LIMIT_FIX,
    agent_provider_host,
    first_output_line,
    github_http_status,
    is_github_rate_limited,
    is_transient_http_status,
    probe_http_200,
    probe_write_access,
    resolve_agent_state_root,
    resolve_executable,
    resolve_gradle_state_root,
    resolve_https_proxy,
    run_command,
)
from utility_scripts.native_image_artifact import ARTIFACT_REPOSITORY_URLS

REPOSITORY_OWNER = "oracle"
REPOSITORY_NAME = "graalvm-reachability-metadata"
PROJECT_NUMBER = 30
WRITE_REPOSITORY_PERMISSIONS = {"WRITE", "MAINTAIN", "ADMIN"}
NETWORK_HOSTS_GITHUB = ("github.com", "api.github.com")
NETWORK_HOSTS_WORK = ("services.gradle.org",)
NETWORK_HOSTS_ISSUE_WORK = (
    "plugins.gradle.org",
    "registry-1.docker.io",
    "auth.docker.io",
)


class AccessChecks:
    """Write, network, git, and GitHub access checks for the selected run.

    Mixed into ``HostRequirements``; every probe is deterministic and scoped by
    the resolved queue requirements (§FS-forge-host-requirements).
    """

    def _check_write_permissions(self) -> None:
        any_work = self.requirements.any_work
        paths: list[tuple[str, str, bool, str]] = [
            ("Forge checkout", self.forge_dir, True, ""),
            ("Forge git metadata", os.path.join(self.forge_repo_dir, ".git"), True, ""),
            (
                "Selected repository git metadata",
                os.path.join(self.repo_dir, ".git"),
                self.separate_repository,
                f"{self.repo_dir} is the checkout that contains Forge; covered by the Forge git metadata check",
            ),
            ("Forge local repositories", os.path.join(self.forge_dir, "local_repositories"), any_work, ""),
            ("Gradle state", resolve_gradle_state_root(self.environment), any_work, ""),
        ]
        selected_state_roots = {
            self.analysis_family: resolve_agent_state_root(self.analysis_family, self.environment),
        }
        if self.requirements.issue_work:
            selected_state_roots[self.setup_family] = resolve_agent_state_root(
                self.setup_family, self.environment
            )
        if self.review_role is not None:
            selected_state_roots[self.review_role.family] = resolve_agent_state_root(
                self.review_role.family, self.environment
            )
        paths.extend(
            (f"{backend} state", path, any_work, "")
            for backend, path in selected_state_roots.items()
        )
        for name, path, required, skip_detail in paths:
            if not required:
                self._add("filesystem", name, False, None, skip_detail or f"{path} is not required by this run")
                continue
            passed, detail = probe_write_access(path)
            self._add(
                "filesystem",
                name,
                True,
                passed,
                detail,
                f"Grant the current user write access to `{path}` and run Forge outside a read-only sandbox.",
            )

    def _check_network(self) -> None:
        self._check_git_remote_access()
        self._check_artifact_repositories()
        hosts: list[tuple[str, bool]] = [
            (host, self.requirements.github_work) for host in NETWORK_HOSTS_GITHUB
        ]
        hosts.extend((host, self.requirements.any_work) for host in NETWORK_HOSTS_WORK)
        hosts.extend((host, self.requirements.build_work) for host in NETWORK_HOSTS_ISSUE_WORK)
        if self.requirements.any_work:
            hosts.append((agent_provider_host(
                self.analysis_family,
                self.analysis_model,
                self.analysis_provider,
            ), True))
        if self.requirements.issue_work:
            hosts.append((agent_provider_host(
                self.setup_family,
                self.setup_model,
                self.setup_provider,
            ), True))
        if self.review_role is not None:
            hosts.append((agent_provider_host(self.review_role.family, self.review_role.model), True))
        required_by_host: dict[str, bool] = {}
        for host, required in hosts:
            required_by_host[host] = required_by_host.get(host, False) or required
        for host, required in required_by_host.items():
            if not required:
                self._add("network", host, False, None, "not required by this run")
                continue
            probe = self._probe_host(host)
            proxy = resolve_https_proxy(self.environment, host)
            self._add(
                "network",
                host,
                True,
                probe.passed,
                probe.detail,
                (
                    f"Allow the configured proxy {proxy[0]}:{proxy[1]} to reach `{host}:443`, "
                    "or unset the proxy for it."
                    if proxy
                    else f"Allow DNS and outbound TCP 443 access to `{host}` in the host firewall or sandbox policy."
                ),
                transient=probe.transient,
            )
        if self.requirements.build_work:
            self._add(
                "network",
                "library-specific sources and Docker registries",
                True,
                None,
                "Targets are discovered from each selected library and cannot be enumerated before queue selection",
                "Allow outbound HTTPS to source-code/documentation URLs and registries declared by selected libraries.",
            )

    def _check_artifact_repositories(self) -> None:
        """Require HTTP 200 from every repository used by issue-form artifact lookup.

        §FS-forge-host-requirements
        """
        for repository_url in ARTIFACT_REPOSITORY_URLS:
            name = f"artifact repository {repository_url}"
            if not self.requirements.build_work:
                self._add("network", name, False, None, "not required by this run")
                continue
            probe = probe_http_200(repository_url)
            self._add(
                "network",
                name,
                True,
                probe.passed,
                probe.detail,
                (
                    f"Make `HEAD {repository_url.rstrip('/')}/` return HTTP 200 from this host; "
                    "check DNS, TLS, proxy or firewall policy, and repository availability."
                ),
                transient=probe.transient,
            )

    def _check_git_remote_access(self) -> None:
        self._check_git_remote("Forge git self-update", self.forge_dir, True, "")
        self._check_git_remote(
            "Selected repository git remote",
            self.repo_dir,
            self.separate_repository,
            f"{self.repo_dir} is the checkout that contains Forge; covered by the Forge self-update check",
        )
        self._check_generated_branch_push_access()

    def _check_generated_branch_push_access(self) -> None:
        """Verify a dry-run push through the remote publication will use.

        A fetch can succeed anonymously, so it does not prove that the later
        generated-branch push can authenticate and write
        (§FS-forge-host-requirements).
        """
        name: str = "generated-branch push access"
        required: bool = self.requirements.build_work and self.requirements.github_work
        if not required:
            self._add(
                "github",
                name,
                False,
                None,
                "this run does not publish a generated branch",
            )
            return

        repository: str = f"{REPOSITORY_OWNER}/{REPOSITORY_NAME}"
        try:
            publication_remote: str = find_remote_for_github_repo(
                repository,
                cwd=self.repo_dir,
            )
        except (OSError, RuntimeError, subprocess.CalledProcessError):
            self._add(
                "github",
                name,
                True,
                False,
                f"checkout={self.repo_dir}, repository={repository}, remote=unavailable",
                f"Configure a Git remote that points to `{repository}`.",
            )
            return

        push_url_result: subprocess.CompletedProcess[str] = run_command(
            [
                "git", "-C", self.repo_dir,
                "remote", "get-url", "--push", publication_remote,
            ],
            self.environment,
        )
        push_url: str = (
            first_output_line(push_url_result)
            if push_url_result.returncode == 0
            else ""
        )
        parsed_url: urllib.parse.ParseResult = urllib.parse.urlparse(push_url)
        if parsed_url.scheme == "https" and parsed_url.hostname == "github.com":
            protocol: str = "https"
            remediation: str = (
                "Run `gh auth setup-git --hostname github.com` for an account "
                f"with write access to `{repository}`."
            )
        elif push_url.startswith("git@github.com:") or parsed_url.scheme == "ssh":
            protocol = "ssh"
            remediation = (
                "Load a GitHub-authorized SSH key for an account with write access "
                f"to `{repository}`."
            )
        else:
            protocol = "configured"
            remediation = (
                f"Configure remote `{publication_remote}` with noninteractive write "
                f"access to `{repository}`."
            )

        target_ref: str = f"refs/heads/ai/forge-host-check-{uuid.uuid4().hex}"
        push_result: subprocess.CompletedProcess[str] = run_command(
            [
                "git", "-C", self.repo_dir,
                "push", "--dry-run", "--no-verify", "--porcelain",
                publication_remote, f"HEAD:{target_ref}",
            ],
            self.environment,
            timeout=60,
        )
        passed: bool = push_result.returncode == 0
        self._add(
            "github",
            name,
            True,
            passed,
            f"checkout={self.repo_dir}, repository={repository}, "
            f"remote={publication_remote}, protocol={protocol}, "
            f"dry-run push={'passed' if passed else 'failed'}",
            remediation,
            transient=self._github_failure_transient(push_result),
        )

    def _check_git_remote(self, name: str, checkout: str, required: bool, skip_detail: str) -> None:
        """Check that one checkout reaches the monitored branch through its own origin remote."""
        if not required:
            self._add("network", name, False, None, skip_detail)
            return
        monitored_branch = self.environment.get("FORGE_MONITORED_BRANCH", "origin/master")
        branch = monitored_branch.removeprefix("origin/")
        remote_result = run_command(
            ["git", "-C", checkout, "remote", "get-url", "origin"],
            self.environment,
        )
        remote_url = first_output_line(remote_result) or "origin"
        result = run_command(
            [
                "git", "-C", checkout,
                "ls-remote", "--exit-code", "origin", f"refs/heads/{branch}",
            ],
            self.environment,
        )
        self._add(
            "network",
            name,
            True,
            result.returncode == 0,
            f"checkout={checkout}, remote={remote_url}, branch={branch}, "
            f"status={'reachable' if result.returncode == 0 else 'unreachable'}",
            f"Grant git transport access to `{remote_url}` from `{checkout}` and ensure branch "
            f"`{branch}` exists on origin.",
            transient=self._github_failure_transient(result),
        )

    def _gh_login_failure(self, auth: subprocess.CompletedProcess[str]) -> tuple[bool, str]:
        """Judge a failed `gh auth status`; return whether it is transient and what GitHub said.

        gh reports a rate limit or a slow answer as an invalid token, so `gh api rate_limit`,
        which no limit counts, asks GitHub: a token it accepts, a 429 or a server error leaves
        the login fine, and a 401 is a host fault (§FS-forge-host-requirements.1).
        """
        if self._github_failure_transient(auth):
            return True, ""
        token_check = run_command(["gh", "api", "--include", "rate_limit"], self.environment)
        status: int | None = github_http_status(token_check)
        if status is None:
            no_answer: str = f"; gh api rate_limit: {first_output_line(token_check)}"
            return self._github_failure_transient(token_check), no_answer
        transient: bool = (
            status == 200 or is_transient_http_status(status) or self._github_failure_transient(token_check)
        )
        return transient, f"; gh api rate_limit answered HTTP {status}"

    def _check_github(self) -> None:
        required = self.requirements.github_work
        if not required or resolve_executable("gh") is None:
            self._add(
                "github",
                "authentication and permissions",
                required,
                None if not required else False,
                "GitHub checks require the gh CLI",
                "Install `gh`, put it on PATH, and run `gh auth login -h github.com`.",
            )
            return

        auth = run_command(["gh", "auth", "status", "--active", "--hostname", "github.com"], self.environment)
        auth_transient, token_note = self._gh_login_failure(auth) if auth.returncode != 0 else (False, "")
        self._add(
            "github",
            "gh authentication",
            True,
            auth.returncode == 0,
            f"{first_output_line(auth) or 'gh auth status returned no output'}{token_note}",
            GITHUB_RATE_LIMIT_FIX if is_github_rate_limited(auth)
            else "Wait for GitHub to confirm the login; the Forge launcher checks again on its own."
            if auth_transient
            else "Run `gh auth login -h github.com` with the account used by Forge.",
            transient=auth_transient,
        )
        if auth.returncode != 0:
            self._add(
                "github",
                "repository and project permissions",
                True,
                False,
                "permission query skipped because gh authentication failed",
                "Authenticate first, then grant Contents=write, Issues=write, Pull requests=write, and Projects=write.",
                transient=auth_transient,
            )
            return

        # A rate limit on this query is not a missing permission (§FS-forge-host-requirements.1).
        query = """
query($owner: String!, $name: String!, $project: Int!) {
  viewer { login }
  repository(owner: $owner, name: $name) { nameWithOwner viewerPermission }
  organization(login: $owner) {
    projectV2(number: $project) { id title viewerCanUpdate }
  }
}
""".strip()
        permission_result = run_command(
            [
                "gh", "api", "graphql",
                "-f", f"query={query}",
                "-F", f"owner={REPOSITORY_OWNER}",
                "-F", f"name={REPOSITORY_NAME}",
                "-F", f"project={PROJECT_NUMBER}",
            ],
            self.environment,
        )
        if permission_result.returncode != 0:
            rate_limited: bool = is_github_rate_limited(permission_result)
            detail: str = first_output_line(permission_result) or "GitHub GraphQL permission query failed"
            self._add(
                "github",
                "repository and project permissions",
                True,
                False,
                f"GitHub rate limit: {detail}" if rate_limited else detail,
                GITHUB_RATE_LIMIT_FIX if rate_limited else (
                    "Allow api.github.com and grant repository plus organization-project access "
                    "to the active gh account."
                ),
                transient=self._github_failure_transient(permission_result),
            )
            return
        self._record_github_permissions(permission_result.stdout)

    def _record_github_permissions(self, output: str) -> None:
        try:
            payload = json.loads(output)
            data = payload["data"]
            viewer = data["viewer"]
            target_repo = data["repository"]
            project = data["organization"]["projectV2"]
        except (json.JSONDecodeError, KeyError, TypeError):
            self._add(
                "github",
                "repository and project permissions",
                True,
                False,
                "GitHub permission response did not contain viewer, repository, and project data",
                "Verify the active gh account can read the repository and oracle project 30.",
            )
            return

        login = str(viewer.get("login") or "unknown")
        target_permission = str((target_repo or {}).get("viewerPermission") or "NONE")
        self._add(
            "github",
            "oracle repository mutations",
            True,
            target_permission in WRITE_REPOSITORY_PERMISSIONS,
            f"account={login}, repository={REPOSITORY_OWNER}/{REPOSITORY_NAME}, permission={target_permission}",
            "Grant the active account repository WRITE, MAINTAIN, or ADMIN permission "
            "(Issues and Pull requests write).",
        )

        project_can_update = bool((project or {}).get("viewerCanUpdate"))
        self._add(
            "github",
            f"oracle project {PROJECT_NUMBER} updates",
            True,
            project_can_update,
            (
                f"account={login}, project={(project or {}).get('title') or '<unavailable>'}, "
                f"viewerCanUpdate={project_can_update}"
            ),
            f"Grant the active account write access to oracle GitHub project {PROJECT_NUMBER}.",
        )

        self._add(
            "github",
            "generated-branch push target",
            self.requirements.build_work,
            target_permission in WRITE_REPOSITORY_PERMISSIONS if self.requirements.build_work else None,
            f"repository={REPOSITORY_OWNER}/{REPOSITORY_NAME}, permission={target_permission}",
            f"Grant the active account write access to `{REPOSITORY_OWNER}/{REPOSITORY_NAME}` "
            "to push generated branches.",
        )
