# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Static call-graph loading for the deep-method report.

Loads the analysis call-tree CSV dump into an id-indexed graph, attributes
synthetic lambda classes and Native Image factory stubs to their source-level
methods, and marks dispatch call sites that name no honest callee
(§AR-code-coverage-improvement.4.2.1).
"""

from __future__ import annotations

import csv
import os
from dataclasses import dataclass, field

from utility_scripts.code_coverage_model import MethodRef, method_ref_from_call_tree_row
from utility_scripts.code_coverage_profile_inputs import ProfileFormatError

#: Marker in the class name the image generator gives a lambda implementation.
SYNTHETIC_LAMBDA_CLASS_MARKER = "$$Lambda"

#: Native Image owner for generated allocation methods that replace constructors.
FACTORY_METHOD_HOLDER = "com.oracle.svm.core.code.FactoryMethodHolder"

#: Method-name prefixes the compiler owns; no test can name one of these
#: (§AR-code-coverage-improvement.4.2.1).
SYNTHETIC_METHOD_PREFIXES = ("lambda$", "access$")


@dataclass
class CallGraph:
    methods: dict[int, MethodRef] = field(default_factory=dict)
    key_to_id: dict[str, int] = field(default_factory=dict)
    loose_to_ids: dict[str, list[int]] = field(default_factory=dict)
    adjacency: dict[int, list[dict]] = field(default_factory=dict)
    reverse_adjacency: dict[int, list[dict]] = field(default_factory=dict)
    #: Invoke id -> every implementation resolved at that exact call site.
    invoke_fan_out: dict[int, list[int]] = field(default_factory=dict)
    #: Native Image factory node -> verified source-level constructor.
    path_aliases: dict[int, MethodRef] = field(default_factory=dict)
    #: Synthetic node -> the method whose source captured that closure.
    creator_of: dict[int, int] = field(default_factory=dict)
    #: Creating method -> the lambda bodies the compiler extracted from it.
    closures_of: dict[int, list[int]] = field(default_factory=dict)
    #: Call sites marked dispatch because their declared target is foreign.
    foreign_dispatch_sites: int = 0
    #: Edges those sites contributed, all of them marked dispatch.
    foreign_dispatch_edges: int = 0
    #: Virtual sites the dump gave no declared target for, so left routing.
    unjudged_dispatch_sites: int = 0


def _find_call_tree_files(directory: str) -> tuple[str, str, str]:
    """Select one coherent methods/invokes/targets CSV suffix atomically."""
    kinds: tuple[str, ...] = ("methods", "invokes", "targets")
    candidates: dict[tuple[str, str], dict[str, str]] = {}
    for current_dir, dir_names, file_names in os.walk(directory):
        dir_names.sort()
        for file_name in sorted(file_names):
            for kind in kinds:
                prefix: str = f"call_tree_{kind}"
                suffix: str | None = None
                if file_name == f"{prefix}.csv":
                    suffix = ""
                elif file_name.startswith(prefix + "_") and file_name.endswith(".csv"):
                    suffix = file_name[len(prefix) + 1:-4]
                if suffix is None:
                    continue
                candidates.setdefault((current_dir, suffix), {})[kind] = os.path.join(
                    current_dir, file_name
                )
    complete: list[tuple[str, str, dict[str, str]]] = [
        (suffix, current_dir, files)
        for (current_dir, suffix), files in candidates.items()
        if all(kind in files for kind in kinds)
    ]
    if not complete:
        raise FileNotFoundError(
            os.path.join(directory, "call_tree_{methods,invokes,targets}_*.csv")
        )
    _, _, selected = max(complete, key=lambda item: (item[0], item[1]))
    return selected["methods"], selected["invokes"], selected["targets"]


def _read_csv_by_id(path: str) -> dict[int, dict]:
    with open(path, encoding="utf-8", newline="") as csv_file:
        return {int(row["Id"]): row for row in csv.DictReader(csv_file)}


def _is_synthetic_lambda_class(owner: str) -> bool:
    return SYNTHETIC_LAMBDA_CLASS_MARKER in owner


def _is_lambda_body(ref: MethodRef) -> bool:
    return ref.name.startswith("lambda$")


def is_synthetic_method(ref: MethodRef) -> bool:
    """Whether the compiler, not a person, owns this method name."""
    return ref.name.startswith(SYNTHETIC_METHOD_PREFIXES)


def _creator_by_name(
        ref: MethodRef,
        by_owner_name: dict[tuple[str, str], list[int]],
) -> int | None:
    """Resolve `lambda$enclosing$0` by name, for bodies with no generated class.

    The body name carries the enclosing method's name without its parameter
    types, so this answers only when that name identifies exactly one method
    (§AR-code-coverage-improvement.4.2.1).
    """
    parts: list[str] = ref.name.split("$")
    if len(parts) < 3 or not parts[1]:
        return None
    enclosing: str = {"new": "<init>", "static": "<clinit>"}.get(parts[1], parts[1])
    candidates: list[int] = by_owner_name.get((ref.owner, enclosing), [])
    return candidates[0] if len(candidates) == 1 else None


def _index_synthetic_lambdas(graph: CallGraph) -> None:
    """Map every synthetic lambda node onto the method that creates it.

    The generated class ties the enclosing method to the extracted body: its
    constructor is called where the closure is captured, and its interface
    method calls the body. Both hops are exact, so neither the overload
    ambiguity of the body name nor the meaningless generated class name reaches
    a prompt (§AR-code-coverage-improvement.4.2.1).
    """
    by_owner: dict[str, list[int]] = {}
    by_owner_name: dict[tuple[str, str], list[int]] = {}
    for static_id, ref in graph.methods.items():
        by_owner_name.setdefault((ref.owner, ref.name), []).append(static_id)
        if _is_synthetic_lambda_class(ref.owner):
            by_owner.setdefault(ref.owner, []).append(static_id)

    for _, member_ids in sorted(by_owner.items()):
        creators: set[int] = set()
        bodies: set[int] = set()
        for member_id in member_ids:
            member: MethodRef = graph.methods[member_id]
            if member.name == "<init>":
                creators.update(
                    edge["caller"]
                    for edge in graph.reverse_adjacency.get(member_id, [])
                    if not _is_synthetic_lambda_class(graph.methods[edge["caller"]].owner)
                )
            elif member.name != "<clinit>":
                bodies.update(
                    edge["callee"]
                    for edge in graph.adjacency.get(member_id, [])
                    if _is_lambda_body(graph.methods[edge["callee"]])
                )
        # Two capture sites sharing one generated class would make attribution a
        # guess; leaving them unmapped keeps the raw names instead of a wrong one.
        if len(creators) != 1:
            continue
        creator_id: int = creators.pop()
        for node_id in (*member_ids, *bodies):
            graph.creator_of[node_id] = creator_id

    for static_id in sorted(graph.methods):
        ref = graph.methods[static_id]
        if static_id in graph.creator_of or not _is_lambda_body(ref):
            continue
        creator_id_or_none: int | None = _creator_by_name(ref, by_owner_name)
        if creator_id_or_none is not None:
            graph.creator_of[static_id] = creator_id_or_none

    for node_id, creator_id in graph.creator_of.items():
        if _is_lambda_body(graph.methods[node_id]):
            graph.closures_of.setdefault(creator_id, []).append(node_id)
    for body_ids in graph.closures_of.values():
        body_ids.sort(key=lambda body_id: graph.methods[body_id].canonical_id)


def _index_factory_stubs(
        graph: CallGraph,
        library_methods: set[str] | None,
) -> None:
    """Map verified Native Image allocation stubs to library constructors.

    The return type and parameters identify the constructor represented by a
    factory stub. Requiring that exact constructor in the bytecode inventory
    avoids inventing source-level constructors for other generated factories
    (§AR-code-coverage-improvement.4.2.1).
    """
    if library_methods is None:
        return
    for static_id, ref in graph.methods.items():
        if ref.owner != FACTORY_METHOD_HOLDER:
            continue
        constructor = MethodRef(ref.return_type, "<init>", ref.params, "void")
        if constructor.canonical_id in library_methods:
            graph.path_aliases[static_id] = constructor


def _mark_dispatch_edges(
        graph: CallGraph,
        site_edges: dict[int, list[dict]],
        site_declared: dict[int, int],
        owners: set[str] | None,
) -> None:
    """Mark call sites that do not say which implementation they reach.

    A virtual site is marked when either holds. The site admits a generated
    lambda class, so it invokes a functional interface whose object was captured
    elsewhere and handed in (§AR-code-coverage-improvement.4.2.1). Or its
    statically declared target belongs to a foreign type — `Iterator.hasNext`,
    `Closeable.close`, `Object.equals` — where class-hierarchy analysis answers
    with every implementation in the image and none of them is what runs.

    The whole site is marked either way, real implementations included: they
    share the one call site and the one missing fact. A site declaring a library
    type is left alone, so `ProcessorNode.process` keeps its 34 implementations.

    A dump that carries no declared target for a site leaves the second rule
    unjudged, and the site keeps its edges rather than losing them all silently.
    """
    for invoke_id, edges in site_edges.items():
        if any(edge["is_direct"] == "true" for edge in edges):
            continue
        lambda_site: bool = any(
            _is_synthetic_lambda_class(graph.methods[edge["callee"]].owner)
            for edge in edges
        )
        foreign_site: bool = False
        if owners is not None and not lambda_site:
            declared: MethodRef | None = graph.methods.get(site_declared.get(invoke_id, -1))
            if declared is None:
                graph.unjudged_dispatch_sites += 1
            else:
                foreign_site = declared.owner not in owners
        if not lambda_site and not foreign_site:
            continue
        if foreign_site:
            graph.foreign_dispatch_sites += 1
            graph.foreign_dispatch_edges += len(edges)
        for edge in edges:
            edge["kind"] = "dispatch"


def _add_creation_edges(graph: CallGraph) -> None:
    """Link each capturing method to the bodies the compiler extracted from it.

    The dump links a body only to its generated class, so without this edge the
    single honest route to a closure body does not exist
    (§AR-code-coverage-improvement.4.2.1).
    """
    for creator_id, body_ids in sorted(graph.closures_of.items()):
        existing: set[int] = {
            edge["callee"] for edge in graph.adjacency.get(creator_id, [])
        }
        for body_id in body_ids:
            if body_id in existing:
                continue
            edge: dict = {
                "caller": creator_id,
                "callee": body_id,
                "bci": "",
                "is_direct": "creation",
                "kind": "creation",
            }
            graph.adjacency.setdefault(creator_id, []).append(edge)
            graph.reverse_adjacency.setdefault(body_id, []).append(edge)


def _source_line_for_bci(
        caller: MethodRef,
        raw_bci: str,
        line_numbers: dict[str, tuple[tuple[int, int], ...]],
) -> int | None:
    """Map one native call-tree invoke BCI through the caller's class table."""
    try:
        invoke_bci: int = int(raw_bci)
    except ValueError:
        return None
    source_line: int | None = None
    for start_bci, line in line_numbers.get(caller.canonical_id, ()):
        if start_bci > invoke_bci:
            break
        source_line = line
    return source_line


def _load_call_graph(
        reports_dir: str,
        owners: set[str] | None,
        line_numbers: dict[str, tuple[tuple[int, int], ...]],
        library_methods: set[str] | None,
) -> CallGraph:
    """Load the analysis call-tree CSV dump into an id-indexed call graph."""
    methods_path, invokes_path, targets_path = _find_call_tree_files(reports_dir)
    methods_rows = _read_csv_by_id(methods_path)
    invokes_rows = _read_csv_by_id(invokes_path)
    with open(targets_path, encoding="utf-8", newline="") as csv_file:
        target_rows = list(csv.DictReader(csv_file))

    graph = CallGraph()
    for method_id, row in methods_rows.items():
        ref = method_ref_from_call_tree_row(row)
        if ref is None:
            continue
        graph.methods[method_id] = ref
        graph.key_to_id.setdefault(ref.canonical_id, method_id)
        graph.loose_to_ids.setdefault(ref.loose_key, []).append(method_id)

    site_edges: dict[int, list[dict]] = {}
    site_declared: dict[int, int] = {}
    for target_row in target_rows:
        invoke_id = int(target_row["InvokeId"])
        invoke = invokes_rows.get(invoke_id)
        if invoke is None:
            continue
        caller_id = int(invoke["MethodId"])
        declared: str = invoke.get("TargetId", "")
        if declared:
            site_declared.setdefault(invoke_id, int(declared))
        callee_id = int(target_row["TargetId"])
        if caller_id not in graph.methods or callee_id not in graph.methods:
            continue
        graph.invoke_fan_out.setdefault(invoke_id, []).append(callee_id)
        raw_bci: str = invoke.get("BytecodeIndexes", "")
        edge = {
            "caller": caller_id,
            "callee": callee_id,
            "bci": raw_bci,
            "is_direct": invoke.get("IsDirect", ""),
            "kind": "call",
            "invoke_id": invoke_id,
        }
        source_line: int | None = _source_line_for_bci(
            graph.methods[caller_id], raw_bci, line_numbers
        )
        if source_line is not None:
            edge["source_line"] = source_line
        site_edges.setdefault(invoke_id, []).append(edge)
        graph.adjacency.setdefault(caller_id, []).append(edge)
        graph.reverse_adjacency.setdefault(callee_id, []).append(edge)

    _index_synthetic_lambdas(graph)
    _index_factory_stubs(graph, library_methods)
    _mark_dispatch_edges(graph, site_edges, site_declared, owners)
    _add_creation_edges(graph)

    for method_ids in graph.loose_to_ids.values():
        method_ids.sort(key=lambda method_id: graph.methods[method_id].canonical_id)
    for method_ids in graph.invoke_fan_out.values():
        method_ids.sort(key=lambda method_id: graph.methods[method_id].canonical_id)
    for edges in graph.adjacency.values():
        edges.sort(key=lambda edge: (
            graph.methods[edge["callee"]].canonical_id,
            str(edge["bci"]),
        ))
    for edges in graph.reverse_adjacency.values():
        edges.sort(key=lambda edge: (
            graph.methods[edge["caller"]].canonical_id,
            str(edge["bci"]),
        ))

    return graph


def load_call_graph(
        reports_dir: str,
        owners: set[str] | None = None,
        line_numbers: dict[str, tuple[tuple[int, int], ...]] | None = None,
        library_methods: set[str] | None = None,
) -> CallGraph:
    """Load one coherent call-tree triplet, failing closed on bad input."""
    try:
        return _load_call_graph(
            reports_dir, owners, line_numbers or {}, library_methods
        )
    except (OSError, csv.Error, KeyError, TypeError, ValueError) as error:
        raise ProfileFormatError(f"Cannot load call-tree CSVs from '{reports_dir}'.") from error


def resolve_graph_id(graph: CallGraph, ref: MethodRef) -> int | None:
    """Resolve an exact graph method, or an unambiguous loose guidance match."""
    static_id: int | None = graph.key_to_id.get(ref.canonical_id)
    if static_id is not None:
        return static_id
    loose_ids: list[int] = graph.loose_to_ids.get(ref.loose_key, [])
    return loose_ids[0] if len(loose_ids) == 1 else None


def translated_ref(static_id: int, graph: CallGraph) -> MethodRef:
    """Return the source-level identity used to compare path steps.

    §AR-code-coverage-improvement.4.2.1
    """
    creator_id: int = graph.creator_of.get(static_id, static_id)
    return graph.path_aliases.get(creator_id, graph.methods[creator_id])


def format_static_id(static_id: int, graph: CallGraph) -> str:
    ref = graph.methods.get(static_id)
    return ref.canonical_id if ref is not None else f"#{static_id}"
