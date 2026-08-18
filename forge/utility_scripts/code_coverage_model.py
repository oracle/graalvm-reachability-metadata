# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Shared method-identity model for the code coverage improvement helpers
(§WF-code-coverage-improvement-architecture).

The three coverage helpers each speak a different dialect of "which method is
this": the API inventory uses `owner#name(params):ret`, the GraalVM analysis
call-tree CSV dump uses `Type`/`Name`/`Parameters`/`Return` columns, and the
sampled PGO `.iprof` profile encodes a method as integer ids into a
`types`/`methods` symbol table. This module normalizes all three onto one
canonical id so coverage evidence can be joined against API targets.
"""

from __future__ import annotations

from dataclasses import dataclass
import re

# JVM primitive type descriptors, used when an `.iprof` type table or a JaCoCo
# descriptor encodes an array element type in internal form (for example `[B`).
_PRIMITIVE_DESCRIPTORS: dict[str, str] = {
    "B": "byte",
    "C": "char",
    "D": "double",
    "F": "float",
    "I": "int",
    "J": "long",
    "S": "short",
    "Z": "boolean",
    "V": "void",
}


def normalize_type_name(name: str) -> str:
    """Normalize a type name from any source onto its Java source form.

    Handles internal array descriptors (`[B` -> `byte[]`,
    `[Ljava.lang.String;` -> `java.lang.String[]`) and slash-separated binary
    names (`java/lang/String` -> `java.lang.String`). Plain source names pass
    through unchanged so call-tree and inventory forms line up with profile
    forms.
    """
    if not name:
        return name
    dimensions = 0
    while name.startswith("["):
        dimensions += 1
        name = name[1:]
    if dimensions:
        if name.startswith("L") and name.endswith(";"):
            base = name[1:-1].replace("/", ".")
        elif len(name) == 1 and name in _PRIMITIVE_DESCRIPTORS:
            base = _PRIMITIVE_DESCRIPTORS[name]
        else:
            base = name.replace("/", ".")
        return base + "[]" * dimensions
    return name.replace("/", ".")


@dataclass(frozen=True)
class MethodRef:
    """One method identity, normalized to Java source form."""

    owner: str
    name: str
    params: tuple[str, ...]
    return_type: str | None = None

    @property
    def canonical_id(self) -> str:
        """`owner#name(p1,p2):ret` — the join key carrying full identity."""
        params = ",".join(self.params)
        suffix = f":{self.return_type}" if self.return_type else ""
        return f"{self.owner}#{self.name}({params}){suffix}"

    @property
    def loose_key(self) -> str:
        """`owner#name/arity` — fallback join key when return/param forms differ."""
        return f"{self.owner}#{self.name}/{len(self.params)}"

    @property
    def owner_class_simple(self) -> str:
        """Outer class name without package, used for grouping discovery targets."""
        simple = self.owner.rsplit(".", 1)[-1]
        return simple.split("$", 1)[0]


def _split_params(param_text: str) -> tuple[str, ...]:
    inner = param_text.strip()
    if not inner:
        return ()
    return tuple(normalize_type_name(part.strip()) for part in inner.split(",") if part.strip())


def parse_inventory_id(target_id: str) -> MethodRef | None:
    """Parse one exact canonical API inventory method id."""
    match = re.match(
        r"^(?P<owner>[^#]+)#(?P<name>[^(]+)\((?P<params>[^)]*)\):(?P<ret>.+)$",
        target_id,
    )
    if not match:
        return None
    ref = MethodRef(
        owner=match.group("owner"),
        name=match.group("name"),
        params=_split_params(match.group("params")),
        return_type=normalize_type_name(match.group("ret")),
    )
    return ref if ref.canonical_id == target_id else None


def method_ref_from_call_tree_row(row: dict) -> MethodRef | None:
    """Build a MethodRef from one `call_tree_methods_*.csv` row.

    The analysis call-tree CSV dump (`-H:PrintAnalysisCallTreeType=CSV`) writes
    `Type`/`Name`/`Parameters`/`Return` columns; `Parameters` is space-separated
    with the literal sentinel `empty` for a no-argument method.
    """
    owner = normalize_type_name((row.get("Type") or "").strip())
    name = (row.get("Name") or "").strip()
    if not owner or not name:
        return None
    param_text = (row.get("Parameters") or "").strip()
    if not param_text or param_text == "empty":
        params: tuple[str, ...] = ()
    else:
        params = tuple(normalize_type_name(part) for part in param_text.split())
    return_type = normalize_type_name((row.get("Return") or "").strip())
    return MethodRef(owner=owner, name=name, params=params, return_type=return_type or None)


def _read_field_descriptor(
        descriptor: str,
        index: int,
        allow_void: bool = False,
) -> tuple[str, int]:
    if index >= len(descriptor):
        raise ValueError("Unexpected end of JVM descriptor.")
    dimensions = 0
    while index < len(descriptor) and descriptor[index] == "[":
        dimensions += 1
        index += 1
    if index >= len(descriptor):
        raise ValueError("Array descriptor has no component type.")
    code = descriptor[index]
    if code == "V":
        if dimensions or not allow_void:
            raise ValueError("void is only valid as a non-array return type.")
        return "void", index + 1
    if code == "L":
        end = descriptor.find(";", index)
        if end < 0:
            raise ValueError("Object descriptor has no terminating semicolon.")
        internal_name = descriptor[index + 1:end]
        if not internal_name or any(char in internal_name for char in ".;[()"):
            raise ValueError(f"Invalid object type in JVM descriptor: '{internal_name}'.")
        base = internal_name.replace("/", ".")
        index = end + 1
    elif code in _PRIMITIVE_DESCRIPTORS and code != "V":
        base = _PRIMITIVE_DESCRIPTORS[code]
        index += 1
    else:
        raise ValueError(f"Unknown JVM descriptor type code: '{code}'.")
    return base + "[]" * dimensions, index


def parse_jvm_descriptor(descriptor: str) -> tuple[tuple[str, ...], str]:
    """Parse a JVM method descriptor into source-form `(params, return)`.

    For example `(Ljava/lang/String;[I)V` -> `(("java.lang.String", "int[]"), "void")`.
    Used to turn JaCoCo `<method desc=...>` entries into canonical ids.
    """
    if not descriptor.startswith("("):
        raise ValueError("JVM method descriptor must start with '('.")
    params: list[str] = []
    index = 1
    while True:
        if index >= len(descriptor):
            raise ValueError("JVM method descriptor has no closing ')'.")
        if descriptor[index] == ")":
            break
        param_type, index = _read_field_descriptor(descriptor, index)
        params.append(param_type)
    return_type, index = _read_field_descriptor(descriptor, index + 1, allow_void=True)
    if index != len(descriptor):
        raise ValueError(f"Trailing data in JVM method descriptor: '{descriptor[index:]}'.")
    return tuple(params), return_type


def method_ref_from_iprof(
        method_record: dict,
        type_names: dict[int, str],
) -> MethodRef | None:
    """Build a MethodRef from one `.iprof` `methods` entry and its type table.

    The `signature` array is `[declaringType, returnType, paramType...]`
    (verified against shipped `jdk_profile.iprof`): index 0 is the declaring
    class, index 1 is the return type, the remainder are parameter types.
    """
    name = method_record.get("name")
    signature = method_record.get("signature") or []
    if not name or len(signature) < 2:
        return None
    owner = normalize_type_name(type_names.get(signature[0], ""))
    return_type = normalize_type_name(type_names.get(signature[1], ""))
    params = tuple(normalize_type_name(type_names.get(type_id, f"?{type_id}")) for type_id in signature[2:])
    if not owner:
        return None
    return MethodRef(owner=owner, name=name, params=params, return_type=return_type or None)
