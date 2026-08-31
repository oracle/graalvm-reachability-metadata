# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from abc import ABC, abstractmethod
from contextlib import contextmanager
import os
import shutil
import sys
import tempfile
import threading
import time
from collections.abc import Iterator

from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import display_log_path, resolve_task_log_dir


class AgentFailureError(RuntimeError):
    """Agent failure carrying the durable log needed by terminal output.

    §FS-forge-run-output-legibility.2
    """

    def __init__(self, message: str, log_path: str | None) -> None:
        self.log_path = log_path
        super().__init__(message)


class AgentTimeoutError(AgentFailureError):
    """Agent timeout carrying the action and durable log needed by failure output."""

    def __init__(self, action: str, timeout_seconds: int, log_path: str | None) -> None:
        self.action = action
        self.timeout_seconds = timeout_seconds
        super().__init__(
            f"Agent {action} timed out after {_format_elapsed(timeout_seconds)}",
            log_path,
        )


class Agent(ABC):
    """Base class for AI agents used by workflow strategies.

    Subclasses represent different AI coding agent backends (e.g. Codex, Claude)
    and must implement the abstract methods that handle prompt exchange, context
    management, and test execution.

    Agents are discovered via a class-level registry. Use the ``@Agent.register``
    decorator to make a concrete implementation available by name::

        @Agent.register("my-agent")
        class MyAgent(Agent):
            ...

    Attributes:
        _registry: Maps agent key strings to their implementing classes.
    """

    _registry: dict[str, type["Agent"]] = {}

    @classmethod
    def register(cls, agent_key: str):
        """Class decorator that registers an agent implementation under the given key."""

        def decorator(subclass):
            if agent_key in cls._registry:
                raise ValueError(f"Duplicate agent key: {agent_key}")
            cls._registry[agent_key] = subclass
            return subclass

        return decorator

    @classmethod
    def get_class(cls, agent_name: str) -> type["Agent"]:
        """Resolve an agent implementation by its registered name."""
        resolved = cls._registry.get(agent_name)
        if resolved is None:
            available = ", ".join(sorted(cls._registry))
            raise ValueError(f"Unknown agent '{agent_name}'. Available: {available}")
        return resolved

    @property
    @abstractmethod
    def total_tokens_sent(self) -> int:
        """Return the cumulative number of input tokens sent to the agent."""

    @property
    @abstractmethod
    def total_tokens_received(self) -> int:
        """Return the cumulative number of output tokens received from the agent."""

    @property
    def cached_input_tokens_used(self) -> int | None:
        """Return cumulative cached input tokens when the backend reports them."""
        return None

    def _create_session_log_path(
            self,
            agent_name: str,
            task_type: str = "session",
            library: str | None = None,
    ) -> str:
        """Create a stable per-session log path under the task log directory.

        Durable per-session logs are required by §FS-durable-generation-logs.
        """
        logs_dir = resolve_task_log_dir(task_type, library)
        file_descriptor, log_path = tempfile.mkstemp(
            prefix=f"{agent_name}-session-",
            suffix=".log",
            dir=logs_dir,
            text=True,
        )
        os.close(file_descriptor)
        return log_path

    def _print_session_log_once(self, agent_name: str, log_path: str) -> None:
        """Print the session log location once per agent instance."""
        if getattr(self, "_session_log_announced", False):
            return
        log_stage("agent", f"{agent_name} session log: {display_log_path(log_path)}")
        self._session_log_announced = True

    def _print_live_status(self, agent_name: str, detail: str) -> None:
        """Render one quiet elapsed-time heartbeat for an active agent turn."""
        del detail
        if not self._live_status_enabled():
            return
        action = self._current_agent_action()
        started_at = float(getattr(self, "_agent_activity_started_at", time.monotonic()))
        elapsed_seconds = int(time.monotonic() - started_at)
        if elapsed_seconds == getattr(self, "_last_live_status_second", None):
            return
        self._last_live_status_second = elapsed_seconds
        terminal_width = shutil.get_terminal_size(fallback=(120, 20)).columns
        max_message_length = max(terminal_width - 1, 20)
        message = f"[agent] Running {action} — {_format_elapsed(elapsed_seconds)}"
        if len(message) > max_message_length:
            message = f"{message[:max_message_length - 3]}..."
        previous_length = int(getattr(self, "_live_status_length", 0) or 0)
        padding = " " * max(previous_length - len(message), 0)
        print(f"\r{message}{padding}", end="", flush=True)
        self._live_status_length = len(message)

    def _clear_live_status(self) -> None:
        """Clear the current live status line."""
        previous_length = int(getattr(self, "_live_status_length", 0) or 0)
        if previous_length <= 0:
            return
        print(f"\r{' ' * previous_length}\r", end="", flush=True)
        self._live_status_length = 0
        self._last_live_status_second = None

    def send_prompt_for_action(self, prompt: str, action: str) -> str:
        """Send one prompt with the workflow action shown in live progress."""
        previous_action = getattr(self, "_pending_agent_action", None)
        self._pending_agent_action = action
        try:
            return self.send_prompt(prompt)
        finally:
            self._pending_agent_action = previous_action

    @contextmanager
    def _agent_activity(self, agent_name: str) -> Iterator[None]:
        """Announce one agent turn and collapse its live output into a heartbeat.

        Full prompts and responses remain in the durable session log while the
        terminal shows only start, elapsed time, and outcome.
        §FS-forge-run-output-legibility.3 §FS-durable-generation-logs
        """
        action = self._current_agent_action()
        self._agent_activity_started_at = time.monotonic()
        log_path = getattr(self, "_session_log_path", None)
        log_suffix = f" (log: {display_log_path(log_path)})" if log_path else ""
        log_stage("agent", f"Running {action}{log_suffix}")
        heartbeat_stop, heartbeat = self._start_heartbeat(agent_name)
        try:
            yield
        except Exception as exc:
            elapsed_seconds = int(time.monotonic() - self._agent_activity_started_at)
            self._stop_heartbeat(heartbeat_stop, heartbeat)
            self._clear_live_status()
            outcome = "timed out" if _is_timeout_exception(exc) else "failed"
            current_log_path = getattr(self, "_session_log_path", None)
            current_log_suffix = (
                f" (log: {display_log_path(current_log_path)})" if current_log_path else ""
            )
            log_stage(
                "agent",
                f"{action} {outcome} after {_format_elapsed(elapsed_seconds)}{current_log_suffix}",
            )
            raise
        else:
            elapsed_seconds = int(time.monotonic() - self._agent_activity_started_at)
            self._stop_heartbeat(heartbeat_stop, heartbeat)
            self._clear_live_status()
            current_log_path = getattr(self, "_session_log_path", None)
            current_log_suffix = (
                f" (log: {display_log_path(current_log_path)})" if current_log_path else ""
            )
            log_stage(
                "agent",
                f"{action} completed in {_format_elapsed(elapsed_seconds)}{current_log_suffix}",
            )

    def _current_agent_action(self) -> str:
        configured = getattr(self, "_pending_agent_action", None)
        if isinstance(configured, str) and configured:
            return configured
        task_type = str(getattr(self, "_task_type", "agent-task"))
        return f"{task_type.replace('-', '_')}()"

    @staticmethod
    def _live_status_enabled() -> bool:
        try:
            parallelism = int(os.environ.get("FORGE_PARALLELISM", "1"))
        except ValueError:
            parallelism = 1
        return sys.stdout.isatty() and parallelism == 1

    def _start_heartbeat(self, agent_name: str) -> tuple[threading.Event | None, threading.Thread | None]:
        if not self._live_status_enabled():
            return None, None
        stop = threading.Event()

        def heartbeat() -> None:
            while not stop.wait(1):
                self._print_live_status(agent_name, "")

        thread = threading.Thread(target=heartbeat, daemon=True)
        thread.start()
        return stop, thread

    @staticmethod
    def _stop_heartbeat(stop: threading.Event | None, thread: threading.Thread | None) -> None:
        if stop is None or thread is None:
            return
        stop.set()
        thread.join(timeout=2)

    @abstractmethod
    def send_prompt(self, prompt: str) -> str:
        """Send a prompt to the agent and return its text response."""

    @abstractmethod
    def fork(self, prompt: str):
        """Create a child conversation branch that inherits full history and send the prompt to it."""

    @abstractmethod
    def compact_fork(self, prompt: str):
        """Like ``fork``, but condenses conversation history before branching to reduce token usage."""

    @abstractmethod
    def clear_context(self) -> None:
        """Clear any agent-side conversation state so the next interaction starts fresh."""

    def replace_persistent_instructions(self, persistent_instructions: str | None) -> None:
        """Replace persistent instructions used for future prompts."""
        raise NotImplementedError(f"{type(self).__name__} does not support persistent instruction replacement")

    @abstractmethod
    def run_test_command(self, test_cmd: str) -> str:
        """Execute a shell test command and return agent-visible stdout/stderr diagnostics."""

    def graphify(self, source_dirs: list[str]) -> str:
        """Send graphify prompts to the agent session to build a merged knowledge graph context."""
        if not source_dirs:
            return ""
        log_stage("graphify", f"Initializing knowledge graph context for {len(source_dirs)} source(s)")
        result = self.send_prompt(f"/graphify {source_dirs[0]} --include-local")
        for extra_dir in source_dirs[1:]:
            log_stage("graphify", f"Merging graph from {display_log_path(extra_dir)}")
            result = self.send_prompt(f"/graphify {extra_dir} --update")
        log_stage("graphify", "Knowledge graph context initialized")
        return result


def send_agent_prompt(agent: object, prompt: str, action: str) -> str:
    """Send a prompt with an action name when the adapter supports it."""
    send_for_action = getattr(agent, "send_prompt_for_action", None)
    if callable(send_for_action):
        return str(send_for_action(prompt, action))
    send_prompt = getattr(agent, "send_prompt")
    return str(send_prompt(prompt))


def _format_elapsed(seconds: int | float) -> str:
    total_seconds = max(int(seconds), 0)
    hours, remainder = divmod(total_seconds, 3600)
    minutes, remaining_seconds = divmod(remainder, 60)
    if hours:
        return f"{hours:02d}:{minutes:02d}:{remaining_seconds:02d}"
    return f"{minutes:02d}:{remaining_seconds:02d}"


def _is_timeout_exception(exc: BaseException) -> bool:
    current: BaseException | None = exc
    while current is not None:
        if isinstance(current, (AgentTimeoutError, TimeoutError)):
            return True
        if "timed out" in str(current).lower():
            return True
        cause = current.__cause__
        current = cause if isinstance(cause, BaseException) else None
    return False
