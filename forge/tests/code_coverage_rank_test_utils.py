# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared fixture builders for the API-rank and eligibility test files."""

import os
import shutil

EXTRACTOR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "utility_scripts", "java", "CallGraphExtractor.java",
)


def _java_tool(name: str) -> str | None:
    home = os.environ.get("GRAALVM_HOME") or os.environ.get("JAVA_HOME")
    if home:
        candidate = os.path.join(home, "bin", name)
        if os.path.isfile(candidate):
            return candidate
    return shutil.which(name)


def _graph(directory: str, methods: list[tuple[str, bool]], edges: list[tuple[str, str]]) -> str:
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, "methods.csv"), "w", encoding="utf-8") as handle:
        handle.write("id,hasCode,isPublicApi\n")
        for method_id, has_code in methods:
            handle.write(f'"{method_id}",{str(has_code).lower()},false\n')
    with open(os.path.join(directory, "edges.csv"), "w", encoding="utf-8") as handle:
        handle.write("caller,callee\n")
        for caller, callee in edges:
            handle.write(f'"{caller}","{callee}"\n')
    return directory


def _typed_graph(
        directory: str,
        methods: list[tuple[str, bool, bool, bool]],
        edges: list[tuple[str, str]],
        types: list[tuple[str, str, str]],
) -> str:
    """Write a graph that carries eligibility inputs.

    `methods` rows are (id, hasCode, isPublicApi, isStatic); `types` rows are
    (name, super, semicolon-joined interfaces).
    """
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, "methods.csv"), "w", encoding="utf-8") as handle:
        handle.write("id,hasCode,isPublicApi,isStatic\n")
        for method_id, has_code, is_public, is_static in methods:
            handle.write(f'"{method_id}",{str(has_code).lower()},'
                         f'{str(is_public).lower()},{str(is_static).lower()}\n')
    with open(os.path.join(directory, "edges.csv"), "w", encoding="utf-8") as handle:
        handle.write("caller,callee\n")
        for caller, callee in edges:
            handle.write(f'"{caller}","{callee}"\n')
    with open(os.path.join(directory, "types.csv"), "w", encoding="utf-8") as handle:
        handle.write("name,isPublic,super,interfaces\n")
        for name, super_name, interfaces in types:
            handle.write(f'"{name}",true,"{super_name}","{interfaces}"\n')
    return directory


def _jacoco(path: str, methods: list[tuple[str, str, str, bool]]) -> str:
    """Write a minimal JaCoCo XML report from (owner, name, desc, covered)."""
    lines: list[str] = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>', '<report name="rank">']
    owners: dict[str, list[tuple[str, str, bool]]] = {}
    for owner, name, desc, covered in methods:
        owners.setdefault(owner, []).append((name, desc, covered))
    for owner, entries in owners.items():
        package, _, simple = owner.rpartition("/")
        lines.append(f'  <package name="{package}">')
        lines.append(f'    <class name="{owner}" sourcefilename="{simple}.java">')
        for name, desc, covered in entries:
            hit, miss = (1, 0) if covered else (0, 1)
            lines.append(f'      <method name="{name}" desc="{desc}" line="1">')
            lines.append(f'        <counter type="METHOD" missed="{miss}" covered="{hit}"/>')
            lines.append("      </method>")
        lines.append("    </class>")
        lines.append("  </package>")
    lines.append("</report>")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    return path
