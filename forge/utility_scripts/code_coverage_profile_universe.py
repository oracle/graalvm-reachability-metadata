# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Method universes for the deep-method report.

Correlates the public API inventory with JaCoCo, and decides which JaCoCo
methods the deep phase targets: library-owned methods outside the inventory,
plus the inventory entries JaCoCo reported uncovered when the API phase ended,
frozen at the phase's first report (§AR-code-coverage-improvement.4.2).
"""

from __future__ import annotations

from dataclasses import dataclass

from utility_scripts.code_coverage_jacoco import JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef, parse_inventory_id
from utility_scripts.code_coverage_profile_inputs import ProfileFormatError, TargetState
from utility_scripts.code_coverage_profile_records import method_evidence


@dataclass(frozen=True)
class InventoryCoverage:
    """Every parseable API inventory entry with its exact JaCoCo status."""

    refs: list[MethodRef]
    entries: list[dict]
    counts: dict[str, int]


def inventory_coverage(
        inventory: dict,
        jacoco_methods: dict[str, JacocoMethodCoverage],
) -> InventoryCoverage:
    refs: list[MethodRef] = []
    entries: list[dict] = []
    counts: dict[str, int] = {"covered": 0, "uncovered": 0, "unknown": 0}
    for target in inventory.get("targets", []):
        ref: MethodRef | None = parse_inventory_id(target.get("id", ""))
        if ref is None:
            continue
        coverage: JacocoMethodCoverage | None = jacoco_methods.get(ref.canonical_id)
        status: str = coverage.status if coverage is not None else "unknown"
        counts[status] += 1
        refs.append(ref)
        entries.append({"id": ref.canonical_id, "kind": target.get("kind"), "status": status})
    return InventoryCoverage(refs=refs, entries=entries, counts=counts)


def diagnostic_methods(
        jacoco_methods: dict[str, JacocoMethodCoverage],
        graph_ids: set[str],
        inventory_ids: set[str],
) -> list[dict]:
    """Non-inventory methods only one of JaCoCo and the call graph knows."""
    jacoco_ids: set[str] = set(jacoco_methods)
    return [
        *[
            method_evidence(jacoco_methods[method_id], graph_status="not-present")
            for method_id in sorted((jacoco_ids - graph_ids) - inventory_ids)
        ],
        *[
            {
                "id": method_id,
                "status": "not-reported",
                "graphStatus": "present",
                "sourcePath": None,
                "sourceLine": None,
            }
            for method_id in sorted((graph_ids - jacoco_ids) - inventory_ids)
        ],
    ]


@dataclass(frozen=True)
class DeepUniverse:
    #: Library-owned JaCoCo methods outside the API inventory.
    internal_ids: list[str]
    #: Inventory entries JaCoCo reported uncovered when the API phase ended.
    public_ids: list[str]
    #: JaCoCo methods the resolved library jars do not declare.
    foreign_ids: set[str]


def deep_universe(
        jacoco_methods: dict[str, JacocoMethodCoverage],
        inventory_ids: set[str],
        library_methods: set[str] | None,
        carried_public_ids: list[str] | None,
) -> DeepUniverse:
    """Build the universe, freezing its public part on the phase's first report.

    `carried_public_ids` is that frozen part as an earlier report recorded it;
    `None` means this report is the first and freezes it now.
    """
    # A JaCoCo report covers every instrumented class on the test runtime
    # classpath, which for libraries publishing a `test`-classifier artifact
    # includes their own unit tests. Restrict the internal part to methods the
    # resolved library jars actually declare (§AR-code-coverage-improvement.4.2).
    candidate_ids: set[str] = set(jacoco_methods) - inventory_ids
    foreign_ids: set[str] = (
        candidate_ids - library_methods if library_methods is not None else set()
    )
    if carried_public_ids is None:
        public_ids: list[str] = sorted(
            method_id
            for method_id in inventory_ids
            if method_id in jacoco_methods and not jacoco_methods[method_id].covered
        )
    else:
        moved: list[str] = [
            method_id
            for method_id in carried_public_ids
            if method_id not in inventory_ids or method_id not in jacoco_methods
        ]
        if moved:
            raise ProfileFormatError(
                f"Carried public target '{moved[0]}' is no longer a JaCoCo-reported "
                "API inventory entry; the deep universe moved during the phase."
            )
        public_ids = sorted(carried_public_ids)
    return DeepUniverse(
        internal_ids=sorted(candidate_ids - foreign_ids),
        public_ids=public_ids,
        foreign_ids=foreign_ids,
    )


def check_target_states(
        states: dict[str, TargetState],
        universe: DeepUniverse,
        jacoco_methods: dict[str, JacocoMethodCoverage],
) -> None:
    """Reject target state that names a method outside the universe or lies about it."""
    target_ids: set[str] = {*universe.internal_ids, *universe.public_ids}
    invalid_state_ids: list[str] = sorted(set(states) - target_ids)
    if invalid_state_ids:
        raise ProfileFormatError(
            f"Target state id '{invalid_state_ids[0]}' is not in the current deep JaCoCo universe."
        )
    for method_id, state in states.items():
        if state.status == "completed" and not jacoco_methods[method_id].covered:
            raise ProfileFormatError(
                f"Target state '{method_id}' is completed but current JaCoCo reports it uncovered."
            )
