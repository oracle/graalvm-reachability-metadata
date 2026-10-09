# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Markdown and LCOV rendering for the deep-method report.

Turns the correlated report and prompt records into the compact prompt
markdown and the guidance-only LCOV file (§AR-code-coverage-improvement.3):
each group's routes, then its miss classifications in one section per cause
(§AR-code-coverage-deep-navigation.3.3).
"""

from __future__ import annotations

from utility_scripts.code_coverage_jacoco import JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef, parse_inventory_id
from utility_scripts.code_coverage_profile_diagnoses import (
    Diagnosis,
    SourceRoot,
    diagnosis_lines,
    display_method,
)
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_records import (
    NearCallRecord,
    simple_owner,
    translated_path,
)
from utility_scripts.code_coverage_profile_routes import Sample, SampledProfile

# A thematic break between consecutive route groups; a blank line always
# precedes it, so Markdown never reads it as a setext heading underline.
GROUP_SEPARATOR = "---"


def _display_path(static_path: list[int], graph: CallGraph, limit: int = 6) -> str:
    # Generated lambda classes and extracted bodies carry compiler-chosen names;
    # the agent can only act on the method that creates them
    # (§AR-code-coverage-improvement.4.2.1).
    path: list[MethodRef] = translated_path(static_path, graph)
    selected: list[MethodRef | None]
    if len(path) <= limit:
        selected = list(path)
    else:
        selected = [*path[:2], None, *path[-3:]]

    labels: list[str] = []
    previous_owner: str | None = None
    for ref in selected:
        if ref is None:
            labels.append("…")
            previous_owner = None
            continue
        labels.append(display_method(ref, ref.owner != previous_owner))
        previous_owner = ref.owner
    return " → ".join(labels)


def _step_label(step: dict) -> str:
    """`Caller.m() → Impl.m()` for one unobserved dispatch step."""
    labels: list[str] = []
    for method_id in (step["caller"], step["callee"]):
        ref: MethodRef | None = parse_inventory_id(method_id)
        labels.append(display_method(ref, True) if ref is not None else method_id)
    return " → ".join(labels)


def _target_label(record: NearCallRecord, graph: CallGraph) -> str:
    """The uncovered call as the route's last step names it."""
    path: list[MethodRef] = translated_path(record.static_path, graph)
    previous: MethodRef | None = path[-2] if len(path) > 1 else None
    return display_method(path[-1], previous is None or path[-1].owner != previous.owner)


def _prompt_line(
        record: NearCallRecord,
        graph: CallGraph,
        notes: dict[str, dict],
) -> str:
    """One prompt path with its synthetic-method notes."""
    note: dict = notes.get(record.target_ref.canonical_id, {})
    # A public target may be called directly, unlike an internal one
    # (§AR-code-coverage-improvement.4.2).
    suffixes: list[str] = ["public API"] if note.get("publicApi") else []
    closures: dict | None = note.get("closures")
    if closures is not None and closures["unexecuted"]:
        suffixes.append(
            f"{closures['total']} closures, {closures['unexecuted']} never executed"
        )
    if note.get("handOff"):
        suffixes.append(f"runs on another thread via `{note['handOff']}` — the test must wait")
    unobserved: list[dict] = note.get("unobservedSteps") or []
    if unobserved:
        more: str = f" (+{len(unobserved) - 1} more)" if len(unobserved) > 1 else ""
        suffixes.append(
            f"route assumes `{_step_label(unobserved[0])}`, a dispatch the run never made{more}"
        )
    path: str = f"`{_display_path(record.static_path, graph)}`"
    return f"{path} — {'; '.join(suffixes)}" if suffixes else path


def _diagnosis(record: NearCallRecord, graph: CallGraph, notes: dict[str, dict]) -> Diagnosis:
    classification: dict = notes.get(record.target_ref.canonical_id, {}).get(
        "missClassification", {"kind": "no-fork", "target": None},
    )
    return Diagnosis(_target_label(record, graph), classification)


def _placement(session: dict | None) -> str:
    """Where this prompt's tests go, by the session kind
    (§AR-code-coverage-deep-navigation.4)."""
    if session is None:
        return (
            "Inside that suite, write one test class per subsystem you drive, not one "
            "class per run. The targets below cluster into a few subsystems — each "
            "public entry in the routes drives one of them — and a scenario built for "
            "its own subsystem, with the setup that subsystem needs, reaches far more "
            "of it than one appended to a class built for something else. Before "
            "extending an existing test class, check whether the cluster you are about "
            "to drive already has one; if not, add a class."
        )
    owners: str = ", ".join(f"`{simple_owner(owner)}`" for owner in session["entryOwners"])
    if session["kind"] == "monolith":
        return (
            f"Every route below enters the library through {owners}, so one subsystem "
            "and one setup serve them all. Drive them from one test class for that "
            "subsystem: extend the suite's class for it if one exists, otherwise add it."
        )
    return (
        f"The routes below come from several small groups entering through {owners}. "
        "They do not share one setup, so expect to change more than one test file: "
        "put each route in the test class for its own subsystem, extending the "
        "suite's class where one fits and adding a class where none does."
    )


def write_markdown(
        report: dict,
        prompt_records: list[NearCallRecord],
        graph: CallGraph,
        coordinate: str,
        iteration: int,
        md_path: str,
        session: dict | None = None,
        source_root: str | None = None,
) -> None:
    notes: dict[str, dict] = {target["id"]: target for target in report["bulkTargets"]}
    scope: str = (
        f"iteration {iteration}, session {session['index']} of {session['count']}"
        if session is not None else f"iteration {iteration}"
    )
    lines: list[str] = [
        f"# Deep coverage paths ({scope}) — {coordinate}",
        "",
        # The public-entry obligation (§AR-code-coverage-deep-navigation.2).
        "Reach every target below through its public entry; never call internal "
        "methods directly.",
        "",
    ]
    # Filled once the routes are rendered and known to use the root.
    root_line_index: int = len(lines)
    lines += [
        "## Where the tests go",
        "",
        "All of them belong in the dedicated coverage suite, and nowhere else: the "
        "library's regular test sources are metadata-generation tests and stay "
        "untouched.",
        "",
        _placement(session),
    ]
    sources: SourceRoot = SourceRoot(source_root)

    # Every prompted record is a sampled route (§AR-code-coverage-deep-navigation.2).
    sampled_groups: dict[tuple[str, int], list[NearCallRecord]] = {}
    sampled_group_order: list[tuple[str, int]] = []
    for record in prompt_records:
        assert record.join_kind == "sampled" and record.sample is not None and record.static_path
        group_key: tuple[str, int] = (record.sample.context_id, record.static_path[0])
        if group_key not in sampled_groups:
            sampled_groups[group_key] = []
            sampled_group_order.append(group_key)
        sampled_groups[group_key].append(record)

    lines += ["", "## Observed (sampled guidance only)", ""]
    if not sampled_group_order:
        lines.append("_No sampled context reaches an actionable uncovered path._")
    for position, group_key in enumerate(sampled_group_order):
        if position:
            lines += [GROUP_SEPARATOR, ""]
        records: list[NearCallRecord] = sampled_groups[group_key]
        representative: NearCallRecord = records[0]
        assert representative.sample is not None
        sample: Sample = representative.sample
        join_index: int = representative.sampled_join_path_index or 0
        observed_path: list[int] = [
            static_id
            for static_id, _ in sample.path[join_index:join_index + 2]
        ]
        lines.append("Observed:")
        lines.append(f"`{_display_path(observed_path, graph)}`")
        lines.append("")
        lines.append("Uncovered paths:")
        for record in records:
            lines.append(_prompt_line(record, graph, notes))
        # The diagnoses follow the routes, one section per cause
        # (§AR-code-coverage-deep-navigation.3.3).
        lines += diagnosis_lines(
            [_diagnosis(record, graph, notes) for record in records], sources,
        )
        lines.append("")
    # Locations are relative to the stated source root, which only a prompt
    # with a resolved location states (§AR-code-coverage-deep-navigation.3.2).
    if sources.resolved:
        lines[root_line_index:root_line_index] = [
            f"Library sources: `{sources.path}`; a location with a directory is "
            "relative to it, a bare file name was not found under it.",
            "",
        ]
    # Totals, omitted counts and caveats stay in the JSON report
    # (§AR-code-coverage-deep-navigation.2).
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def write_lcov(
        profile: SampledProfile,
        graph: CallGraph,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        lcov_path: str,
) -> None:
    """Emit only positive sampled observations as guidance-only LCOV."""
    by_method: dict[str, tuple[JacocoMethodCoverage, int]] = {}
    for static_id, count in profile.sample_counts.items():
        if count <= 0:
            continue
        ref: MethodRef = graph.methods[static_id]
        coverage: JacocoMethodCoverage | None = jacoco_methods.get(ref.canonical_id)
        if coverage is None:
            continue
        previous: tuple[JacocoMethodCoverage, int] | None = by_method.get(ref.canonical_id)
        total: int = count + (previous[1] if previous is not None else 0)
        by_method[ref.canonical_id] = (coverage, total)

    by_source: dict[str, list[tuple[MethodRef, int, int]]] = {}
    for coverage, count in by_method.values():
        ref: MethodRef = coverage.method_ref
        source_path: str = coverage.source_path or f"{ref.owner.replace('.', '/')}.java"
        source_line: int = coverage.source_line or 1
        by_source.setdefault(source_path, []).append((ref, source_line, count))

    lines: list[str] = ["TN:sampled-pgo-guidance"]
    for source_path in sorted(by_source):
        entries: list[tuple[MethodRef, int, int]] = sorted(
            by_source[source_path], key=lambda item: (item[1], item[0].canonical_id)
        )
        lines.append(f"SF:{source_path}")
        for ref, line, _ in entries:
            lines.append(f"FN:{line},{ref.canonical_id}")
        for ref, _, count in entries:
            lines.append(f"FNDA:{count},{ref.canonical_id}")
        lines.append(f"FNF:{len(entries)}")
        lines.append(f"FNH:{len(entries)}")
        lines.append("end_of_record")
    with open(lcov_path, "w", encoding="utf-8") as lcov_file:
        lcov_file.write("\n".join(lines) + "\n")
