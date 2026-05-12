# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Apply explicit missing-metadata requests extracted from issue bodies."""

import copy
import json
import os
import re
from typing import Any


REACHABILITY_METADATA_FILENAME = "reachability-metadata.json"


def apply_issue_requested_metadata(metadata_dir: str, context: str) -> int:
    """Merge explicit issue-requested reflection metadata into a metadata directory."""
    entries = extract_issue_requested_reflection_entries(context)
    if not entries:
        return 0

    metadata_path = os.path.join(metadata_dir, REACHABILITY_METADATA_FILENAME)
    metadata = _load_metadata(metadata_path)
    reflection = metadata.setdefault("reflection", [])
    if not isinstance(reflection, list):
        return 0

    added = 0
    for entry in entries:
        added += _merge_reflection_entry(reflection, entry)

    if added == 0:
        return 0

    os.makedirs(metadata_dir, exist_ok=True)
    with open(metadata_path, "w", encoding="utf-8") as metadata_file:
        json.dump(metadata, metadata_file, indent=2, ensure_ascii=False)
        metadata_file.write("\n")
    return added


def extract_issue_requested_reflection_entries(context: str) -> list[dict[str, Any]]:
    """Extract concrete reflection entries from reporter-provided metadata context."""
    if not context.strip():
        return []

    entries: list[dict[str, Any]] = []
    trigger_type = _first_non_jdk_trigger_type(context)
    for entry in _extract_json_reflection_entries(context):
        entries.append(_with_condition(entry, trigger_type))
    entries.extend(_extract_missing_reflection_method_entries(context))
    entries.extend(_extract_class_literal_entries(context, trigger_type))
    return _deduplicate_entries(entries)


def format_issue_requested_test_requirements(context: str) -> str:
    """Format explicit reporter metadata as mandatory test-coverage requirements."""
    entries = extract_issue_requested_reflection_entries(context)
    if not entries:
        return ""

    lines = [
        "Mandatory issue-requested test coverage:",
        "- Add or preserve tests that exercise these reporter-requested metadata needs through public library API paths.",
        "- Do not satisfy these requirements with direct test reflection, no-op class literals, or assertions that only reference the metadata target.",
    ]
    for entry in entries:
        lines.extend(_format_entry_test_requirements(entry))
    return "\n".join(lines)


def _load_metadata(metadata_path: str) -> dict[str, Any]:
    if not os.path.isfile(metadata_path):
        return {}
    with open(metadata_path, "r", encoding="utf-8") as metadata_file:
        metadata = json.load(metadata_file)
    return metadata if isinstance(metadata, dict) else {}


def _format_entry_test_requirements(entry: dict[str, Any]) -> list[str]:
    entry_type = entry.get("type")
    if not isinstance(entry_type, str) or not entry_type:
        return []

    requirements: list[str] = []
    for method in entry.get("methods") or []:
        if not isinstance(method, dict):
            continue
        method_name = method.get("name")
        if not isinstance(method_name, str) or not method_name:
            continue
        parameter_types = method.get("parameterTypes")
        if isinstance(parameter_types, list):
            params = ", ".join(str(parameter_type) for parameter_type in parameter_types)
        else:
            params = ""
        requirements.append(f"- Exercise code that requires `{entry_type}.{method_name}({params})`.")

    if not requirements:
        requirements.append(f"- Exercise code that requires `{entry_type}` to be registered.")
    return requirements


def _first_non_jdk_trigger_type(context: str) -> str | None:
    method_entries = _extract_missing_reflection_method_entries(context)
    for entry in method_entries:
        entry_type = entry.get("type")
        if isinstance(entry_type, str) and not _is_jdk_type(entry_type):
            return _strip_array_suffix(entry_type)

    for match in re.finditer(r"\b((?:[a-z_][\w$]*\.)+[A-Z][\w$]*(?:\[\])*)\b", context):
        class_name = match.group(1)
        if not _is_jdk_type(class_name):
            return _strip_array_suffix(class_name)
    return None


def _extract_json_reflection_entries(context: str) -> list[dict[str, Any]]:
    decoder = json.JSONDecoder()
    entries: list[dict[str, Any]] = []
    for index, char in enumerate(context):
        if char not in "[{":
            continue
        try:
            parsed, _end = decoder.raw_decode(context[index:])
        except json.JSONDecodeError:
            continue
        entries.extend(_reflection_entries_from_json(parsed))
    return entries


def _reflection_entries_from_json(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, list):
        entries: list[dict[str, Any]] = []
        for item in value:
            entries.extend(_reflection_entries_from_json(item))
        return entries
    if not isinstance(value, dict):
        return []
    reflection = value.get("reflection")
    if isinstance(reflection, list):
        return [copy.deepcopy(entry) for entry in reflection if _is_reflection_entry(entry)]
    if _is_reflection_entry(value):
        return [copy.deepcopy(value)]
    return []


def _is_reflection_entry(value: Any) -> bool:
    return isinstance(value, dict) and "type" in value


def _extract_missing_reflection_method_entries(context: str) -> list[dict[str, Any]]:
    method_pattern = re.compile(
        r"Cannot reflectively invoke method\s+'[^']*?"
        r"(?P<class>[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+)"
        r"\.(?P<method>[A-Za-z_$][\w$]*)\((?P<params>[^)]*)\)'"
    )
    entries: list[dict[str, Any]] = []
    for match in method_pattern.finditer(context):
        declaring_type = match.group("class")
        params = [param.strip() for param in match.group("params").split(",") if param.strip()]
        entries.append({
            "condition": {"typeReached": _strip_array_suffix(declaring_type)},
            "type": declaring_type,
            "methods": [
                {
                    "name": match.group("method"),
                    "parameterTypes": params,
                }
            ],
        })
    return entries


def _extract_class_literal_entries(context: str, trigger_type: str | None) -> list[dict[str, Any]]:
    pattern = re.compile(r"\b((?:[a-z_][\w$]*\.)+[A-Z][\w$]*(?:\[\])*)\.class\b")
    entries: list[dict[str, Any]] = []
    for match in pattern.finditer(context):
        class_name = match.group(1)
        entries.append(_with_condition({"type": class_name}, trigger_type))
    return entries


def _with_condition(entry: dict[str, Any], trigger_type: str | None) -> dict[str, Any]:
    conditioned = copy.deepcopy(entry)
    condition = conditioned.get("condition")
    if isinstance(condition, dict) and isinstance(condition.get("typeReached"), str):
        return conditioned

    entry_type = conditioned.get("type")
    type_reached = trigger_type
    if type_reached is None and isinstance(entry_type, str):
        type_reached = _strip_array_suffix(entry_type)
    if type_reached is not None:
        conditioned["condition"] = {"typeReached": type_reached}
    return conditioned


def _merge_reflection_entry(reflection: list[Any], new_entry: dict[str, Any]) -> int:
    for existing in reflection:
        if not isinstance(existing, dict):
            continue
        if existing.get("type") != new_entry.get("type"):
            continue
        if existing.get("condition") != new_entry.get("condition"):
            continue
        return _merge_entry_members(existing, new_entry)
    reflection.append(copy.deepcopy(new_entry))
    return _entry_member_count(new_entry)


def _merge_entry_members(existing: dict[str, Any], new_entry: dict[str, Any]) -> int:
    added = 0
    for key, value in new_entry.items():
        if key in {"type", "condition"}:
            continue
        if key not in existing:
            existing[key] = copy.deepcopy(value)
            added += _member_value_count(value)
            continue
        if isinstance(existing[key], list) and isinstance(value, list):
            added += _merge_list_members(existing[key], value)
    return added


def _merge_list_members(existing: list[Any], additions: list[Any]) -> int:
    added = 0
    serialized_existing = {_stable_json(item) for item in existing}
    for item in additions:
        serialized = _stable_json(item)
        if serialized in serialized_existing:
            continue
        existing.append(copy.deepcopy(item))
        serialized_existing.add(serialized)
        added += 1
    return added


def _entry_member_count(entry: dict[str, Any]) -> int:
    count = 0
    for key, value in entry.items():
        if key not in {"type", "condition"}:
            count += _member_value_count(value)
    return count or 1


def _member_value_count(value: Any) -> int:
    if isinstance(value, list):
        return len(value)
    return 1


def _deduplicate_entries(entries: list[dict[str, Any]]) -> list[dict[str, Any]]:
    deduplicated: list[dict[str, Any]] = []
    seen: set[str] = set()
    for entry in entries:
        key = _stable_json(entry)
        if key in seen:
            continue
        seen.add(key)
        deduplicated.append(entry)
    return deduplicated


def _stable_json(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def _strip_array_suffix(type_name: str) -> str:
    while type_name.endswith("[]"):
        type_name = type_name[:-2]
    return type_name


def _is_jdk_type(type_name: str) -> bool:
    stripped = _strip_array_suffix(type_name)
    return stripped.startswith(("java.", "javax.", "jdk.", "sun."))
