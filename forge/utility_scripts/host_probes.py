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
import ssl
import subprocess
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Mapping, NamedTuple, Sequence

from git_scripts.github_cli import (
    is_github_rate_limit_errors,
    is_github_rate_limit_text,
    is_github_transient_errors,
    is_github_transient_failure_text,
)

PI_PROVIDER = "openai-codex"
GITHUB_RATE_LIMIT_FIX: str = (
    "Wait for the active gh account's rate limit to reset (`gh api rate_limit` shows when); "
    "the Forge launcher checks again on its own."
)


class ProbeResult(NamedTuple):
    """A reachability probe; `transient` marks a failure that clears on its own.

    No answer, a server error, or a rate-limit status is transient; an endpoint
    that answers and denies the request is a host fault (§FS-forge-host-requirements.1).
    """

    passed: bool
    detail: str
    transient: bool = False


def is_transient_http_status(status: int) -> bool:
    """Return whether an HTTP status reports an overloaded or rate-limiting endpoint."""
    return status == 429 or status >= 500


def is_certificate_failure(error: object) -> bool:
    """Return whether a TLS failure concerns a certificate the host does not trust."""
    if isinstance(error, ssl.SSLCertVerificationError):
        return True
    reason: str = f"{getattr(error, 'reason', '')} {error}" if isinstance(error, ssl.SSLError) else ""
    return "CERTIFICATE" in reason or "UNKNOWN_CA" in reason


def is_transient_connection_error(error: OSError) -> bool:
    """Return whether a request that got no HTTP answer failed on something that clears on its own.

    A proxy that refuses the tunnel answered, so its status is judged like any answer, and an
    untrusted certificate is the host's setup; anything else reached nothing
    (§FS-forge-host-requirements.1).
    """
    reason: object = error.reason if isinstance(error, urllib.error.URLError) else error
    if is_certificate_failure(reason):
        return False
    tunnel: re.Match[str] | None = re.search(r"Tunnel connection failed: (\d{3})", str(reason))
    return is_transient_http_status(int(tunnel.group(1))) if tunnel else True


def resolve_executable(command: str) -> str | None:
    """Resolve an executable command or absolute path."""
    if os.path.sep in command:
        return command if os.path.isfile(command) and os.access(command, os.X_OK) else None
    return shutil.which(command)


def probe_http_200(url: str, timeout: float = 20.0) -> ProbeResult:
    """Return whether an HTTP HEAD request to a base URL returns exactly 200.

    §FS-forge-host-requirements
    """
    probe_url = f"{url.rstrip('/')}/"
    request = urllib.request.Request(probe_url, method="HEAD")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.getcode()
    except urllib.error.HTTPError as exc:
        return ProbeResult(False, f"HEAD {probe_url} returned HTTP {exc.code}", is_transient_http_status(exc.code))
    except (OSError, urllib.error.URLError) as exc:
        return ProbeResult(False, f"HEAD {probe_url} failed: {exc}", is_transient_connection_error(exc))
    return ProbeResult(status == 200, f"HEAD {probe_url} returned HTTP {status}")


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


def graphql_errors(output: str) -> object:
    """Return the `errors` member of a GraphQL response, or None when there is none."""
    try:
        payload: object = json.loads(output)
    except json.JSONDecodeError:
        return None
    return payload.get("errors") if isinstance(payload, dict) else None


def is_github_rate_limited(result: subprocess.CompletedProcess[str]) -> bool:
    """Return whether a failed `gh` call hit a GitHub rate limit, by the shared GitHub CLI rule."""
    output: str = f"{result.stderr}\n{result.stdout}"
    return result.returncode != 0 and (
        is_github_rate_limit_text(output) or is_github_rate_limit_errors(graphql_errors(result.stdout))
    )


def is_command_timeout(result: subprocess.CompletedProcess[str]) -> bool:
    """Return whether a probe command gave up waiting: `run_command`'s limit, or gh's own login timeout."""
    output: str = f"{result.stderr}\n{result.stdout}"
    return result.returncode != 0 and ("timed out after" in output or "Timeout trying to log in" in output)


def is_github_transient(result: subprocess.CompletedProcess[str]) -> bool:
    """Return whether a failed `gh` or git call met a rate limit, timeout, server or network error.

    §FS-forge-host-requirements.1
    """
    output: str = f"{result.stderr}\n{result.stdout}"
    return result.returncode != 0 and (
        is_github_rate_limited(result)
        or is_command_timeout(result)
        or is_github_transient_failure_text(output)
        or is_github_transient_errors(graphql_errors(result.stdout))
    )


def github_http_status(result: subprocess.CompletedProcess[str]) -> int | None:
    """Return the HTTP status a `gh api --include` call was answered with, or None when none came."""
    match: re.Match[str] | None = (
        re.search(r"^HTTP/\S+ (\d{3})\b", result.stdout, flags=re.MULTILINE)
        or re.search(r"\(HTTP (\d{3})\)", result.stderr)
    )
    if match is not None:
        return int(match.group(1))
    return 200 if result.returncode == 0 else None


def parse_grype_version(output: str) -> str | None:
    """Extract the release from `grype version`."""
    match = re.search(r"^Version:\s*v?(\S+)", output, flags=re.MULTILINE | re.IGNORECASE)
    return match.group(1) if match else None


def parse_rhei_version(output: str) -> str | None:
    """Extract the release from `rhei --version`, keeping a `-dev` suffix so a source build never matches a pin."""
    match = re.search(r"^rhei\s+v?(\S+)", output, flags=re.MULTILINE)
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


TCP_PROBE_ATTEMPTS: int = 2


def probe_tcp_host(
        host: str,
        port: int,
        timeout: float = 5.0,
        environment: Mapping[str, str] | None = None,
) -> ProbeResult:
    """Test outbound access the way Forge's tools use it: directly, or through the configured proxy.

    A failed direct connect is tried once more, since one lost connect does not
    make a host unreachable.
    """
    values = os.environ if environment is None else environment
    proxy = resolve_https_proxy(values, host)
    if proxy is not None:
        return probe_proxied_host(host, port, proxy, timeout)
    error: OSError | None = None
    for _ in range(TCP_PROBE_ATTEMPTS):
        try:
            with socket.create_connection((host, port), timeout=timeout):
                return ProbeResult(True, f"tcp://{host}:{port} reachable")
        except OSError as exc:
            error = exc
    return ProbeResult(False, f"tcp://{host}:{port} unreachable: {error}", True)


def probe_proxied_host(
        host: str,
        port: int,
        proxy: tuple[str, int],
        timeout: float = 5.0,
) -> ProbeResult:
    """Ask the configured proxy to open a tunnel, proving both hops without transferring data."""
    proxy_host, proxy_port = proxy
    route = f"tcp://{host}:{port} via proxy {proxy_host}:{proxy_port}"
    request = f"CONNECT {host}:{port} HTTP/1.1\r\nHost: {host}:{port}\r\n\r\n".encode()
    try:
        with socket.create_connection((proxy_host, proxy_port), timeout=timeout) as connection:
            connection.sendall(request)
            status_line = connection.recv(512).decode("latin-1", "replace").splitlines()
    except OSError as exc:
        return ProbeResult(False, f"{route} unreachable: {exc}", True)
    status = status_line[0].strip() if status_line else ""
    fields = status.split()
    if len(fields) > 1 and fields[1] == "200":
        return ProbeResult(True, f"{route} reachable")
    # A proxy that closes the tunnel or reports a server error could not reach the host.
    code: int = int(fields[1]) if len(fields) > 1 and fields[1].isdigit() else 503
    return ProbeResult(False, f"{route} refused: {status or 'proxy closed the connection'}",
                       is_transient_http_status(code))


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


def resolve_agent_state_root(backend: str, environment: Mapping[str, str]) -> str:
    """Resolve the writable state root for a selected backend."""
    if backend == "pi":
        return resolve_pi_state_root(environment)
    return os.path.join(os.path.expanduser(environment.get("HOME", "~")), ".claude")


def agent_provider_host(
        backend: str,
        model: str,
        provider: str | None = None,
) -> str:
    """Return the provider transport host implied by a backend/model selection."""
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
    }.get(selected_provider, "chatgpt.com")
