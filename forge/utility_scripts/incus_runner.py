# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Run a whole Forge generation inside a fresh, single-use Incus VM.

This module is the only Incus-aware surface in Forge. The opt-in `--incus` flag
on `forge_metadata.py` routes each claimed run through `run_issue_in_vm`, which
preflights the host, launches a fresh VM from the cached base image, mounts a
per-run host log directory, seeds GitHub credentials, refreshes the baked
checkout to `master`, runs `forge_metadata.py --issue-number N` inside the VM,
and destroys the VM afterwards. Workflow drivers, engines, and agents never see
Incus; they run the same code inside the VM as on the host.
§AR-forge-vm-runner-boundary §FS-forge-vm-isolated-execution
"""

from __future__ import annotations

import os
import shlex
import shutil
import subprocess
import sys
import time
import uuid
from contextlib import contextmanager
from typing import Iterator, Sequence

from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import FORGE_LOGS_DIR_ENV, resolve_logs_root

# Name of the cached base image the per-run VMs are launched from. Built once by
# the operator (see forge/README.md) and never modified by Forge.
FORGE_INCUS_IMAGE_ENV = "FORGE_INCUS_IMAGE"
DEFAULT_INCUS_IMAGE = "forge-base"

# Optional Incus profile applied at launch (Docker nesting, resource limits).
FORGE_INCUS_PROFILE_ENV = "FORGE_INCUS_PROFILE"
DEFAULT_INCUS_PROFILE = "forge"

# Path of the baked reachability checkout inside the VM. The per-issue run is
# invoked from `<repo>/forge`.
FORGE_INCUS_REPO_PATH_ENV = "FORGE_INCUS_REPO_PATH"
DEFAULT_VM_REPO_PATH = "/root/graalvm-reachability-metadata"

# Host directory that holds each run's mounted log directory. Per-run logs land
# in `<root>/issue-<number>` and survive VM teardown.
FORGE_INCUS_LOGS_ROOT_ENV = "FORGE_INCUS_LOGS_ROOT"

# Clean top-level mount target inside the VM, pointed at by FORGE_LOGS_DIR so the
# run writes its durable logs straight onto the host.
VM_LOGS_MOUNT_PATH = "/forge-logs"
LOGS_DEVICE_NAME = "forge-logs"

# Seconds to wait for the VM guest agent to accept commands after launch.
FORGE_INCUS_LAUNCH_TIMEOUT_ENV = "FORGE_INCUS_LAUNCH_TIMEOUT"
DEFAULT_LAUNCH_TIMEOUT_SECONDS = 300

# Minimal codex/pi config seeded into each VM at launch. Generation drives the
# codex and pi CLIs, which authenticate against an internal gateway using small
# config files (a few KB); the rest of ~/.codex and ~/.pi (sessions, sqlite
# state, logs — gigabytes) is never needed. Seeding these at runtime keeps the
# secrets out of the published base image (§FS-forge-vm-isolated-execution).
FORGE_INCUS_CODEX_DIR_ENV = "FORGE_INCUS_CODEX_DIR"
FORGE_INCUS_PI_DIR_ENV = "FORGE_INCUS_PI_DIR"
DEFAULT_CODEX_DIR = "~/.codex"
DEFAULT_PI_DIR = "~/.pi"
VM_CODEX_DIR = "/root/.codex"
VM_PI_AGENT_DIR = "/root/.pi/agent"

# Environment variables forwarded into the VM run. The behavior-shaping FORGE_*
# settings are forwarded dynamically; these are the credentials and fixed names.
FORWARDED_TOKEN_ENV_NAMES = ("GH_TOKEN", "GITHUB_TOKEN")
# Incus-runner-only settings that must not leak into the inner host-style run.
_INCUS_ONLY_ENV_NAMES = frozenset(
    {
        FORGE_INCUS_IMAGE_ENV,
        FORGE_INCUS_PROFILE_ENV,
        FORGE_INCUS_REPO_PATH_ENV,
        FORGE_INCUS_LOGS_ROOT_ENV,
        FORGE_INCUS_LAUNCH_TIMEOUT_ENV,
        FORGE_LOGS_DIR_ENV,
    }
)


class IncusError(RuntimeError):
    """A recoverable failure while driving Incus for a run."""


class IncusPreflightError(IncusError):
    """The host is not ready to run with `--incus`; setup is incomplete.

    Raised with an actionable message instead of installing anything or falling
    back to the host (§FS-forge-vm-isolated-execution).
    """


def incus_image_name() -> str:
    """Return the configured base image alias."""
    return os.environ.get(FORGE_INCUS_IMAGE_ENV) or DEFAULT_INCUS_IMAGE


def incus_profile_name() -> str:
    """Return the configured Incus profile, or empty when none is set."""
    return os.environ.get(FORGE_INCUS_PROFILE_ENV, DEFAULT_INCUS_PROFILE)


def vm_repo_path() -> str:
    """Return the baked reachability checkout path inside the VM."""
    return os.environ.get(FORGE_INCUS_REPO_PATH_ENV) or DEFAULT_VM_REPO_PATH


def host_logs_root() -> str:
    """Return the host directory that holds per-run mounted log directories."""
    override = os.environ.get(FORGE_INCUS_LOGS_ROOT_ENV)
    if override:
        return os.path.abspath(os.path.expanduser(override))
    return resolve_logs_root()


def _codex_host_dir() -> str:
    """Return the host codex config dir whose key/provider files are seeded."""
    return os.path.expanduser(os.environ.get(FORGE_INCUS_CODEX_DIR_ENV) or DEFAULT_CODEX_DIR)


def _pi_host_dir() -> str:
    """Return the host pi config dir whose key/provider files are seeded."""
    return os.path.expanduser(os.environ.get(FORGE_INCUS_PI_DIR_ENV) or DEFAULT_PI_DIR)


def agent_config_files() -> list[tuple[str, str]]:
    """Return `(host_path, vm_path)` pairs for the minimal codex/pi config.

    Only the small auth/provider files are seeded — never the gigabytes of
    session and sqlite state under the agent dirs (§FS-forge-vm-isolated-execution).

    `.oca-key` holds the OCA gateway JWT that pi's `models.json` resolves at
    runtime via `"apiKey": "!cat $HOME/.pi/agent/.oca-key"`. Without it the VM's
    pi reads an empty key and every model call returns nothing (0 tokens), so it
    must be seeded to the exact path `models.json` shells out to.
    """
    codex = _codex_host_dir()
    pi_agent = os.path.join(_pi_host_dir(), "agent")
    return [
        (os.path.join(codex, "auth.json"), f"{VM_CODEX_DIR}/auth.json"),
        (os.path.join(codex, "config.toml"), f"{VM_CODEX_DIR}/config.toml"),
        (os.path.join(pi_agent, "models.json"), f"{VM_PI_AGENT_DIR}/models.json"),
        (os.path.join(pi_agent, "settings.json"), f"{VM_PI_AGENT_DIR}/settings.json"),
        (os.path.join(pi_agent, ".oca-key"), f"{VM_PI_AGENT_DIR}/.oca-key"),
    ]


def launch_timeout_seconds() -> int:
    """Return how long to wait for the VM guest agent after launch."""
    raw_value = os.environ.get(FORGE_INCUS_LAUNCH_TIMEOUT_ENV)
    if not raw_value:
        return DEFAULT_LAUNCH_TIMEOUT_SECONDS
    try:
        parsed = int(raw_value)
    except ValueError as exc:
        raise IncusError(
            f"{FORGE_INCUS_LAUNCH_TIMEOUT_ENV} must be an integer number of seconds, got {raw_value!r}"
        ) from exc
    if parsed <= 0:
        raise IncusError(f"{FORGE_INCUS_LAUNCH_TIMEOUT_ENV} must be a positive integer, got {raw_value!r}")
    return parsed


def _run_incus(
        args: Sequence[str],
        *,
        capture: bool = False,
        check: bool = True,
        input_text: str | None = None,
) -> subprocess.CompletedProcess:
    """Run an `incus` subcommand, raising `IncusError` on unexpected failure."""
    command = ["incus", *args]
    try:
        return subprocess.run(
            command,
            check=check,
            text=True,
            input=input_text,
            capture_output=capture,
        )
    except FileNotFoundError as exc:
        raise IncusPreflightError(
            "The `incus` command was not found on PATH. Install and initialize Incus on the host "
            "before using --incus; see forge/README.md (Isolated execution with Incus)."
        ) from exc
    except subprocess.CalledProcessError as exc:
        detail = (exc.stderr or exc.stdout or "").strip()
        suffix = f": {detail}" if detail else ""
        raise IncusError(f"`{' '.join(command)}` failed with exit code {exc.returncode}{suffix}") from exc


def _resolve_github_token() -> str:
    """Return a GitHub token to seed into the VM, or raise if none is available."""
    for env_name in FORWARDED_TOKEN_ENV_NAMES:
        token = os.environ.get(env_name)
        if token:
            return token.strip()
    try:
        result = subprocess.run(
            ["gh", "auth", "token"],
            check=True,
            text=True,
            capture_output=True,
        )
    except FileNotFoundError as exc:
        raise IncusPreflightError(
            "Neither GH_TOKEN/GITHUB_TOKEN nor the `gh` CLI is available to obtain a GitHub token. "
            "Authenticate `gh` on the host (`gh auth login`) before using --incus."
        ) from exc
    except subprocess.CalledProcessError as exc:
        raise IncusPreflightError(
            "Could not read a GitHub token from `gh auth token`. Authenticate `gh` on the host "
            "(`gh auth login`) before using --incus."
        ) from exc
    token = result.stdout.strip()
    if not token:
        raise IncusPreflightError(
            "`gh auth token` returned no token. Authenticate `gh` on the host (`gh auth login`) "
            "before using --incus."
        )
    return token


def _incus_daemon_reachable() -> bool:
    """Return whether the local Incus daemon is reachable for this user."""
    result = subprocess.run(
        ["incus", "info"],
        check=False,
        text=True,
        capture_output=True,
    )
    return result.returncode == 0


def _image_exists(image: str) -> bool:
    """Return whether an image with the given alias is in the local store."""
    result = subprocess.run(
        ["incus", "image", "info", image],
        check=False,
        text=True,
        capture_output=True,
    )
    return result.returncode == 0


def preflight() -> None:
    """Verify the host is ready to run with `--incus`, or raise `IncusPreflightError`.

    Checks Incus availability, the base image, and GitHub credentials; never
    installs or configures anything (§FS-forge-vm-isolated-execution, step 1 of
    §AR-forge-vm-runner-boundary).
    """
    if shutil.which("incus") is None:
        raise IncusPreflightError(
            "The `incus` command was not found on PATH. Install and initialize Incus on the host "
            "before using --incus; see forge/README.md (Isolated execution with Incus)."
        )
    if not _incus_daemon_reachable():
        raise IncusPreflightError(
            "The Incus daemon is not reachable (`incus info` failed). Initialize Incus "
            "(`incus admin init --minimal`) and ensure your user is in the `incus-admin` group; "
            "see forge/README.md (Isolated execution with Incus)."
        )
    image = incus_image_name()
    if not _image_exists(image):
        raise IncusPreflightError(
            f"The base image '{image}' is not in the local Incus image store. Build it once with the "
            "steps in forge/README.md (Build the base image), or set FORGE_INCUS_IMAGE to an existing "
            "image alias."
        )
    # Surfaces a missing-credentials failure now rather than mid-run.
    _resolve_github_token()


def _forwarded_run_environment() -> dict[str, str]:
    """Return the behavior-shaping environment forwarded into the inner run.

    Forwards the operator's `FORGE_*` settings unchanged so workflow selection,
    strategy configuration, and limits behave the same inside the VM, but drops
    the Incus-runner-only settings and forces logs onto the mounted host
    directory (§FS-forge-vm-isolated-execution, "Behavior unchanged").
    """
    forwarded: dict[str, str] = {
        name: value
        for name, value in os.environ.items()
        if name.startswith("FORGE_") and name not in _INCUS_ONLY_ENV_NAMES
    }
    forwarded[FORGE_LOGS_DIR_ENV] = VM_LOGS_MOUNT_PATH
    return forwarded


def build_inner_forge_command(
        issue_number: int,
        *,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
) -> list[str]:
    """Build the host-style `forge_metadata.py` invocation that runs inside the VM.

    The inner run never carries `--incus`; it is the ordinary per-issue run
    (§AR-forge-vm-runner-boundary, step 5).
    """
    command = ["python3", "forge_metadata.py", "--issue-number", str(issue_number)]
    if strategy_name:
        command += ["--strategy-name", strategy_name]
    if keep_tests_without_dynamic_access:
        command.append("--keep-tests-without-dynamic-access")
    return command


def build_incus_exec_command(
        vm_name: str,
        inner_command: Sequence[str],
        forwarded_env: dict[str, str],
) -> list[str]:
    """Build the `incus exec` command that runs the inner run inside the VM.

    `incus exec` does not go through PAM, so the GraalVM homes and PATH baked
    into the VM's `/etc/environment` are not loaded for the run (neither a plain
    nor a login shell sources that file). Wrap the inner command in a shell that
    sources `/etc/environment` first, then applies the forwarded `FORGE_*`
    settings so they win over anything `forge.env` baked in — notably the
    host-mounted `FORGE_LOGS_DIR`. §FS-forge-vm-isolated-execution
    """
    assignments = "".join(
        f"{name}={shlex.quote(forwarded_env[name])}\n" for name in sorted(forwarded_env)
    )
    script = (
        "set -a\n"
        ". /etc/environment 2>/dev/null || true\n"
        f"{assignments}"
        "set +a\n"
        'exec "$@"\n'
    )
    return [
        "exec",
        vm_name,
        "--cwd",
        f"{vm_repo_path()}/forge",
        "--",
        "bash",
        "-c",
        script,
        "forge-inner",
        *inner_command,
    ]


def _wait_for_guest_agent(vm_name: str, timeout_seconds: int) -> None:
    """Block until the VM accepts commands, or raise `IncusError` on timeout."""
    deadline = time.monotonic() + timeout_seconds
    while True:
        probe = subprocess.run(
            ["incus", "exec", vm_name, "--", "true"],
            check=False,
            text=True,
            capture_output=True,
        )
        if probe.returncode == 0:
            return
        if time.monotonic() >= deadline:
            raise IncusError(
                f"VM '{vm_name}' did not become ready within {timeout_seconds}s "
                "(guest agent unreachable)."
            )
        time.sleep(2)


@contextmanager
def _launched_vm(vm_name: str) -> Iterator[None]:
    """Launch a fresh VM from the base image and always destroy it afterwards."""
    launch_args = ["launch", incus_image_name(), vm_name, "--vm"]
    profile = incus_profile_name()
    if profile:
        launch_args += ["--profile", profile]
    log_stage("incus", f"Launching fresh VM '{vm_name}' from image '{incus_image_name()}'")
    _run_incus(launch_args, capture=True)
    try:
        _wait_for_guest_agent(vm_name, launch_timeout_seconds())
        yield
    finally:
        log_stage("incus", f"Destroying VM '{vm_name}'")
        _run_incus(["delete", vm_name, "--force"], capture=True, check=False)


def _mount_host_logs(vm_name: str, host_log_dir: str) -> None:
    """Mount the per-run host log directory into the VM at the logs mount path."""
    os.makedirs(host_log_dir, exist_ok=True)
    _run_incus(
        [
            "config",
            "device",
            "add",
            vm_name,
            LOGS_DEVICE_NAME,
            "disk",
            f"source={host_log_dir}",
            f"path={VM_LOGS_MOUNT_PATH}",
        ],
        capture=True,
    )


def _seed_credentials(vm_name: str, github_token: str) -> None:
    """Inject GitHub credentials and author identity into the VM.

    The forge code is already baked into the image (a shallow copy of the
    operator's local repository), so there is no checkout to refresh here
    (§FS-forge-vm-isolated-execution).
    """
    log_stage("incus", f"Seeding credentials in '{vm_name}'")
    _run_incus(
        ["exec", vm_name, "--", "gh", "auth", "login", "--with-token"],
        input_text=github_token + "\n",
        capture=True,
    )
    _run_incus(
        ["exec", vm_name, "--", "gh", "auth", "setup-git"],
        capture=True,
    )
    _seed_git_identity(vm_name)


def _host_git_config(key: str) -> str | None:
    """Return the host's git config value for `key`, or None if unset."""
    try:
        result = subprocess.run(
            ["git", "config", "--get", key],
            check=False,
            text=True,
            capture_output=True,
        )
    except FileNotFoundError:
        return None
    value = (result.stdout or "").strip()
    return value or None


def _seed_git_identity(vm_name: str) -> None:
    """Mirror the host git author identity into the VM.

    `incus exec` runs as a bare `root@<vm>` with no identity, so preserve-work
    and generation commits fail with "Author identity unknown". Copy the host's
    `user.name`/`user.email` so commits made in the VM are attributed normally.
    """
    for key in ("user.name", "user.email"):
        value = _host_git_config(key)
        if value:
            _run_incus(
                ["exec", vm_name, "--", "git", "config", "--global", key, value],
                capture=True,
            )


def _seed_agent_config(vm_name: str) -> None:
    """Seed the minimal codex/pi auth + provider config into the VM.

    Pushes only the small key/provider files (a few KB total), never the
    gigabytes of session state, so secrets stay out of the published image
    (§FS-forge-vm-isolated-execution). Files absent on the host are skipped; if
    none are found the run continues but agent strategies will fail to
    authenticate, so warn.
    """
    seeded = 0
    for host_path, vm_path in agent_config_files():
        if not os.path.isfile(host_path):
            continue
        _run_incus(
            ["exec", vm_name, "--", "mkdir", "-p", os.path.dirname(vm_path)],
            capture=True,
        )
        _run_incus(
            ["file", "push", host_path, f"{vm_name}{vm_path}", "--mode=0600"],
            capture=True,
        )
        seeded += 1
    if seeded == 0:
        print(
            "WARNING: no codex/pi config found on the host (looked under "
            f"{_codex_host_dir()} and {_pi_host_dir()}); agent strategies may fail to authenticate.",
            file=sys.stderr,
        )


def run_issue_in_vm(
        issue_number: int,
        *,
        strategy_name: str | None = None,
        keep_tests_without_dynamic_access: bool = False,
) -> bool:
    """Run one issue's generation inside a fresh, single-use Incus VM.

    Implements the per-run sequence of §AR-forge-vm-runner-boundary: preflight,
    launch, mount logs, seed credentials, run the per-issue workflow, then
    destroy the VM. Branches, PRs, and metrics leave the VM over the network;
    only logs cross through the mounted host directory. Returns whether the
    inner run reported success.
    """
    preflight()
    github_token = _resolve_github_token()

    vm_name = f"forge-run-{issue_number}-{uuid.uuid4().hex[:8]}"
    host_log_dir = os.path.join(host_logs_root(), f"issue-{issue_number}")
    inner_command = build_inner_forge_command(
        issue_number,
        strategy_name=strategy_name,
        keep_tests_without_dynamic_access=keep_tests_without_dynamic_access,
    )

    with _launched_vm(vm_name):
        _mount_host_logs(vm_name, host_log_dir)
        _seed_credentials(vm_name, github_token)
        _seed_agent_config(vm_name)
        exec_command = build_incus_exec_command(
            vm_name,
            inner_command,
            _forwarded_run_environment(),
        )
        log_stage(
            "incus",
            f"Running issue #{issue_number} in VM '{vm_name}'; logs on host at {host_log_dir}",
        )
        result = subprocess.run(["incus", *exec_command], check=False)
    succeeded = result.returncode == 0
    if not succeeded:
        print(
            f"ERROR: Forge run for issue #{issue_number} inside VM exited with code {result.returncode}; "
            f"see logs on the host at {host_log_dir}.",
            file=sys.stderr,
        )
    return succeeded
