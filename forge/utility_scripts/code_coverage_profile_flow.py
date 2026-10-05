# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Bytecode control-flow tables for the deep-method report.

Loads the extractor's `flow.csv` and answers the one question a fork hint
needs: which successors of a branch can still reach a given call
(§AR-code-coverage-deep-navigation.1.3).
"""

from __future__ import annotations

import bisect
import csv
from collections.abc import Iterable
from dataclasses import dataclass

from utility_scripts.code_coverage_profile_inputs import ProfileFormatError


@dataclass(frozen=True)
class Branch:
    """One conditional branch or switch and its distinct successor bcis."""

    bci: int
    successors: tuple[int, ...]
    #: Successors javac generated and no test can reach, like the hidden
    #: `default` of an exhaustive switch.
    synthetic: frozenset[int]
    #: javac lowering around the real decision, such as a String switch's
    #: `hashCode` switch; JaCoCo does not count it and neither does a hint.
    plumbing: bool

    @property
    def reachable_successors(self) -> tuple[int, ...]:
        return tuple(bci for bci in self.successors if bci not in self.synthetic)


@dataclass(frozen=True)
class MethodFlow:
    """Branches and basic blocks of one method body."""

    branches: tuple[Branch, ...]
    #: Sorted block start bcis; a block covers `[start, next start)`.
    block_starts: tuple[int, ...]
    block_successors: dict[int, tuple[int, ...]]

    def block_of(self, bci: int) -> int | None:
        index: int = bisect.bisect_right(self.block_starts, bci) - 1
        return self.block_starts[index] if index >= 0 else None

    def reaches(self, start_bci: int, target_bci: int, barrier_bcis: Iterable[int]) -> bool:
        """Whether control entering at `start_bci` can reach `target_bci`.

        The walk never passes back through the block of any of `barrier_bcis`,
        the fork line's branches: a successor that loops to the line would
        otherwise reach everything the line reaches, so a loop header would
        control nothing, and `a || b`, one block per condition, would let a
        loop re-enter through the other one (§AR-code-coverage-deep-navigation.3.2).
        """
        target_block: int | None = self.block_of(target_bci)
        barriers: set[int | None] = {self.block_of(bci) for bci in barrier_bcis}
        start: int | None = self.block_of(start_bci)
        if target_block is None or start is None:
            return False
        visited: set[int] = set()
        pending: list[int] = [start]
        while pending:
            block: int = pending.pop()
            if block in visited:
                continue
            visited.add(block)
            if block == target_block:
                return True
            if block in barriers:
                continue
            pending.extend(self.block_successors.get(block, ()))
        return False


def _parse_branch(entry: str) -> Branch:
    head, _, targets = entry.partition(">")
    plumbing: bool = head.endswith("*")
    successors: list[int] = []
    synthetic: set[int] = set()
    for target in targets.split(","):
        bci: int = int(target.rstrip("!"))
        successors.append(bci)
        if target.endswith("!"):
            synthetic.add(bci)
    return Branch(
        bci=int(head.rstrip("*")),
        successors=tuple(successors),
        synthetic=frozenset(synthetic),
        plumbing=plumbing,
    )


def _parse_blocks(encoded: str) -> tuple[tuple[int, ...], dict[int, tuple[int, ...]]]:
    successors: dict[int, tuple[int, ...]] = {}
    for entry in encoded.split(";"):
        start, _, targets = entry.partition(">")
        successors[int(start)] = tuple(int(target) for target in targets.split(",") if target)
    return tuple(sorted(successors)), successors


def load_library_flow(path: str) -> dict[str, MethodFlow]:
    """Read `flow.csv`: one row per library method that holds a branch."""
    flows: dict[str, MethodFlow] = {}
    try:
        with open(path, encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle):
                block_starts, block_successors = _parse_blocks(row["blocks"])
                flows[row["id"]] = MethodFlow(
                    branches=tuple(
                        _parse_branch(entry) for entry in row["branches"].split(";") if entry
                    ),
                    block_starts=block_starts,
                    block_successors=block_successors,
                )
    except (OSError, csv.Error, KeyError, ValueError) as error:
        raise ProfileFormatError(f"Cannot read control-flow table '{path}'.") from error
    return flows
