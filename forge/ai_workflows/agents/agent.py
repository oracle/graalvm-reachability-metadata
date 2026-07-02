# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from abc import ABC, abstractmethod
import os
import shutil
import tempfile

from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import display_log_path, resolve_task_log_dir


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

    # Fork token accounting. A fork child inherits this agent's counters, spends
    # its own tokens, and is then discarded. On the child's ``with``-block exit
    # it folds its delta into the parent's accumulators below, so a discarded
    # child's usage still surfaces in run metrics. Token-tracking agents add
    # these accumulators into their token properties. Class-level defaults let
    # this work without every backend initializing them in ``__init__``.
    _forked_tokens_sent: int = 0
    _forked_tokens_received: int = 0
    _forked_tokens_cached: int = 0
    _fork_parent: "Agent | None" = None
    _fork_baseline: "tuple[int, int, int] | None" = None

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

    def add_forked_usage(
            self,
            tokens_sent: int,
            tokens_received: int,
            cached_input_tokens: int = 0,
    ) -> None:
        """Accumulate token usage handed back by a discarded fork child.

        Token-tracking agents surface these accumulators through their token
        properties; keeping them on the base means the bookkeeping works for any
        backend without extra per-agent code.
        """
        self._forked_tokens_sent += max(int(tokens_sent), 0)
        self._forked_tokens_received += max(int(tokens_received), 0)
        self._forked_tokens_cached += max(int(cached_input_tokens), 0)

    def _begin_fork_child(self, parent: "Agent") -> None:
        """Stamp a freshly forked child with its parent and pre-turn baseline.

        Call this after the child inherits the parent's counters but before its
        first turn is counted, so the child's delta (``total - baseline``) covers
        every turn it runs, including the initial generation prompt.
        """
        self._fork_parent = parent
        self._fork_baseline = self._usage_tuple()

    def _usage_tuple(self) -> tuple[int, int, int]:
        """Current (sent, received, cached) totals, tolerant of ``None`` cached."""
        return (
            int(self.total_tokens_sent or 0),
            int(self.total_tokens_received or 0),
            int(self.cached_input_tokens_used or 0),
        )

    def __enter__(self) -> "Agent":
        return self

    def __exit__(self, exc_type, exc, traceback) -> bool:
        # Block exit is the fork child's "kill" point: fold its delta into the
        # parent so the discarded child's tokens survive in run metrics.
        if self._fork_parent is not None:
            sent, received, cached = self._usage_tuple()
            base_sent, base_received, base_cached = self._fork_baseline or (0, 0, 0)
            self._fork_parent.add_forked_usage(
                sent - base_sent,
                received - base_received,
                cached - base_cached,
            )
            self._fork_parent = None
            self._fork_baseline = None
        return False

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
        print(f"[{agent_name} session log: {display_log_path(log_path)}]", flush=True)
        self._session_log_announced = True

    def _print_live_status(self, agent_name: str, detail: str) -> None:
        """Render a single-line live status update without advancing the console."""
        terminal_width = shutil.get_terminal_size(fallback=(120, 20)).columns
        max_message_length = max(terminal_width - 1, 20)
        message = f"[{agent_name}] {detail}".replace("\n", " ").strip()
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
