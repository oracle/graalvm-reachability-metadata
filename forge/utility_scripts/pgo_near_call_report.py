# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import csv
import json
import os
from collections import deque
from dataclasses import dataclass

from utility_scripts.dynamic_access_report import DynamicAccessCallSite


PRIMITIVE_ARRAY_TYPES = {
    "Z": "boolean",
    "B": "byte",
    "C": "char",
    "S": "short",
    "I": "int",
    "J": "long",
    "F": "float",
    "D": "double",
}

FRAMEWORK_TYPE_PREFIXES = (
    "com.oracle.svm.",
    "java.",
    "javax.",
    "jdk.",
    "junit.",
    "org.gradle.",
    "org.graalvm.junit.",
    "org.junit.",
    "sun.",
    "worker.org.gradle.",
)

TEST_TYPE_SUFFIXES = (
    "IT",
    "ITCase",
    "Test",
    "TestCase",
    "Tests",
)


@dataclass(frozen=True)
class PgoNearCallRecord:
    call_site: DynamicAccessCallSite
    static_path: list[int]
    static_path_edges: list[dict]
    sampled_path: list[tuple[int, int]]
    sampled_full_path: list[tuple[tuple[str, str, tuple[str, ...], str], int]]
    sampled_path_full_indexes: list[int]
    sampled_join_path_index: int | None
    sample_count: int
    prefix_length: int
    methods: dict

    @property
    def depth_remaining(self) -> int:
        return max(len(self.static_path) - self.prefix_length, 0)


def format_pgo_near_call_guidance(
        report_dir: str,
        call_sites: list[DynamicAccessCallSite],
) -> str:
    """Return prompt-ready PGO path guidance for the best uncovered call site."""
    try:
        records = build_pgo_near_call_records(report_dir, call_sites)
    except (FileNotFoundError, ValueError, KeyError, json.JSONDecodeError) as error:
        return "- PGO near-call guidance unavailable: {error}".format(error=error)

    if not records:
        return "- PGO near-call guidance unavailable: no sampled path matched the uncovered call sites."

    best = records[0]
    lines = [
        "Target uncovered dynamic-access call:",
        _format_call_site(best.call_site),
        "",
        "Closest sampled stack from an existing test to the PGO/call-graph join point:",
        _format_existing_test_stack_to_join(best),
        "",
        "Static path from the PGO-reached method to the uncovered call:",
        _format_path(best.static_path, best.methods, best.static_path_edges),
    ]
    alternate_records = [record for record in records[1:] if record.call_site == best.call_site]
    if alternate_records:
        lines.extend([
            "",
            "Other matching static call-tree edges for this call site:",
        ])
        for alternate in alternate_records[:5]:
            lines.append("- invoke-bci={bci}, missing-steps={steps}".format(
                bci=_target_edge_bci(alternate),
                steps=alternate.depth_remaining,
            ))
        if len(alternate_records) > 5:
            lines.append("- ... {count} more".format(count=len(alternate_records) - 5))
    lines.extend([
        "",
        "Divergence:",
    ])
    divergence = best.static_path[best.prefix_length - 1] if best.prefix_length > 0 else None
    static_next = best.static_path[best.prefix_length] if best.prefix_length < len(best.static_path) else None
    observed_next = _observed_branch_after_join(best)
    static_next_edge = _edge_between(best.static_path_edges, best.prefix_length - 1)
    if divergence is not None:
        lines.append("- PGO reached: {method}".format(method=_format_method(divergence, best.methods)))
    else:
        lines.append("- No sampled method could be joined to the static call graph.")
    if observed_next is not None:
        observed_method, observed_bci = observed_next
        lines.append("- Current sampled execution then goes to: {method} @sample-bci={bci}".format(
            method=_format_key(observed_method),
            bci=observed_bci,
        ))
    else:
        lines.append("- Current sampled path ended at the PGO/call-graph join point.")
    if static_next is not None:
        suffix = ""
        if static_next_edge is not None:
            suffix = " @invoke-bci={bci}".format(bci=static_next_edge["bci"])
        lines.append("- To reach the uncovered call, tests need to drive: {method}{suffix}".format(
            method=_format_method(static_next, best.methods),
            suffix=suffix,
        ))
    else:
        lines.append("- The sampled path already reaches the tracked call.")
    lines.append("- Closest sampled context count: {count}".format(count=best.sample_count))
    lines.append("- Static steps still missing: {count}".format(count=best.depth_remaining))
    return "\n".join(lines)


def build_pgo_near_call_records(
        report_dir: str,
        call_sites: list[DynamicAccessCallSite],
) -> list[PgoNearCallRecord]:
    reports_dir = os.path.join(report_dir, "reports")
    if not os.path.isdir(reports_dir):
        reports_dir = report_dir

    methods, edges, adjacency, reverse_adjacency, roots = _load_static_call_tree(reports_dir)
    iprof, iprof_methods = _load_iprof(_find_file(report_dir, "native-test.iprof"))
    static_key_to_id = {method["key"]: method_id for method_id, method in methods.items()}
    samples = _sample_paths(iprof, iprof_methods, static_key_to_id)

    records: list[PgoNearCallRecord] = []
    for call_site in call_sites:
        target_edges = _find_static_edges_to_call_site(edges, methods, call_site)
        if not target_edges:
            continue
        for target_edge in target_edges:
            records.append(_build_record_for_target_edge(
                call_site,
                target_edge,
                methods,
                adjacency,
                reverse_adjacency,
                roots,
                samples,
            ))
    records.sort(key=_record_sort_key, reverse=True)
    return records


def _build_record_for_target_edge(
        call_site: DynamicAccessCallSite,
        target_edge: dict,
        methods: dict,
        adjacency: dict[int, list[dict]],
        reverse_adjacency: dict[int, list[dict]],
        roots: list[int],
        samples: list[dict],
) -> PgoNearCallRecord:
    next_edges_to_target = _next_edges_to_target_caller(reverse_adjacency, target_edge["caller"])
    best_join = _choose_best_sampled_join(next_edges_to_target, target_edge, methods, samples)
    if best_join is not None:
        return PgoNearCallRecord(
            call_site=call_site,
            static_path=best_join["static_path"],
            static_path_edges=best_join["static_path_edges"],
            sampled_path=best_join["sample"]["path"],
            sampled_full_path=best_join["sample"]["full_path"],
            sampled_path_full_indexes=best_join["sample"]["path_full_indexes"],
            sampled_join_path_index=best_join["sampled_join_path_index"],
            sample_count=best_join["sample"]["count"],
            prefix_length=1,
            methods=methods,
        )

    static_path, static_path_edges = _shortest_path_to_edge(adjacency, roots, target_edge)
    closest_sample = _choose_closest_sample(static_path, samples)
    return PgoNearCallRecord(
        call_site=call_site,
        static_path=static_path,
        static_path_edges=static_path_edges,
        sampled_path=closest_sample["path"],
        sampled_full_path=closest_sample["full_path"],
        sampled_path_full_indexes=closest_sample["path_full_indexes"],
        sampled_join_path_index=None,
        sample_count=closest_sample["count"],
        prefix_length=_common_prefix_length(static_path, closest_sample["path"]),
        methods=methods,
    )


def _next_edges_to_target_caller(reverse_adjacency: dict[int, list[dict]], target_caller: int) -> dict[int, dict | None]:
    next_edges: dict[int, dict | None] = {target_caller: None}
    queue = deque([target_caller])
    while queue:
        method_id = queue.popleft()
        for edge in reverse_adjacency.get(method_id, []):
            predecessor = edge["caller"]
            if predecessor in next_edges:
                continue
            next_edges[predecessor] = edge
            queue.append(predecessor)
    return next_edges


def _choose_best_sampled_join(
        next_edges_to_target: dict[int, dict | None],
        target_edge: dict,
        methods: dict,
        samples: list[dict],
) -> dict | None:
    candidates = []
    for sample in samples:
        for sampled_path_index, (method_id, _) in enumerate(sample["path"]):
            if method_id not in next_edges_to_target:
                continue
            static_path, static_path_edges = _path_from_join_to_target_edge(
                method_id,
                next_edges_to_target,
                target_edge,
            )
            candidates.append({
                "sample": sample,
                "sampled_join_path_index": sampled_path_index,
                "static_path": static_path,
                "static_path_edges": static_path_edges,
                "score": _sampled_join_score(sample, sampled_path_index, static_path_edges, methods),
            })
    if not candidates:
        return None
    return max(candidates, key=lambda candidate: candidate["score"])


def _path_from_join_to_target_edge(
        join_method: int,
        next_edges_to_target: dict[int, dict | None],
        target_edge: dict,
) -> tuple[list[int], list[dict]]:
    static_path = [join_method]
    static_path_edges = []
    current = join_method
    while current != target_edge["caller"]:
        edge = next_edges_to_target[current]
        if edge is None:
            break
        static_path_edges.append(edge)
        current = edge["callee"]
        static_path.append(current)
    static_path_edges.append(target_edge)
    static_path.append(target_edge["callee"])
    return static_path, static_path_edges


def _sampled_join_score(
        sample: dict,
        sampled_path_index: int,
        static_path_edges: list[dict],
        methods: dict,
) -> tuple:
    join_method_id = sample["path"][sampled_path_index][0]
    sampled_full_index = sample["path_full_indexes"][sampled_path_index]
    test_index = _existing_test_frame_index(sample["full_path"])
    join_is_after_test = test_index is None or sampled_full_index >= test_index
    return (
        join_is_after_test,
        _method_prompt_quality(methods[join_method_id]["key"]),
        -len(static_path_edges),
        sample["count"],
        sampled_path_index,
    )


def _record_sort_key(record: PgoNearCallRecord) -> tuple:
    join_method = record.static_path[record.prefix_length - 1] if record.prefix_length > 0 else None
    prompt_quality = 0 if join_method is None else _method_prompt_quality(record.methods[join_method]["key"])
    return (
        record.prefix_length > 0,
        prompt_quality,
        -record.depth_remaining,
        record.sample_count,
    )


def _method_prompt_quality(key: tuple[str, str, tuple[str, ...], str]) -> int:
    if _looks_like_framework_frame(key):
        return 0
    if _looks_like_existing_test_frame(key):
        return 1
    return 2


def _normalize_type_name(name: str) -> str:
    dimensions = 0
    while name.startswith("["):
        dimensions += 1
        name = name[1:]
    if dimensions == 0:
        return name
    if name.startswith("L") and name.endswith(";"):
        name = name[1:-1]
    else:
        name = PRIMITIVE_ARRAY_TYPES.get(name, name)
    return name + "[]" * dimensions


def _parse_parameters(parameters: str) -> tuple[str, ...]:
    if parameters == "empty":
        return ()
    return tuple(_normalize_type_name(parameter) for parameter in parameters.split())


def _method_key(method: dict) -> tuple[str, str, tuple[str, ...], str]:
    return (
        _normalize_type_name(method["Type"]),
        method["Name"],
        _parse_parameters(method["Parameters"]),
        _normalize_type_name(method["Return"]),
    )


def _format_key(key: tuple[str, str, tuple[str, ...], str]) -> str:
    holder, name, parameters, return_type = key
    return "{holder}.{name}({parameters}):{return_type}".format(
        holder=holder,
        name=name,
        parameters=", ".join(parameters),
        return_type=return_type,
    )


def _format_method(method_id: int, methods: dict) -> str:
    return _format_key(methods[method_id]["key"])


def _read_csv_by_id(path: str) -> dict[int, dict]:
    with open(path, encoding="utf-8", newline="") as csv_file:
        return {int(row["Id"]): row for row in csv.DictReader(csv_file)}


def _load_static_call_tree(reports_dir: str) -> tuple[dict, list[dict], dict[int, list[dict]], dict[int, list[dict]], list[int]]:
    methods = _read_csv_by_id(_find_file(reports_dir, "call_tree_methods.csv"))
    invokes = _read_csv_by_id(_find_file(reports_dir, "call_tree_invokes.csv"))
    targets_path = _find_file(reports_dir, "call_tree_targets.csv")

    with open(targets_path, encoding="utf-8", newline="") as csv_file:
        targets = list(csv.DictReader(csv_file))

    for method in methods.values():
        method["key"] = _method_key(method)

    edges = []
    adjacency: dict[int, list[dict]] = {}
    reverse_adjacency: dict[int, list[dict]] = {}
    for target in targets:
        invoke = invokes[int(target["InvokeId"])]
        caller_id = int(invoke["MethodId"])
        callee_id = int(target["TargetId"])
        edge = {
            "caller": caller_id,
            "callee": callee_id,
            "bci": invoke["BytecodeIndexes"],
            "is_direct": invoke["IsDirect"],
        }
        edges.append(edge)
        adjacency.setdefault(caller_id, []).append(edge)
        reverse_adjacency.setdefault(callee_id, []).append(edge)

    roots = [method_id for method_id, method in methods.items() if method["IsEntryPoint"] == "true"]
    return methods, edges, adjacency, reverse_adjacency, roots


def _load_iprof(iprof_path: str) -> tuple[dict, dict[int, tuple[str, str, tuple[str, ...], str]]]:
    with open(iprof_path, encoding="utf-8") as file:
        iprof = json.load(file)

    types = {entry["id"]: _normalize_type_name(entry["name"]) for entry in iprof["types"]}
    methods = {}
    for method in iprof["methods"]:
        signature = method["signature"]
        methods[method["id"]] = (
            types[signature[0]],
            method["name"],
            tuple(types[type_id] for type_id in signature[2:]),
            types[signature[1]],
        )
    return iprof, methods


def _parse_context(context: str) -> list[tuple[int, int]]:
    pairs = []
    for item in reversed(context.split("<")):
        method_id, bci = item.split(":", 1)
        pairs.append((int(method_id), int(bci)))
    return pairs


def _sample_paths(iprof: dict, iprof_methods: dict, static_key_to_id: dict) -> list[dict]:
    paths = []
    for profile in iprof.get("samplingProfiles", []):
        mapped_path = []
        mapped_full_indexes = []
        full_path = []
        for method_id, bci in _parse_context(profile["ctx"]):
            key = iprof_methods.get(method_id)
            if key is None:
                continue
            full_path.append((key, bci))
            static_id = static_key_to_id.get(key)
            if static_id is not None and (not mapped_path or mapped_path[-1][0] != static_id):
                mapped_path.append((static_id, bci))
                mapped_full_indexes.append(len(full_path) - 1)
        if mapped_path:
            paths.append({
                "path": mapped_path,
                "full_path": full_path,
                "path_full_indexes": mapped_full_indexes,
                "count": int(profile["records"][0]),
                "context": profile["ctx"],
            })
    return paths


def _find_static_edges_to_call_site(edges: list[dict], methods: dict, call_site: DynamicAccessCallSite) -> list[dict]:
    holder, method_name = _frame_holder_and_method(call_site.frame)
    tracked_holder, tracked_method, tracked_parameters = _tracked_holder_method_and_parameters(call_site.tracked_api)
    if holder is None or method_name is None or tracked_holder is None or tracked_method is None:
        return []

    exact_matches = []
    for edge in edges:
        caller = methods[edge["caller"]]["key"]
        callee = methods[edge["callee"]]["key"]
        if (
                caller[0] == holder and caller[1] == method_name
                and callee[0] == tracked_holder and callee[1] == tracked_method
                and _parameters_match(callee[2], tracked_parameters)
        ):
            exact_matches.append(edge)
    if exact_matches:
        return exact_matches

    lowered_matches = []
    for edge in edges:
        caller = methods[edge["caller"]]["key"]
        callee = methods[edge["callee"]]["key"]
        if (
                caller[0] == holder and caller[1] == method_name
                and callee[0] == tracked_holder and callee[1].startswith(tracked_method)
                and _parameters_match(callee[2], tracked_parameters)
        ):
            lowered_matches.append(edge)
    return lowered_matches


def _frame_holder_and_method(frame: str) -> tuple[str | None, str | None]:
    location_prefix = frame.split("(", 1)[0]
    holder, separator, method_name = location_prefix.rpartition(".")
    if not separator:
        return None, None
    return holder, method_name


def _tracked_holder_method_and_parameters(tracked_api: str) -> tuple[str | None, str | None, tuple[str, ...] | None]:
    prefix = tracked_api.split("(", 1)[0]
    parameters = None
    if "(" in tracked_api and ")" in tracked_api:
        raw_parameters = tracked_api.split("(", 1)[1].split(")", 1)[0].strip()
        if raw_parameters:
            parameters = tuple(_normalize_type_name(parameter.strip()) for parameter in raw_parameters.split(","))
        else:
            parameters = ()

    holder, separator, method_name = prefix.partition("#")
    if separator:
        return holder, method_name, parameters
    holder, separator, method_name = prefix.rpartition(".")
    if not separator:
        return None, None, None
    return holder, method_name, parameters


def _parameters_match(static_parameters: tuple[str, ...], tracked_parameters: tuple[str, ...] | None) -> bool:
    if tracked_parameters is None:
        return True
    if len(static_parameters) != len(tracked_parameters):
        return False
    return all(
        static_parameter == tracked_parameter or _simple_type_name(static_parameter) == _simple_type_name(tracked_parameter)
        for static_parameter, tracked_parameter in zip(static_parameters, tracked_parameters)
    )


def _simple_type_name(type_name: str) -> str:
    array_suffix = ""
    while type_name.endswith("[]"):
        array_suffix += "[]"
        type_name = type_name[:-2]
    return type_name.rsplit(".", 1)[-1] + array_suffix


def _shortest_path_to_edge(adjacency: dict[int, list[dict]], roots: list[int], target_edge: dict) -> tuple[list[int], list[dict]]:
    target_caller = target_edge["caller"]
    queue = deque((root, [root], []) for root in roots)
    seen = set(roots)
    while queue:
        method_id, path, path_edges = queue.popleft()
        if method_id == target_caller:
            return path + [target_edge["callee"]], path_edges + [target_edge]
        for edge in adjacency.get(method_id, []):
            callee = edge["callee"]
            if callee in seen:
                continue
            seen.add(callee)
            queue.append((callee, path + [callee], path_edges + [edge]))
    raise ValueError("Could not find a static root-to-target path")


def _common_prefix_length(static_path: list[int], sample_path: list[tuple[int, int]]) -> int:
    length = 0
    sample_methods = [method_id for method_id, _ in sample_path]
    for static_method, sample_method in zip(static_path, sample_methods):
        if static_method != sample_method:
            break
        length += 1
    return length


def _choose_closest_sample(static_path: list[int], samples: list[dict]) -> dict:
    if not samples:
        raise ValueError("The iprof file did not contain sampling profiles that map to the static call tree")
    return max(
        samples,
        key=lambda sample: (
            _common_prefix_length(static_path, sample["path"]),
            -max(0, len(static_path) - _common_prefix_length(static_path, sample["path"])),
            sample["count"],
        ),
    )


def _edge_between(path_edges: list[dict], index: int) -> dict | None:
    if 0 <= index < len(path_edges):
        return path_edges[index]
    return None


def _target_edge_bci(record: PgoNearCallRecord) -> str:
    if not record.static_path_edges:
        return "unknown"
    return record.static_path_edges[-1]["bci"]


def _format_path(path: list, methods: dict, path_edges: list[dict] | None = None, include_bci: bool = False) -> str:
    lines = []
    for index, item in enumerate(path):
        if include_bci:
            method_id, bci = item
            suffix = " @sample-bci={bci}".format(bci=bci)
        else:
            method_id = item
            edge = _edge_between(path_edges or [], index - 1)
            suffix = " @invoke-bci={bci}".format(bci=edge["bci"]) if edge is not None else ""
        lines.append("{index}. {method}{suffix}".format(
            index=index + 1,
            method=_format_method(method_id, methods),
            suffix=suffix,
        ))
    return "\n".join(lines) if lines else "- None"


def _format_existing_test_stack_to_join(record: PgoNearCallRecord) -> str:
    stack, note, test_entry_index, join_index = _existing_test_stack_to_join(record)
    lines = []
    if note:
        lines.append("- {note}".format(note=note))
    lines.extend(_format_stack_trace_path(stack, test_entry_index, join_index).splitlines())
    return "\n".join(lines) if lines else "- None"


def _observed_branch_after_join(
        record: PgoNearCallRecord,
) -> tuple[tuple[str, str, tuple[str, ...], str], int] | None:
    if not record.sampled_full_path:
        return None
    if record.sampled_join_path_index is not None:
        observed_path_index = record.sampled_join_path_index + 1
    else:
        observed_path_index = record.prefix_length
    if observed_path_index >= len(record.sampled_path_full_indexes):
        return None

    observed_full_index = record.sampled_path_full_indexes[observed_path_index]
    test_start = _existing_test_frame_index(record.sampled_full_path)
    if test_start is not None and observed_full_index < test_start:
        observed_full_index = _first_mapped_index_at_or_after(
            record.sampled_full_path,
            record.sampled_path_full_indexes,
            test_start,
        )
    if observed_full_index is None:
        return None
    return record.sampled_full_path[observed_full_index]


def _existing_test_stack_to_join(
        record: PgoNearCallRecord,
) -> tuple[list[tuple[tuple[str, str, tuple[str, ...], str], int]], str | None, int | None, int | None]:
    if not record.sampled_full_path:
        return [], "No sampled stack frames were available.", None, None

    join_full_index = _sampled_join_full_index(record)
    note = None
    if join_full_index is None:
        join_full_index = len(record.sampled_full_path) - 1
        note = "No sampled call-graph join frame was identified; showing the sampled stack."

    test_start = _existing_test_frame_index(record.sampled_full_path)
    start = test_start if test_start is not None else 0
    if start > join_full_index:
        start = 0
        note = "The identified test frame appears after the PGO/call-graph join point; showing the sampled prefix."
    elif test_start is None:
        note = note or "No existing test frame was identified before the PGO/call-graph join point; showing the sampled prefix."

    stack = record.sampled_full_path[start:join_full_index + 1]
    relative_test_entry = test_start - start if test_start is not None and start <= test_start <= join_full_index else None
    relative_join = len(stack) - 1 if stack else None
    return stack, note, relative_test_entry, relative_join


def _sampled_join_full_index(record: PgoNearCallRecord) -> int | None:
    if record.sampled_join_path_index is not None:
        return record.sampled_path_full_indexes[record.sampled_join_path_index]
    if record.prefix_length > 0 and record.sampled_path_full_indexes:
        return record.sampled_path_full_indexes[record.prefix_length - 1]
    if record.sampled_path_full_indexes:
        return record.sampled_path_full_indexes[0]
    return None


def _existing_test_frame_index(
        sampled_full_path: list[tuple[tuple[str, str, tuple[str, ...], str], int]],
) -> int | None:
    for index, (key, _) in enumerate(sampled_full_path):
        if _looks_like_existing_test_frame(key):
            return index
    return None


def _first_mapped_index_at_or_after(
        sampled_full_path: list[tuple[tuple[str, str, tuple[str, ...], str], int]],
        mapped_indexes: list[int],
        start_index: int,
) -> int | None:
    for mapped_index in mapped_indexes:
        if mapped_index >= start_index and not _looks_like_framework_frame(sampled_full_path[mapped_index][0]):
            return mapped_index
    for mapped_index in mapped_indexes:
        if mapped_index >= start_index:
            return mapped_index
    return None


def _looks_like_existing_test_frame(key: tuple[str, str, tuple[str, ...], str]) -> bool:
    if _looks_like_framework_frame(key):
        return False
    simple_holder = key[0].rsplit(".", 1)[-1]
    return simple_holder.endswith(TEST_TYPE_SUFFIXES)


def _looks_like_framework_frame(key: tuple[str, str, tuple[str, ...], str]) -> bool:
    holder = key[0]
    return holder.startswith(FRAMEWORK_TYPE_PREFIXES)


def _format_stack_trace_path(
        path: list[tuple[tuple[str, str, tuple[str, ...], str], int]],
        test_entry_index: int | None,
        join_index: int | None,
) -> str:
    lines = []
    for index, (key, bci) in enumerate(path):
        markers = []
        if index == test_entry_index:
            markers.append("existing test entry point")
        if index == join_index:
            markers.append("PGO/call-graph join point")
        marker_suffix = ""
        if markers:
            marker_suffix = "  <-- {markers}".format(markers=", ".join(markers))
        lines.append("    at {method} @sample-bci={bci}{marker_suffix}".format(
            method=_format_key(key),
            bci=bci,
            marker_suffix=marker_suffix,
        ))
    return "\n".join(lines) if lines else "- None"


def _format_call_site(call_site: DynamicAccessCallSite) -> str:
    line_suffix = ""
    if call_site.line is not None:
        line_suffix = " (line {line})".format(line=call_site.line)
    return "- [{metadata_type}] {tracked_api} <- {frame}{line_suffix}".format(
        metadata_type=call_site.metadata_type,
        tracked_api=call_site.tracked_api,
        frame=call_site.frame,
        line_suffix=line_suffix,
    )


def _find_file(directory: str, file_name: str) -> str:
    exact = os.path.join(directory, file_name)
    if os.path.isfile(exact):
        return exact

    prefix, extension = os.path.splitext(file_name)
    for current_dir, _, file_names in os.walk(directory):
        for candidate in sorted(file_names):
            if candidate == file_name or (candidate.startswith(prefix + "_") and candidate.endswith(extension)):
                return os.path.join(current_dir, candidate)
    raise FileNotFoundError(os.path.join(directory, file_name))
