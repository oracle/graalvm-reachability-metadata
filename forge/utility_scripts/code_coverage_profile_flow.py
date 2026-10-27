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
from dataclasses import dataclass, field

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
    """The never-executed blocks around an invoke, and the edges into them.

    `forks` pairs each branch that ran with the successor bci through which it
    never entered the region (§AR-code-coverage-deep-navigation.3.2); `catches`
    pairs each handler block in the region with a block under its `try`, which
    never raised into it (§AR-code-coverage-deep-navigation.3.3).
    """

    blocks: frozenset[int]
    forks: tuple[tuple[Branch, int], ...]
    catches: tuple[tuple[int, int], ...] = ()


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
    #: Handler block start -> the caught type, `any` for a catch-all.
    handler_types: dict[int, str] = field(default_factory=dict)
    #: Blocks holding a call or a `throw`, which can raise a caught exception.
    throwing_blocks: frozenset[int] = frozenset()

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
        when `line_ran` says JaCoCo covers its line; a block without a branch
        instruction is dead when its only normal exit is. The walk stops at a
        block that ran, at contradictory evidence — a positive count into a
        block believed dead — and at unknown liveness. An exception edge is
        never followed: every block under the `try` is recorded as a catch
        boundary. A block that ran but whose every real successor is dead left
        by an exception and is no fork; without a counter it cannot be told
        from a block that never ran, so the walk passes through it
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
        #: Handler block -> blocks under its `try`.
        catches: dict[int, set[int]] = {}

        def expand() -> None:
            while pending:
                block: int = pending.pop()
                for predecessor in predecessors.get(block, ()):
                    if predecessor in dead:
                        continue
                    if block in self.exception_successors.get(predecessor, frozenset()):
                        catches.setdefault(block, set()).add(predecessor)
                        continue
                    branch: Branch | None = self.branch_in(predecessor)
                    if branch is None:
                        dead.add(predecessor)
                        pending.append(predecessor)
                        continue
                    counts: dict[int, int] | None = branch_counts(branch)
                    ran: bool | None
                    if counts is None:
                        ran = line_ran(branch.bci)
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
        return DeadRegion(
            blocks=frozenset(dead),
            forks=tuple(forks),
            catches=tuple(
                (handler, source)
                for handler in sorted(catches)
                for source in sorted(catches[handler]) if source not in dead
            ),
        )

    def block_counts(
            self, branch_counts: Callable[[Branch], dict[int, int] | None],
    ) -> dict[int, int | None]:
        """How often each block ran, propagated forward from the branch counters.

        A block ending in a branch ran as often as its counter sums; any other
        block as often as its normal forward edges were entered, a branch edge
        by its own count and a fall-through by its source's count. A block
        entered by a back edge without a counter, or missing any term, has no
        count (§AR-code-coverage-deep-navigation.3.3).
        """
        predecessors: dict[int, tuple[int, ...]] = self.predecessors()
        counts: dict[int, int | None] = {}

        def count_of(block: int) -> int | None:
            if block in counts:
                return counts[block]
            counts[block] = None
            branch: Branch | None = self.branch_in(block)
            own: dict[int, int] | None = branch_counts(branch) if branch is not None else None
            if own is not None:
                counts[block] = sum(own.values())
                return counts[block]
            normal: list[int] = [
                predecessor for predecessor in predecessors.get(block, ())
                if block in self.normal_successors(predecessor)
            ]
            total: int | None = 0 if normal else None
            for predecessor in normal:
                source_branch: Branch | None = self.branch_in(predecessor)
                if source_branch is not None:
                    source_counts: dict[int, int] | None = branch_counts(source_branch)
                    term: int | None = source_counts.get(block, 0) if source_counts is not None else None
                elif predecessor < block:
                    term = count_of(predecessor)
                else:
                    term = None
                total = total + term if total is not None and term is not None else None
            counts[block] = total
            return total

        for block in self.block_starts:
            count_of(block)
        return counts


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


@dataclass(frozen=True)
class _Blocks:
    starts: tuple[int, ...]
    successors: dict[int, tuple[int, ...]]
    exceptional: dict[int, frozenset[int]]
    handler_types: dict[int, str]
    throwing: frozenset[int]


def _parse_blocks(encoded: str) -> _Blocks:
    """Blocks as `start>successor,...`: `bci~Type` is an exception edge to a
    handler catching `Type`, and `start!>` marks a block that can raise."""
    successors: dict[int, tuple[int, ...]] = {}
    exceptional: dict[int, frozenset[int]] = {}
    handler_types: dict[int, str] = {}
    throwing: set[int] = set()
    for entry in encoded.split(";"):
        start, _, targets = entry.partition(">")
        block: int = int(start.rstrip("!"))
        if start.endswith("!"):
            throwing.add(block)
        normal: list[int] = []
        handlers: set[int] = set()
        for target in filter(None, targets.split(",")):
            bci_text, marker, caught = target.partition("~")
            bci: int = int(bci_text)
            normal.append(bci)
            if marker:
                handlers.add(bci)
                handler_types[bci] = caught or "any"
        successors[block] = tuple(normal)
        exceptional[block] = frozenset(handlers)
    return _Blocks(tuple(sorted(successors)), successors, exceptional, handler_types, frozenset(throwing))


def load_library_flow(path: str) -> dict[str, MethodFlow]:
    """Read `flow.csv`: one row per library method that holds a branch."""
    flows: dict[str, MethodFlow] = {}
    try:
        with open(path, encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle):
                blocks: _Blocks = _parse_blocks(row["blocks"])
                flows[row["id"]] = MethodFlow(
                    branches=tuple(
                        _parse_branch(entry) for entry in row["branches"].split(";") if entry
                    ),
                    block_starts=blocks.starts,
                    block_successors=blocks.successors,
                    exception_successors=blocks.exceptional,
                    handler_types=blocks.handler_types,
                    throwing_blocks=blocks.throwing,
                )
    except (OSError, csv.Error, KeyError, ValueError) as error:
        raise ProfileFormatError(f"Cannot read control-flow table '{path}'.") from error
    return flows
