# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from __future__ import annotations

from dataclasses import dataclass
import os
import re
import shutil
import signal
import subprocess
import sys
import time

from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import build_timestamped_task_log_path, display_log_path


DEFAULT_GRADLE_TEST_TIMEOUT_SECONDS = 30 * 60
DEFAULT_THREAD_DUMP_PROMPT_CHARS = 40_000
_TIMEOUT_ENV_VAR = "FORGE_GRADLE_TEST_TIMEOUT_SECONDS"
_THREAD_DUMP_TIMEOUT_SECONDS = 15


@dataclass(frozen=True)
class _ProcessInfo:
    pid: int
    ppid: int
    command: str
    args: str


def run_gradle_test_command(
        test_cmd: str,
        working_dir: str,
        library: str | None = None,
        env: dict[str, str] | None = None,
        timeout_seconds: int | None = None,
) -> str:
    """Run a Gradle test command and return output suitable for agent repair prompts."""
    resolved_library = library or _extract_coordinates(test_cmd)
    resolved_timeout = timeout_seconds or _timeout_from_environment()
    log_path = build_timestamped_task_log_path("gradle-test", resolved_library, "gradle-test")
    log_stage(
        "gradle-test",
        "$ {command}  (log: {log_path}, timeout: {timeout}s)".format(
            command=test_cmd,
            log_path=display_log_path(log_path),
            timeout=resolved_timeout,
        ),
    )

    start_time = time.monotonic()
    with open(log_path, "w", encoding="utf-8") as log_file:
        process = subprocess.Popen(
            test_cmd,
            cwd=working_dir,
            env=env,
            shell=True,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            text=True,
            start_new_session=True,
        )
        try:
            process.wait(timeout=resolved_timeout)
        except subprocess.TimeoutExpired:
            log_file.flush()
            elapsed_seconds = int(time.monotonic() - start_time)
            thread_dump_path = _write_thread_dump(process.pid, resolved_library)
            _terminate_process_group(process)
            output = _read_text(log_path)
            return _format_timeout_output(
                test_cmd=test_cmd,
                elapsed_seconds=elapsed_seconds,
                timeout_seconds=resolved_timeout,
                gradle_log_path=log_path,
                thread_dump_path=thread_dump_path,
                output=output,
            )

    return _read_text(log_path)


def _timeout_from_environment() -> int:
    value = os.environ.get(_TIMEOUT_ENV_VAR)
    if not value:
        return DEFAULT_GRADLE_TEST_TIMEOUT_SECONDS
    try:
        timeout_seconds = int(value)
    except ValueError:
        print(
            f"ERROR: {_TIMEOUT_ENV_VAR} must be an integer number of seconds.",
            file=sys.stderr,
        )
        return DEFAULT_GRADLE_TEST_TIMEOUT_SECONDS
    if timeout_seconds <= 0:
        print(
            f"ERROR: {_TIMEOUT_ENV_VAR} must be greater than zero.",
            file=sys.stderr,
        )
        return DEFAULT_GRADLE_TEST_TIMEOUT_SECONDS
    return timeout_seconds


def _extract_coordinates(command: str) -> str | None:
    match = re.search(r"-Pcoordinates=(?P<coordinates>[^\s]+)", command)
    if match is None:
        return None
    return match.group("coordinates").strip("'\"")


def _format_timeout_output(
        test_cmd: str,
        elapsed_seconds: int,
        timeout_seconds: int,
        gradle_log_path: str,
        thread_dump_path: str,
        output: str,
) -> str:
    thread_dump = _trim_for_prompt(_read_text(thread_dump_path), DEFAULT_THREAD_DUMP_PROMPT_CHARS)
    diagnostic = """

> Task :test FAILED

ERROR: Gradle test command timed out before completion.
Failure kind: gradle_timeout
Command: {test_cmd}
Elapsed seconds: {elapsed_seconds}
Timeout seconds: {timeout_seconds}
Gradle log: {gradle_log}
Thread dump log: {thread_dump_log}

The command was stopped as a suspected test deadlock or leaked-thread hang. Fix the generated tests so every test
method completes in under 60 seconds. Prefer bounded waits, closing clients/servers/executors, and avoiding
background work that can keep the JVM alive. Do not skip, disable, or trivialize the test.

Thread dump captured before terminating the Gradle process tree:
```text
{thread_dump}
```
""".format(
        test_cmd=test_cmd,
        elapsed_seconds=elapsed_seconds,
        timeout_seconds=timeout_seconds,
        gradle_log=display_log_path(gradle_log_path),
        thread_dump_log=display_log_path(thread_dump_path),
        thread_dump=thread_dump or "<no thread dump captured>",
    )
    if output and not output.endswith("\n"):
        output += "\n"
    return output + diagnostic


def _write_thread_dump(root_pid: int, library: str | None) -> str:
    thread_dump_path = build_timestamped_task_log_path("gradle-test", library, "thread-dump")
    process_table = _load_process_table()
    descendants = _collect_descendants(root_pid, process_table)
    descendant_java_processes = [
        process_info
        for process_info in descendants
        if _is_java_process(process_info)
    ]
    descendant_pids = {process_info.pid for process_info in descendants}
    gradle_java_processes = [
        process_info
        for process_info in process_table.values()
        if process_info.pid not in descendant_pids
        and _is_java_process(process_info)
        and _looks_like_gradle_process(process_info)
    ]
    java_processes = descendant_java_processes + gradle_java_processes
    with open(thread_dump_path, "w", encoding="utf-8") as dump_file:
        dump_file.write(f"Root PID: {root_pid}\n")
        dump_file.write("Process tree:\n")
        for process_info in descendants:
            dump_file.write(
                "  pid={pid} ppid={ppid} command={command} args={args}\n".format(
                    pid=process_info.pid,
                    ppid=process_info.ppid,
                    command=process_info.command,
                    args=process_info.args,
                )
            )
        if gradle_java_processes:
            dump_file.write("\nAdditional Gradle Java processes:\n")
            for process_info in gradle_java_processes:
                dump_file.write(
                    "  pid={pid} ppid={ppid} command={command} args={args}\n".format(
                        pid=process_info.pid,
                        ppid=process_info.ppid,
                        command=process_info.command,
                        args=process_info.args,
                    )
                )
        if not java_processes:
            dump_file.write("\nNo descendant Java processes found.\n")
            return thread_dump_path

        jcmd = _resolve_jcmd()
        if jcmd is None:
            dump_file.write("\nNo jcmd executable found on PATH, JAVA_HOME, or GRAALVM_HOME.\n")
            return thread_dump_path

        for process_info in java_processes:
            dump_file.write(f"\n{'=' * 80}\n")
            dump_file.write(f"jcmd {process_info.pid} Thread.print -l\n")
            dump_file.write(f"{'=' * 80}\n")
            try:
                result = subprocess.run(
                    [jcmd, str(process_info.pid), "Thread.print", "-l"],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    timeout=_THREAD_DUMP_TIMEOUT_SECONDS,
                    check=False,
                )
                dump_file.write(result.stdout or "<no output>\n")
                if result.stdout and not result.stdout.endswith("\n"):
                    dump_file.write("\n")
            except subprocess.TimeoutExpired as exc:
                dump_file.write(exc.stdout or "")
                dump_file.write(f"\njcmd timed out after {_THREAD_DUMP_TIMEOUT_SECONDS}s.\n")
    return thread_dump_path


def _load_process_table() -> dict[int, _ProcessInfo]:
    try:
        result = subprocess.run(
            ["ps", "-eo", "pid=,ppid=,comm=,args="],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            check=False,
        )
    except OSError:
        return {}
    process_table: dict[int, _ProcessInfo] = {}
    for line in result.stdout.splitlines():
        parts = line.strip().split(None, 3)
        if len(parts) < 3:
            continue
        try:
            pid = int(parts[0])
            ppid = int(parts[1])
        except ValueError:
            continue
        process_table[pid] = _ProcessInfo(
            pid=pid,
            ppid=ppid,
            command=parts[2],
            args=parts[3] if len(parts) > 3 else "",
        )
    return process_table


def _collect_descendants(root_pid: int, process_table: dict[int, _ProcessInfo]) -> list[_ProcessInfo]:
    children_by_parent: dict[int, list[_ProcessInfo]] = {}
    for process_info in process_table.values():
        children_by_parent.setdefault(process_info.ppid, []).append(process_info)

    descendants: list[_ProcessInfo] = []
    pending = list(children_by_parent.get(root_pid, []))
    while pending:
        process_info = pending.pop(0)
        descendants.append(process_info)
        pending.extend(children_by_parent.get(process_info.pid, []))
    root_info = process_table.get(root_pid)
    if root_info is not None:
        descendants.insert(0, root_info)
    return descendants


def _is_java_process(process_info: _ProcessInfo) -> bool:
    command = os.path.basename(process_info.command)
    first_arg = os.path.basename(process_info.args.split(maxsplit=1)[0]) if process_info.args else ""
    return command == "java" or first_arg == "java"


def _looks_like_gradle_process(process_info: _ProcessInfo) -> bool:
    return any(
        marker in process_info.args
        for marker in (
            "GradleDaemon",
            "GradleWorkerMain",
            "gradle-launcher",
            "gradle-worker",
        )
    )


def _resolve_jcmd() -> str | None:
    for env_var in ("JAVA_HOME", "GRAALVM_HOME"):
        java_home = os.environ.get(env_var)
        if not java_home:
            continue
        candidate = os.path.join(java_home, "bin", "jcmd")
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    return shutil.which("jcmd")


def _terminate_process_group(process: subprocess.Popen) -> None:
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=10)
        return
    except subprocess.TimeoutExpired:
        pass
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        pass


def _read_text(path: str) -> str:
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as file:
            return file.read()
    except OSError:
        return ""


def _trim_for_prompt(text: str, max_chars: int) -> str:
    if len(text) <= max_chars:
        return text
    head_chars = max_chars // 2
    tail_chars = max_chars - head_chars
    return (
        text[:head_chars]
        + "\n\n... <thread dump trimmed for prompt> ...\n\n"
        + text[-tail_chars:]
    )
