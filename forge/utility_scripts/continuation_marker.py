# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Durable run-continuation marker for failed Forge issue runs."""

import json
import os
from dataclasses import dataclass, field
from collections.abc import Callable
from typing import Any

CONTINUATION_MARKER_FILENAME = ".continuation-marker.json"
SCHEMA_VERSION = 1

PHASE_SETUP = "setup"
PHASE_FIX = "fix"
PHASE_EXPLORE = "explore"
PHASE_FINALIZATION = "finalization"
PHASE_PUBLICATION = "publication"
ORDERED_PHASES = [PHASE_SETUP, PHASE_FIX, PHASE_EXPLORE, PHASE_FINALIZATION, PHASE_PUBLICATION]

STATUS_PENDING = "pending"
STATUS_RUNNING = "running"
STATUS_COMPLETED = "completed"
STATUS_SKIPPED = "skipped"
TERMINAL_PHASE_STATUSES = {STATUS_COMPLETED, STATUS_SKIPPED}


def continuation_marker_path(repo_path: str) -> str:
    """Return the Forge-local continuation marker path for a reachability checkout."""
    return os.path.join(repo_path, "forge", CONTINUATION_MARKER_FILENAME)


def find_continuation_marker_path(repo_path: str) -> str | None:
    """Return the Forge-local continuation marker path when it exists."""
    path = continuation_marker_path(repo_path)
    return path if os.path.isfile(path) else None


def load_continuation_marker(path: str | None) -> "ContinuationMarker | None":
    """Load a marker if the path exists."""
    if not path or not os.path.isfile(path):
        return None
    return ContinuationMarker.load(path)


def _default_phases() -> dict[str, dict[str, Any]]:
    return {
        PHASE_SETUP: {"status": STATUS_PENDING, "preflightDone": False, "setupDone": False},
        PHASE_FIX: {"status": STATUS_PENDING, "iteration": None},
        PHASE_EXPLORE: {"status": STATUS_PENDING, "exhaustedClasses": []},
        PHASE_FINALIZATION: {"status": STATUS_PENDING},
        PHASE_PUBLICATION: {"status": STATUS_PENDING, "isPushed": False, "branch": None},
    }


@dataclass
class ContinuationMarker:
    """Machine-readable state needed to resume a failed Forge run.

    The marker stores only logical state that cannot be reconstructed from the
    preserved branch HEAD, following the continuation contract
    (§FS-forge-run-continuation.2).
    """

    continue_from: str
    preserved_branch: str | None
    strategy_name: str
    issue_number: int
    label: str
    coordinate: str
    new_version: str | None
    phases: dict[str, dict[str, Any]] = field(default_factory=_default_phases)
    schema_version: int = SCHEMA_VERSION

    @classmethod
    def create(
            cls,
            *,
            strategy_name: str,
            issue_number: int,
            label: str,
            coordinate: str,
            new_version: str | None,
    ) -> "ContinuationMarker":
        """Create a fresh marker for a claimed issue run."""
        marker = cls(
            continue_from=PHASE_SETUP,
            preserved_branch=None,
            strategy_name=strategy_name,
            issue_number=issue_number,
            label=label,
            coordinate=coordinate,
            new_version=new_version,
        )
        marker.recompute_continue_from()
        return marker

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "ContinuationMarker":
        """Build a marker from its JSON payload."""
        if int(payload.get("schemaVersion", 0)) != SCHEMA_VERSION:
            raise ValueError(f"Unsupported continuation marker schemaVersion: {payload.get('schemaVersion')}")
        phases = _default_phases()
        for phase_name, phase_payload in dict(payload.get("phases", {})).items():
            if phase_name in phases and isinstance(phase_payload, dict):
                phases[phase_name].update(phase_payload)
        marker = cls(
            continue_from=str(payload["continueFrom"]),
            preserved_branch=payload.get("preservedBranch"),
            strategy_name=str(payload["strategyName"]),
            issue_number=int(payload["issueNumber"]),
            label=str(payload["label"]),
            coordinate=str(payload["coordinate"]),
            new_version=payload.get("newVersion"),
            phases=phases,
            schema_version=SCHEMA_VERSION,
        )
        marker.recompute_continue_from()
        return marker

    @classmethod
    def load(cls, path: str) -> "ContinuationMarker":
        """Load a marker from JSON."""
        with open(path, "r", encoding="utf-8") as marker_file:
            return cls.from_dict(json.load(marker_file))

    def to_dict(self) -> dict[str, Any]:
        """Return a JSON-serializable marker payload."""
        self.recompute_continue_from()
        return {
            "schemaVersion": self.schema_version,
            "continueFrom": self.continue_from,
            "preservedBranch": self.preserved_branch,
            "strategyName": self.strategy_name,
            "issueNumber": self.issue_number,
            "label": self.label,
            "coordinate": self.coordinate,
            "newVersion": self.new_version,
            "phases": self.phases,
        }

    def save(self, path: str) -> None:
        """Write the marker to JSON."""
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as marker_file:
            json.dump(self.to_dict(), marker_file, indent=2, sort_keys=True)
            marker_file.write("\n")

    def recompute_continue_from(self) -> str:
        """Refresh and return the first non-terminal phase."""
        for phase_name in ORDERED_PHASES:
            status = str(self.phases.get(phase_name, {}).get("status") or STATUS_PENDING)
            if status not in TERMINAL_PHASE_STATUSES:
                self.continue_from = phase_name
                return self.continue_from
        self.continue_from = PHASE_PUBLICATION
        return self.continue_from

    def mark_phase_running(self, phase_name: str, **fields: Any) -> None:
        """Mark a phase as entered and update phase-local fields."""
        phase = self._phase(phase_name)
        phase["status"] = STATUS_RUNNING
        phase.update(fields)
        self.recompute_continue_from()

    def mark_phase_completed(self, phase_name: str, **fields: Any) -> None:
        """Mark a phase complete and update phase-local fields."""
        phase = self._phase(phase_name)
        phase["status"] = STATUS_COMPLETED
        phase.update(fields)
        self.recompute_continue_from()

    def mark_phase_pending(self, phase_name: str, **fields: Any) -> None:
        """Mark a phase as pending after an incomplete transition."""
        phase = self._phase(phase_name)
        phase["status"] = STATUS_PENDING
        phase.update(fields)
        self.recompute_continue_from()

    def mark_phase_skipped(self, phase_name: str, **fields: Any) -> None:
        """Mark a phase as intentionally skipped."""
        phase = self._phase(phase_name)
        phase["status"] = STATUS_SKIPPED
        phase.update(fields)
        self.recompute_continue_from()

    def mark_setup_preflight_done(self) -> None:
        """Record the setup preflight sub-step."""
        self._phase(PHASE_SETUP)["preflightDone"] = True
        self.recompute_continue_from()

    def mark_setup_done(self) -> None:
        """Record completed deterministic setup."""
        self.mark_phase_completed(PHASE_SETUP, preflightDone=True, setupDone=True)

    def record_iteration(self, phase_name: str, iteration: int | None) -> None:
        """Record the latest logical iteration for a continuous phase."""
        if phase_name not in {PHASE_FIX, PHASE_EXPLORE}:
            return
        self._phase(phase_name)["iteration"] = iteration
        self.recompute_continue_from()

    def record_exhausted_classes(self, exhausted_classes: list[str]) -> None:
        """Record dynamic-access classes that a fresh report cannot reconstruct."""
        self._phase(PHASE_EXPLORE)["exhaustedClasses"] = sorted(set(exhausted_classes))
        self.recompute_continue_from()

    def record_preserved_branch(self, branch_name: str) -> None:
        """Record the preservation branch that carries this marker."""
        self.preserved_branch = branch_name

    def record_publication_branch(self, branch_name: str) -> None:
        """Record the branch that publication should use."""
        self._phase(PHASE_PUBLICATION)["branch"] = branch_name
        self.recompute_continue_from()

    def record_publication_pushed(self, branch_name: str) -> None:
        """Record that publication pushed the PR branch."""
        self._phase(PHASE_PUBLICATION).update({
            "status": STATUS_PENDING,
            "isPushed": True,
            "branch": branch_name,
        })
        self.recompute_continue_from()

    def mark_publication_completed(self, branch_name: str | None = None) -> None:
        """Record that PR creation reached the terminal publication point."""
        fields: dict[str, Any] = {}
        if branch_name:
            fields["branch"] = branch_name
        self.mark_phase_completed(PHASE_PUBLICATION, **fields)

    def publication_is_pushed(self) -> bool:
        """Return True when resume should skip the push and only find-or-create the PR."""
        phase = self._phase(PHASE_PUBLICATION)
        return bool(phase.get("isPushed")) and bool(phase.get("branch"))

    def publication_branch(self) -> str | None:
        """Return the recorded publication branch."""
        branch = self._phase(PHASE_PUBLICATION).get("branch")
        return str(branch) if branch else None

    def _phase(self, phase_name: str) -> dict[str, Any]:
        if phase_name not in self.phases:
            raise ValueError(f"Unknown continuation phase: {phase_name}")
        return self.phases[phase_name]


def save_phase_update(path: str | None, update: Callable[[ContinuationMarker], None]) -> None:
    """Load, mutate, and save a marker when continuation is active."""
    marker = load_continuation_marker(path)
    if marker is None:
        return
    update(marker)
    marker.save(path)
