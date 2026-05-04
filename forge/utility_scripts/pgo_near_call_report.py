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


@dataclass(frozen=True)
class PgoNearCallRecord:
    call_site: DynamicAccessCallSite
    static_path: list[int]
    static_path_edges: list[dict]
    sampled_path: list[tuple[int, int]]
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
        "Closest sampled runtime path:",
        _format_path(best.sampled_path, best.methods, include_bci=True),
        "",
        "Static path to the uncovered call:",
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
    observed_next = best.sampled_path[best.prefix_length][0] if best.prefix_length < len(best.sampled_path) else None
    static_next_edge = _edge_between(best.static_path_edges, best.prefix_length - 1)
    if divergence is not None:
        lines.append("- Shared prefix ends at: {method}".format(method=_format_method(divergence, best.methods)))
    else:
        lines.append("- No shared static prefix was sampled.")
    if observed_next is not None:
        lines.append("- Current tests then execute: {method}".format(method=_format_method(observed_next, best.methods)))
    else:
        lines.append("- Current sampled path ended at the divergence point.")
    if static_next is not None:
        suffix = ""
        if static_next_edge is not None:
            suffix = " @invoke-bci={bci}".format(bci=static_next_edge["bci"])
        lines.append("- Tests need to drive: {method}{suffix}".format(
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

    methods, edges, adjacency, roots = _load_static_call_tree(reports_dir)
    iprof, iprof_methods = _load_iprof(_find_file(report_dir, "native-test.iprof"))
    static_key_to_id = {method["key"]: method_id for method_id, method in methods.items()}
    samples = _sample_paths(iprof, iprof_methods, static_key_to_id)

    records: list[PgoNearCallRecord] = []
    for call_site in call_sites:
        target_edges = _find_static_edges_to_call_site(edges, methods, call_site)
        if not target_edges:
            continue
        for target_edge in target_edges:
            static_path, static_path_edges = _shortest_path_to_edge(adjacency, roots, target_edge)
            closest_sample = _choose_closest_sample(static_path, samples)
            records.append(PgoNearCallRecord(
                call_site=call_site,
                static_path=static_path,
                static_path_edges=static_path_edges,
                sampled_path=closest_sample["path"],
                sample_count=closest_sample["count"],
                prefix_length=_common_prefix_length(static_path, closest_sample["path"]),
                methods=methods,
            ))
    records.sort(key=lambda record: (record.prefix_length, -record.depth_remaining, record.sample_count), reverse=True)
    return records


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


def _load_static_call_tree(reports_dir: str) -> tuple[dict, list[dict], dict[int, list[dict]], list[int]]:
    methods = _read_csv_by_id(_find_file(reports_dir, "call_tree_methods.csv"))
    invokes = _read_csv_by_id(_find_file(reports_dir, "call_tree_invokes.csv"))
    targets_path = _find_file(reports_dir, "call_tree_targets.csv")

    with open(targets_path, encoding="utf-8", newline="") as csv_file:
        targets = list(csv.DictReader(csv_file))

    for method in methods.values():
        method["key"] = _method_key(method)

    edges = []
    adjacency: dict[int, list[dict]] = {}
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

    roots = [method_id for method_id, method in methods.items() if method["IsEntryPoint"] == "true"]
    return methods, edges, adjacency, roots


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
        for method_id, bci in _parse_context(profile["ctx"]):
            key = iprof_methods.get(method_id)
            static_id = static_key_to_id.get(key)
            if static_id is not None and (not mapped_path or mapped_path[-1][0] != static_id):
                mapped_path.append((static_id, bci))
        if mapped_path:
            paths.append({
                "path": mapped_path,
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
