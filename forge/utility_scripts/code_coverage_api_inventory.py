# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Deterministic public-API inventory for the code coverage improvement workflow
(§WF-code-coverage-improvement.3.1, §WF-code-coverage-improvement-architecture).

It enumerates the public, user-callable method and constructor surface of a
resolved library artifact and emits compact JSON and Markdown reports. The
canonical target `id` carries full identity (`owner#name(params):ret`) so the
JaCoCo validator can classify public entries exactly. The deep-path analyzer
uses those identities as public navigation boundaries and removes them from its
internal-method target set; redundant split fields are avoided.

The inventory is derived from the library jar via `javap -public -s`, so it is repeatable
and needs no network access or library execution. Generic type arguments are
erased, varargs are normalized to arrays, and fields are excluded so target ids line up with the raw
types used by the analysis call tree and the sampled profile.

Usage:
  python3 utility_scripts/code_coverage_api_inventory.py \
    --coordinate group:artifact:version \
    --library-jar path/to/library.jar [--library-jar ...] \
    --output-dir runtime/code-coverage/api-inventory [--include-package com.example]
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

from utility_scripts.code_coverage_model import (
    MethodRef,
    normalize_type_name,
    parse_jvm_descriptor,
)

_MODIFIERS = {
    "public", "protected", "private", "static", "final", "abstract",
    "synchronized", "native", "default", "volatile", "transient", "strictfp",
}


class ApiInventoryError(RuntimeError):
    """Raised when the public API inventory cannot be generated completely."""


@dataclass
class ApiTarget:
    method_ref: MethodRef | None
    target_id: str
    kind: str
    source_path: str
    behavior_hint: str
    is_static: bool = False


@dataclass
class ClassInfo:
    owner: str
    kind: str
    source_file: str
    source_path: str = ""
    targets: list[ApiTarget] = field(default_factory=list)


def _strip_generics(text: str) -> str:
    """Erase `<...>` generic arguments at any nesting depth."""
    result: list[str] = []
    depth = 0
    for char in text:
        if char == "<":
            depth += 1
        elif char == ">":
            depth = max(0, depth - 1)
        elif depth == 0:
            result.append(char)
    return "".join(result)


def _normalize_param(raw: str) -> str:
    raw = raw.strip()
    if raw.endswith("..."):
        raw = raw[:-3] + "[]"
    return normalize_type_name(raw)


def _split_params(param_text: str) -> tuple[str, ...]:
    inner = param_text.strip()
    if not inner:
        return ()
    parts: list[str] = []
    depth = 0
    current = ""
    for char in inner:
        if char == "<":
            depth += 1
        elif char == ">":
            depth -= 1
        if char == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += char
    if current.strip():
        parts.append(current)
    return tuple(_normalize_param(_strip_generics(part)) for part in parts)


def _behavior_hint(name: str, kind: str) -> str:
    lowered = name.lower()
    if kind == "constructor":
        return "Constructs an instance through a public constructor."
    if kind == "enumConstant":
        return "Public enum constant."
    if name in ("values", "valueOf"):
        return "Generated enum accessor."
    if lowered.startswith(("get", "is", "has")):
        return "Public accessor."
    if lowered.startswith(("set", "with")):
        return "Public mutator/configuration method."
    if lowered.startswith(("build", "create", "of", "new", "from")):
        return "Public factory/builder method."
    if "parse" in lowered or "read" in lowered or "decode" in lowered:
        return "Public parsing/decoding method."
    if "write" in lowered or "serialize" in lowered or "encode" in lowered or "format" in lowered:
        return "Public serialization/formatting method."
    return "Public user-callable method."


def parse_javap(text: str, source_root: str = "") -> list[ClassInfo]:
    """Parse `javap -public` multi-class output into class/target records."""
    classes: list[ClassInfo] = []
    current: ClassInfo | None = None
    pending_source_file = ""
    pending_method: ApiTarget | None = None

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        descriptor = re.match(r"^descriptor:\s*(?P<value>\S+)$", line)
        if descriptor:
            if pending_method is not None and pending_method.method_ref is not None:
                params: tuple[str, ...]
                return_type: str
                params, return_type = parse_jvm_descriptor(descriptor.group("value"))
                ref = MethodRef(
                    owner=pending_method.method_ref.owner,
                    name=pending_method.method_ref.name,
                    params=params,
                    return_type=return_type,
                )
                pending_method.method_ref = ref
                pending_method.target_id = ref.canonical_id
            pending_method = None
            continue
        compiled = re.match(r'^Compiled from "(?P<file>.+)"$', line)
        if compiled:
            pending_source_file = compiled.group("file")
            pending_method = None
            continue

        decl = re.match(
            r"^(?P<mods>(?:\w+\s+)*)(?P<kind>class|interface|enum|record)\s+(?P<owner>[\w.$]+)",
            line,
        )
        if decl and "(" not in line.split("{")[0]:
            mods = set(decl.group("mods").split())
            if "public" not in mods:
                current = None
                pending_method = None
                continue
            owner = decl.group("owner")
            package_path = owner.rsplit(".", 1)[0].replace(".", "/") if "." in owner else ""
            source_file = pending_source_file or f"{owner.rsplit('.', 1)[-1]}.java"
            source_path = os.path.join(source_root, package_path, source_file) if package_path else source_file
            # javap renders enums as `final class ... extends java.lang.Enum`,
            # never with the `enum` keyword, so detect them by the supertype.
            class_kind = "enum" if "extends java.lang.Enum" in line else decl.group("kind")
            current = ClassInfo(owner=owner, kind=class_kind, source_file=source_file)
            current.source_path = source_path.replace(os.sep, "/")
            classes.append(current)
            pending_source_file = ""
            pending_method = None
            continue

        if current is None:
            continue
        if not line.endswith(";"):
            continue
        pending_method = None
        member = _parse_member(line[:-1], current)
        if member is not None:
            current.targets.append(member)
            pending_method = member if member.method_ref is not None else None
    return classes


def _parse_member(body: str, owner_class: ClassInfo) -> ApiTarget | None:
    erased = _strip_generics(body).strip()
    tokens = erased.split()
    is_static = "static" in tokens
    # Drop leading modifier tokens.
    index = 0
    while index < len(tokens) and tokens[index] in _MODIFIERS:
        index += 1
    remainder = " ".join(tokens[index:]).strip()
    if not remainder:
        return None

    owner = owner_class.owner
    if "(" in remainder:
        head, _, after = remainder.partition("(")
        params = _split_params(after.rsplit(")", 1)[0])
        head = head.strip()
        head_tokens = head.split()
        if len(head_tokens) == 1 and head_tokens[0] == owner:
            ref = MethodRef(owner=owner, name="<init>", params=params, return_type="void")
            return ApiTarget(ref, ref.canonical_id, "constructor", owner_class.source_path,
                             _behavior_hint("<init>", "constructor"), is_static)
        if len(head_tokens) < 2:
            return None
        name = head_tokens[-1]
        return_type = normalize_type_name(" ".join(head_tokens[:-1]))
        ref = MethodRef(owner=owner, name=name, params=params, return_type=return_type)
        kind = "staticMethod" if is_static else "method"
        return ApiTarget(ref, ref.canonical_id, kind, owner_class.source_path,
                         _behavior_hint(name, kind), is_static)

    # §WF-code-coverage-improvement.3.1: fields are not callable entry targets.
    return None


def enumerate_public_classes(jar_path: str, include_package: str | None) -> list[str]:
    """Return candidate binary class names from a jar, skipping synthetic ones."""
    names: list[str] = []
    with zipfile.ZipFile(jar_path) as jar:
        for entry in jar.namelist():
            if not entry.endswith(".class"):
                continue
            binary = entry[:-len(".class")].replace("/", ".")
            simple = binary.rsplit(".", 1)[-1]
            if simple in ("module-info", "package-info"):
                continue
            # Skip anonymous classes (Outer$1); keep named nested classes.
            if re.search(r"\$\d", binary):
                continue
            if include_package and not (binary == include_package or binary.startswith(include_package + ".")):
                continue
            names.append(binary)
    return sorted(names)


def run_javap(jar_paths: list[str], class_names: list[str]) -> str:
    """Run `javap -public -s` so inventory ids use erased JVM descriptors."""
    javap = _resolve_tool("javap")
    classpath = os.pathsep.join(jar_paths)
    output: list[str] = []
    # Batch to keep argument lists bounded on large libraries.
    for start in range(0, len(class_names), 200):
        batch = class_names[start:start + 200]
        result = subprocess.run(
            [javap, "-public", "-s", "-classpath", classpath, *batch],
            check=False, capture_output=True, text=True,
        )
        if result.returncode != 0:
            stderr: str = (result.stderr or "").strip()[-2000:]
            first_class: str = batch[0] if batch else "<empty batch>"
            raise ApiInventoryError(
                f"javap failed for the batch starting with '{first_class}': "
                f"{stderr or 'no diagnostic output'}"
            )
        output.append(result.stdout)
    return "\n".join(output)


def _resolve_tool(tool: str) -> str:
    for env_var in ("GRAALVM_HOME", "JAVA_HOME"):
        home = os.environ.get(env_var)
        if home:
            candidate = os.path.join(home, "bin", tool)
            if os.path.isfile(candidate):
                return candidate
    resolved = shutil.which(tool)
    if resolved is None:
        print(f"ERROR: {tool} not found on JAVA_HOME/GRAALVM_HOME/PATH.", file=sys.stderr)
        raise SystemExit(1)
    return resolved


def build_inventory(coordinate: str, classes: list[ClassInfo]) -> dict:
    targets: list[dict] = []
    for class_info in classes:
        for target in class_info.targets:
            entry = {
                "id": target.target_id,
                "sourcePath": target.source_path,
                "kind": target.kind,
                "status": "pending",
                "behaviorHint": target.behavior_hint,
                "evidence": ["bytecode"],
            }
            targets.append(entry)
    targets.sort(key=lambda item: item["id"])
    return {"coordinate": coordinate, "targets": targets}


def write_markdown(inventory: dict, md_path: str) -> None:
    targets = inventory["targets"]
    by_kind: dict[str, int] = {}
    for target in targets:
        by_kind[target["kind"]] = by_kind.get(target["kind"], 0) + 1
    lines = [
        f"# API inventory — {inventory['coordinate']}",
        "",
        f"Total public user-callable targets: {len(targets)}",
        "",
        "| Kind | Count |",
        "|---|---|",
    ]
    for kind in sorted(by_kind):
        lines.append(f"| {kind} | {by_kind[kind]} |")
    lines += ["", "## Targets", ""]
    for target in targets:
        lines.append(f"- `{target['id']}` ({target['kind']}) — {target['behaviorHint']}")
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def generate_inventory(
        coordinate: str,
        jar_paths: list[str],
        output_dir: str,
        include_package: str | None,
        source_root: str,
) -> dict:
    class_names: list[str] = []
    for jar_path in jar_paths:
        try:
            class_names.extend(enumerate_public_classes(jar_path, include_package))
        except (OSError, zipfile.BadZipFile) as error:
            raise ApiInventoryError(f"Cannot read library jar '{jar_path}': {error}") from error
    class_names = sorted(set(class_names))
    javap_output = run_javap(jar_paths, class_names) if class_names else ""
    classes = parse_javap(javap_output, source_root=source_root)
    inventory = build_inventory(coordinate, classes)

    os.makedirs(output_dir, exist_ok=True)
    json_path = os.path.join(output_dir, "api-inventory.json")
    md_path = os.path.join(output_dir, "api-inventory.md")
    with open(json_path, "w", encoding="utf-8") as json_file:
        json.dump(inventory, json_file, indent=2)
        json_file.write("\n")
    write_markdown(inventory, md_path)
    return inventory


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a public-API inventory for a library.")
    parser.add_argument("--coordinate", required=True, help="group:artifact:version.")
    parser.add_argument("--library-jar", action="append", required=True, help="Library jar path (repeatable).")
    parser.add_argument("--output-dir", required=True, help="Directory for inventory artifacts.")
    parser.add_argument("--include-package", default=None, help="Restrict to this package prefix.")
    parser.add_argument("--source-root", default="", help="Prefix for emitted sourcePath values.")
    args = parser.parse_args()

    try:
        inventory = generate_inventory(
            coordinate=args.coordinate,
            jar_paths=args.library_jar,
            output_dir=args.output_dir,
            include_package=args.include_package,
            source_root=args.source_root,
        )
    except ApiInventoryError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2) from error
    print(f"API inventory: {len(inventory['targets'])} public targets for {args.coordinate}.")


if __name__ == "__main__":
    main()
