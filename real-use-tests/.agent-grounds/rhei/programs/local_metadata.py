# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Copying collected entries into a project's test-local metadata, and reporting them.

An entry is copied only under the rules of §FS-real-use-tests.2.2: conditioned
on `typeReached`, naming a type the project's filter includes or the test's own
code. Grouping for the pull request is §FS-real-use-tests.2.3.
"""
from __future__ import annotations

import json
import os
from typing import Any, Iterator

from run_state import read_json

def filter_prefixes(filter_path: str) -> list[str]:
    """Package prefixes the project's `user-code-filter.json` includes."""
    prefixes: list[str] = []
    for rule in read_json(filter_path).get("rules", []):
        pattern: str | None = rule.get("includeClasses")
        if pattern:
            prefixes.append(pattern.rstrip("*").rstrip(".") + ".")
    return prefixes


def _entries(metadata: dict[str, Any]) -> Iterator[tuple[str, dict[str, Any]]]:
    for section in ("reflection", "resources"):
        for entry in metadata.get(section, []):
            yield section, entry
    for section, entries in metadata.get("foreign", {}).items():
        for entry in entries:
            yield f"foreign.{section}", entry


def _describe(section: str, entry: dict[str, Any]) -> str:
    subject: Any = entry.get("type") or entry.get("glob") or entry.get("bundle") or entry.get("returnType")
    return f"{section} {subject}"


def _owned_by(type_name: str, prefixes: list[str]) -> bool:
    return any(type_name.startswith(prefix) for prefix in prefixes)


def _conforms(entry: dict[str, Any], owners: list[str]) -> bool:
    condition: Any = entry.get("condition")
    return isinstance(condition, dict) and _owned_by(str(condition.get("typeReached", "")), owners)


def copy_collected(collected_path: str, metadata_path: str, prefixes: list[str], test_packages: set[str]) -> int:
    """Copy the agent's conforming entries into the test-local file; return how many were new.

    An entry must already carry a `typeReached` condition the test-local rules
    accept (§FS-real-use-tests.2.2); others are left for the native fix agent.
    An exact duplicate of an entry already in the file is skipped. A duplicate
    of shipped metadata is copied: it changes nothing (§FS-real-use-test-generation.2.5).
    """
    if not os.path.exists(collected_path):
        return 0
    owners: list[str] = prefixes + [package + "." for package in test_packages]
    collected: dict[str, Any] = read_json(collected_path)
    merged: dict[str, Any] = read_json(metadata_path) if os.path.exists(metadata_path) else {}
    added: int = 0
    for section in ("reflection", "resources"):
        present: list[Any] = merged.setdefault(section, [])
        for entry in collected.get(section, []):
            if _conforms(entry, owners) and entry not in present:
                present.append(entry)
                added += 1
    for section, entries in collected.get("foreign", {}).items():
        present = merged.setdefault("foreign", {}).setdefault(section, [])
        for entry in entries:
            if _conforms(entry, owners) and entry not in present:
                present.append(entry)
                added += 1
    merged = {key: value for key, value in merged.items() if value}
    if added:
        os.makedirs(os.path.dirname(metadata_path), exist_ok=True)
        with open(metadata_path, "w", encoding="utf-8") as handle:
            json.dump(merged, handle, indent=2, sort_keys=True)
            handle.write("\n")
    return added


def grouped_entries(
        metadata_path: str,
        allowed_packages: list[str],
        prefixes: list[str],
        test_packages: set[str],
) -> dict[str, list[str]]:
    """Entries grouped by the library their condition names, target first.

    §FS-real-use-tests.2.3
    """
    groups: dict[str, list[str]] = {}
    if not os.path.exists(metadata_path):
        return groups
    target: list[str] = [package + "." for package in allowed_packages]
    tests: list[str] = [package + "." for package in test_packages]
    for section, entry in _entries(read_json(metadata_path)):
        reached: str = str((entry.get("condition") or {}).get("typeReached", ""))
        if _owned_by(reached, target):
            group: str = "Target library"
        elif _owned_by(reached, tests):
            group = "Test code"
        else:
            owner: str = next((prefix for prefix in prefixes if reached.startswith(prefix)), reached)
            group = f"Supporting library `{owner.rstrip('.')}`"
        groups.setdefault(group, []).append(f"{_describe(section, entry)} when `{reached}` is reached")
    return dict(sorted(groups.items(), key=lambda item: item[0] != "Target library"))
