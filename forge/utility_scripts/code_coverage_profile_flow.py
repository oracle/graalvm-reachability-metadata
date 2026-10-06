# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Bytecode control-flow tables for the deep-method report.

Loads the extractor's `flow.csv` and answers the one question a fork hint
needs: which branches that ran never entered the never-executed region around
a given call (§AR-code-coverage-deep-navigation.1.3,
§AR-code-coverage-deep-navigation.3.2).
"""

from __future__ import annotations

import bisect
import csv
from collections.abc import Callable
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
class DeadRegion:
    """The never-executed blocks around an invoke, and the forks into them.

    `forks` pairs each branch that ran with the successor bci through which it
    never entered the region (§AR-code-coverage-deep-navigation.3.2).
    """

    blocks: frozenset[int]
    forks: tuple[tuple[Branch, int], ...]


@dataclass(frozen=True)
class MethodFlow:
    """Branches and basic blocks of one method body."""

    branches: tuple[Branch, ...]
    #: Sorted block start bcis; a block covers `[start, next start)`.
    block_starts: tuple[int, ...]
    #: Every successor of a block, exception edges included.
    block_successors: dict[int, tuple[int, ...]]
    #: The successors entered only by an exception: the handlers of every
    #: `try` covering the block (§AR-code-coverage-deep-navigation.1.3).
    exception_successors: dict[int, frozenset[int]]

    def block_of(self, bci: int) -> int | None:
        index: int = bisect.bisect_right(self.block_starts, bci) - 1
        return self.block_starts[index] if index >= 0 else None

    def branch_in(self, block: int) -> Branch | None:
        """The branch instruction that ends `block`, if any."""
        return next((branch for branch in self.branches if self.block_of(branch.bci) == block), None)

    def normal_successors(self, block: int) -> tuple[int, ...]:
        """Successors reached without an exception; empty for a block that
        ends in a return or throw."""
        handlers: frozenset[int] = self.exception_successors.get(block, frozenset())
        return tuple(
            successor for successor in self.block_successors.get(block, ())
            if successor not in handlers
        )

    def predecessors(self) -> dict[int, tuple[int, ...]]:
        inverted: dict[int, list[int]] = {}
        for block, successors in self.block_successors.items():
            for successor in successors:
                inverted.setdefault(successor, []).append(block)
        return {block: tuple(sorted(blocks)) for block, blocks in inverted.items()}

    def dead_region(
            self,
            target_bci: int,
            branch_counts: Callable[[Branch], dict[int, int] | None],
            line_ran: Callable[[int], bool | None],
    ) -> DeadRegion | None:
        """Walk backwards from `target_bci` through blocks that never executed.

        A block ran when its branch counter is positive, or without a counter
        when `line_ran` says JaCoCo covers its branch's line; a block without a
        branch instruction is dead when its only normal exit is. The walk stops
        at a block that ran, at contradictory evidence — a positive count into
        a block believed dead — and at unknown liveness, and never follows an
        exception edge. A block that ran but whose every real successor is
        dead left by an exception and is no fork; without a counter it cannot
        be told from a block that never ran, so the walk passes through it
        (§AR-code-coverage-deep-navigation.3.2).
        """
        target: int | None = self.block_of(target_bci)
        if target is None:
            return None
        predecessors: dict[int, tuple[int, ...]] = self.predecessors()
        dead: set[int] = {target}
        pending: list[int] = [target]
        #: Block that ran -> the dead successors it never entered.
        edges: dict[int, set[int]] = {}
        #: Blocks that ran by JaCoCo's line status alone.
        soft: set[int] = set()

        def expand() -> None:
            while pending:
                block: int = pending.pop()
                for predecessor in predecessors.get(block, ()):
                    if predecessor in dead or block not in self.normal_successors(predecessor):
                        continue
                    branch: Branch | None = self.branch_in(predecessor)
                    if branch is None:
                        dead.add(predecessor)
                        pending.append(predecessor)
                        continue
                    counts: dict[int, int] | None = branch_counts(branch)
                    if counts is None:
                        ran: bool | None = line_ran(branch.bci)
                        if ran:
                            soft.add(predecessor)
                    else:
                        ran = sum(counts.values()) > 0
                        if ran and counts.get(block, 0) > 0:
                            continue
                    if ran is None:
                        continue
                    if not ran:
                        dead.add(predecessor)
                        pending.append(predecessor)
                        continue
                    edges.setdefault(predecessor, set()).add(block)

        expand()
        while True:
            passable: list[int] = [
                block for block in soft
                if all(successor in dead for successor in self.normal_successors(block))
            ]
            if not passable:
                break
            for block in passable:
                soft.discard(block)
                edges.pop(block, None)
                dead.add(block)
                pending.append(block)
            expand()
        forks: list[tuple[Branch, int]] = []
        for block in sorted(edges):
            branch = self.branch_in(block)
            assert branch is not None
            if any(successor not in dead for successor in branch.reachable_successors):
                forks.extend((branch, successor) for successor in sorted(edges[block]))
        return DeadRegion(blocks=frozenset(dead), forks=tuple(forks))


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


def _parse_blocks(
        encoded: str,
) -> tuple[tuple[int, ...], dict[int, tuple[int, ...]], dict[int, frozenset[int]]]:
    """Blocks as `start>successor,...`; a `~` suffix marks an exception edge."""
    successors: dict[int, tuple[int, ...]] = {}
    exceptional: dict[int, frozenset[int]] = {}
    for entry in encoded.split(";"):
        start, _, targets = entry.partition(">")
        block: int = int(start)
        successors[block] = tuple(int(target.rstrip("~")) for target in targets.split(",") if target)
        exceptional[block] = frozenset(
            int(target.rstrip("~")) for target in targets.split(",") if target.endswith("~")
        )
    return tuple(sorted(successors)), successors, exceptional


def load_library_flow(path: str) -> dict[str, MethodFlow]:
    """Read `flow.csv`: one row per library method that holds a branch."""
    flows: dict[str, MethodFlow] = {}
    try:
        with open(path, encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle):
                block_starts, block_successors, exception_successors = _parse_blocks(row["blocks"])
                flows[row["id"]] = MethodFlow(
                    branches=tuple(
                        _parse_branch(entry) for entry in row["branches"].split(";") if entry
                    ),
                    block_starts=block_starts,
                    block_successors=block_successors,
                    exception_successors=exception_successors,
                )
    except (OSError, csv.Error, KeyError, ValueError) as error:
        raise ProfileFormatError(f"Cannot read control-flow table '{path}'.") from error
    return flows
