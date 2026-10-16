# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Copying collected entries into a project's test-local metadata.

An entry is copied only under the rules of §FS-real-use-tests.2.2: conditioned
on `typeReached`, naming a type the project's filter includes or the test's own
code.
"""
from __future__ import annotations

import json
import os
from typing import Any

from run_state import read_json

def filter_prefixes(filter_path: str) -> list[str]:
    """Package prefixes the project's `user-code-filter.json` includes."""
    prefixes: list[str] = []
    for rule in read_json(filter_path).get("rules", []):
        pattern: str | None = rule.get("includeClasses")
        if pattern:
            prefixes.append(pattern.rstrip("*").rstrip(".") + ".")
    return prefixes


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

