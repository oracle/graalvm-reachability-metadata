# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Scenario discovery.

A scenario is one test source file (§FS-real-use-test-contract.1.1), so the
programs find scenarios without parsing Java beyond the package declaration.
"""
from __future__ import annotations

import os
import re

PACKAGE_DECLARATION: re.Pattern[str] = re.compile(r"^package\s+([\w.]+)\s*;", re.MULTILINE)


def scenario_files(test_java_path: str) -> list[str]:
    """Every scenario source file under `src/test/java`, as sorted absolute paths."""
    found: list[str] = []
    for directory, _, names in os.walk(test_java_path):
        found.extend(os.path.join(directory, name) for name in names if name.endswith(".java"))
    return sorted(found)


def class_file(test_java_path: str, class_name: str) -> str:
    """The source file that declares top-level class `class_name`."""
    return os.path.join(test_java_path, *class_name.split(".")) + ".java"


def _read(path: str) -> str:
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def test_packages(test_java_path: str) -> set[str]:
    """Packages the scenarios declare: the test's own namespace (§FS-real-use-tests.2.2)."""
    packages: set[str] = set()
    for path in scenario_files(test_java_path):
        match: re.Match[str] | None = PACKAGE_DECLARATION.search(_read(path))
        if match is not None:
            packages.add(match.group(1))
    return packages
