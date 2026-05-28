# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dataclasses import dataclass, field


@dataclass(frozen=True)
class NativeGateCodexDiagnostics:
    """Diagnostic context passed from the native verification gate to Codex."""

    coordinate: str
    reproduction_command: str
    staged_agent_dir: str
    staged_trace_dir: str | None = None
    accepted_trace_run_dirs: list[str] = field(default_factory=list)
    last_log_path: str | None = None
