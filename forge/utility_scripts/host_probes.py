# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Deterministic host probes: command execution, network reachability, write access, agent state, and tool-output parsing."""

from __future__ import annotations

import json
import os
import re
import shutil
import socket
import subprocess
import tempfile
import tomllib
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Mapping, Sequence

PI_PROVIDER = "openai-codex"


def resolve_executable(command: str) -> str | None:
    """Resolve an executable command or absolute path."""
    if os.path.sep in command:
        return command if os.path.isfile(command) and os.access(command, os.X_OK) else None
    return shutil.which(command)


def probe_http_200(url: str, timeout: float = 20.0) -> tuple[bool, str]:
    """Return whether an HTTP HEAD request to a base URL returns exactly 200.

    §FS-forge-host-requirements
    """
    probe_url = f"{url.rstrip('/')}/"
    request = urllib.request.Request(probe_url, method="HEAD")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.getcode()
    except urllib.error.HTTPError as exc:
        return False, f"HEAD {probe_url} returned HTTP {exc.code}"
    except (OSError, urllib.error.URLError) as exc:
        return False, f"HEAD {probe_url} failed: {exc}"
    return status == 200, f"HEAD {probe_url} returned HTTP {status}"


def run_command(
        command: Sequence[str],
        environment: Mapping[str, str],
        timeout: int = 20,
) -> subprocess.CompletedProcess[str]:
    """Run one deterministic probe command without a shell or interactive prompt."""
    command_environment = dict(environment)
    command_environment["GH_PROMPT_DISABLED"] = "1"
    command_environment["GH_PAGER"] = ""
    command_environment["GIT_TERMINAL_PROMPT"] = "0"
    try:
        return subprocess.run(
            list(command),
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
            env=command_environment,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return subprocess.CompletedProcess(list(command), 1, "", str(exc))


def first_output_line(result: subprocess.CompletedProcess[str]) -> str:
    """Return one safe diagnostic line from a completed command."""
    output = "\n".join(part.strip() for part in (result.stdout, result.stderr) if part.strip())
    return output.splitlines()[0] if output else ""


def parse_grype_version(output: str) -> str | None:
    """Extract the release from `grype version`."""
    match = re.search(r"^Version:\s*v?(\S+)", output, flags=re.MULTILINE | re.IGNORECASE)
    return match.group(1) if match else None


def parse_gradle_version(output: str) -> str | None:
    """Extract the release from Gradle wrapper version output."""
    match = re.search(r"^Gradle\s+(\S+)", output, flags=re.MULTILINE)
    return match.group(1) if match else None


def check_pi_authentication(
        model: str,
        environment: Mapping[str, str] | None = None,
        pi_command: str = "pi",
        provider: str | None = None,
) -> tuple[bool, str]:
    """Check Pi authentication for the Forge provider and model without invoking a model.

    §FS-forge-host-requirements
    """
    values = os.environ if environment is None else environment
    checked_provider = provider or PI_PROVIDER
    if resolve_executable(pi_command) is None:
        return False, f"`{pi_command}` was not found on PATH; provider={checked_provider}, model={model}"
    result = run_command(
        [
            pi_command, "auth", "check",
            "--provider", checked_provider,
            "--model", model,
            "--json",
        ],
        values,
    )
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError:
        payload = {}
    if not isinstance(payload, dict):
        payload = {}
    ready = (
        result.returncode == 0
        and payload.get("status") == "ready"
        and payload.get("provider") == checked_provider
    )
    detail = (
        f"provider={payload.get('provider') or checked_provider}, model={model}, "
        f"status={payload.get('status') or 'invalid'}"
    )
    failure = "" if ready else first_output_line(result)
    return ready, f"{detail}; {failure}" if failure else detail


def nearest_existing_directory(path: str) -> str:
    """Return the nearest existing directory at or above path."""
    candidate = Path(path).expanduser().absolute()
    while not candidate.exists():
        if candidate.parent == candidate:
            break
        candidate = candidate.parent
    if candidate.is_file():
        candidate = candidate.parent
    return str(candidate)


def probe_write_access(path: str) -> tuple[bool, str]:
    """Prove write access with a temporary file that is removed immediately."""
    expanded = os.path.abspath(os.path.expanduser(path))
    probe_dir = nearest_existing_directory(expanded)
    try:
        with tempfile.NamedTemporaryFile(prefix=".forge-host-", dir=probe_dir, delete=True):
            pass
    except OSError as exc:
        return False, f"target={expanded}, probe={probe_dir}, error={exc}"
    return True, f"target={expanded}, write probe passed in {probe_dir}"


def host_bypasses_proxy(environment: Mapping[str, str], host: str) -> bool:
    """Return whether `no_proxy` sends this host straight out instead of through a proxy."""
    no_proxy = environment.get("no_proxy") or environment.get("NO_PROXY") or ""
    target = host.lower()
    for entry in (part.strip().lower().lstrip(".") for part in no_proxy.split(",")):
        if entry == "*":
            return True
        if entry and (target == entry or target.endswith(f".{entry}")):
            return True
    return False


def resolve_https_proxy(environment: Mapping[str, str], host: str) -> tuple[str, int] | None:
    """Return the proxy that must carry HTTPS traffic to a host, or None for a direct route."""
    proxy_url = environment.get("https_proxy") or environment.get("HTTPS_PROXY")
    if not proxy_url or host_bypasses_proxy(environment, host):
        return None
    parsed = urllib.parse.urlparse(proxy_url if "://" in proxy_url else f"http://{proxy_url}")
    if not parsed.hostname:
        return None
    return parsed.hostname, parsed.port or 80


def probe_tcp_host(
        host: str,
        port: int,
        timeout: float = 5.0,
        environment: Mapping[str, str] | None = None,
) -> tuple[bool, str]:
    """Test outbound access the way Forge's tools use it: directly, or through the configured proxy."""
    values = os.environ if environment is None else environment
    proxy = resolve_https_proxy(values, host)
    if proxy is None:
        try:
            with socket.create_connection((host, port), timeout=timeout):
                return True, f"tcp://{host}:{port} reachable"
        except OSError as exc:
            return False, f"tcp://{host}:{port} unreachable: {exc}"
    return probe_proxied_host(host, port, proxy, timeout)


def probe_proxied_host(
        host: str,
        port: int,
        proxy: tuple[str, int],
        timeout: float = 5.0,
) -> tuple[bool, str]:
    """Ask the configured proxy to open a tunnel, proving both hops without transferring data."""
    proxy_host, proxy_port = proxy
    route = f"tcp://{host}:{port} via proxy {proxy_host}:{proxy_port}"
    request = f"CONNECT {host}:{port} HTTP/1.1\r\nHost: {host}:{port}\r\n\r\n".encode()
    try:
        with socket.create_connection((proxy_host, proxy_port), timeout=timeout) as connection:
            connection.sendall(request)
            status_line = connection.recv(512).decode("latin-1", "replace").splitlines()
    except OSError as exc:
        return False, f"{route} unreachable: {exc}"
    status = status_line[0].strip() if status_line else ""
    fields = status.split()
    if len(fields) > 1 and fields[1] == "200":
        return True, f"{route} reachable"
    return False, f"{route} refused: {status or 'proxy closed the connection'}"


def resolve_gradle_state_root(environment: Mapping[str, str]) -> str:
    """Return the operator-configured or default Forge Gradle state root."""
    override = environment.get("FORGE_GRADLE_USER_HOME")
    if override:
        return os.path.abspath(os.path.expanduser(override))
    return os.path.join(tempfile.gettempdir(), "metadata-forge-gradle")


def resolve_pi_state_root(environment: Mapping[str, str]) -> str:
    """Return Pi's writable agent state directory."""
    override = environment.get("PI_CODING_AGENT_DIR")
    if override:
        return os.path.abspath(os.path.expanduser(override))
    return os.path.join(os.path.expanduser(environment.get("HOME", "~")), ".pi", "agent")


def resolve_codex_state_root(environment: Mapping[str, str]) -> str:
    """Return Codex's writable state directory."""
    override = environment.get("CODEX_HOME")
    if override:
        return os.path.abspath(os.path.expanduser(override))
    return os.path.join(os.path.expanduser(environment.get("HOME", "~")), ".codex")


def resolve_agent_state_root(backend: str, environment: Mapping[str, str]) -> str:
    """Resolve the writable state root for a selected backend."""
    if backend == "pi":
        return resolve_pi_state_root(environment)
    if backend == "codex":
        return resolve_codex_state_root(environment)
    home = os.path.expanduser(environment.get("HOME", "~"))
    if backend == "claude-code":
        return os.path.join(home, ".claude")
    data_home = environment.get("XDG_DATA_HOME") or os.path.join(home, ".local", "share")
    return os.path.join(os.path.expanduser(data_home), "opencode")


def agent_provider_host(
        backend: str,
        model: str,
        provider: str | None = None,
) -> str:
    """Return the provider transport host implied by a backend/model selection."""
    if backend == "codex":
        return "chatgpt.com"
    if backend == "claude-code":
        return "api.anthropic.com"
    selected_provider = (
        provider or (model.split("/", 1)[0] if "/" in model else "")
    ).lower()
    return {
        "anthropic": "api.anthropic.com",
        "google": "generativelanguage.googleapis.com",
        "openai": "api.openai.com",
        "openai-codex": "chatgpt.com",
        "openrouter": "openrouter.ai",
    }.get(selected_provider, "opencode.ai")


def codex_doctor_provider_status(output: str) -> tuple[bool, str]:
    """Extract the provider reachability result from `codex doctor --json`."""
    try:
        payload = json.loads(output)
        check = payload["checks"]["network.provider_reachability"]
        status = str(check["status"])
        summary = str(check["summary"])
    except (json.JSONDecodeError, KeyError, TypeError):
        return False, "codex doctor did not return provider reachability data"
    return status == "ok", f"status={status}, {summary}"


def codex_unattended_policy_status(codex_home: str) -> tuple[bool, str]:
    """Detect cached enterprise requirements incompatible with Forge Codex exec."""
    bundle_path = os.path.join(codex_home, "cloud-config-bundle-cache.json")
    if not os.path.isfile(bundle_path):
        return True, "no cached enterprise requirements conflict was found"
    try:
        with open(bundle_path, "rb") as bundle_file:
            bundle = json.load(bundle_file)
        requirement_entries = bundle.get("requirements_toml", {}).get("enterprise_managed", [])
    except (OSError, json.JSONDecodeError, AttributeError):
        return False, f"could not read managed Codex requirements from {bundle_path}"

    conflicts: list[str] = []
    policies: list[str] = []
    for entry in requirement_entries:
        if not isinstance(entry, dict) or not isinstance(entry.get("contents"), str):
            continue
        name = str(entry.get("name") or entry.get("id") or "enterprise policy")
        try:
            requirements = tomllib.loads(entry["contents"])
        except tomllib.TOMLDecodeError:
            conflicts.append(f"{name}: requirements TOML is invalid")
            continue
        approvals = requirements.get("allowed_approval_policies")
        sandboxes = requirements.get("allowed_sandbox_modes")
        policies.append(name)
        if isinstance(approvals, list) and "never" not in approvals:
            conflicts.append(f"{name}: approval policy `never` is disallowed ({approvals})")
        if isinstance(sandboxes, list) and "workspace-write" not in sandboxes:
            conflicts.append(f"{name}: sandbox `workspace-write` is disallowed ({sandboxes})")

    if conflicts:
        return False, "; ".join(conflicts)
    if policies:
        return True, f"managed policies compatible with unattended recovery: {', '.join(policies)}"
    return True, "no cached enterprise requirements conflict was found"
