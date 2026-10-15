# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Scenario discovery and the subsystem each scenario names.

A scenario is one test source file (§FS-real-use-test-contract.1.1), so the
programs find and describe scenarios without parsing Java beyond the class doc
comment and the package declaration.
"""
from __future__ import annotations

import os
import re

CLASS_DECLARATION: re.Pattern[str] = re.compile(
    r"^(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:public\s+)?(?:final\s+|abstract\s+)*class\s+(\w+)",
    re.MULTILINE,
)
JAVADOC: re.Pattern[str] = re.compile(r"/\*\*(.*?)\*/\s*$", re.DOTALL)
MARKDOWN_DOC_LINE: re.Pattern[str] = re.compile(r"^\s*///\s?(.*)$")
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


def class_name(test_java_path: str, path: str) -> str:
    return os.path.relpath(path, test_java_path)[: -len(".java")].replace(os.sep, ".")


def _read(path: str) -> str:
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def subsystem(path: str) -> str | None:
    """First sentence of the class doc comment, which names the scenario's subsystem."""
    text: str = _read(path)
    declaration: re.Match[str] | None = CLASS_DECLARATION.search(text)
    if declaration is None:
        return None
    before: str = text[: declaration.start()].rstrip()
    javadoc: re.Match[str] | None = JAVADOC.search(before)
    if javadoc is not None:
        body: str = " ".join(
            line.strip().lstrip("*").strip() for line in javadoc.group(1).splitlines()
        )
    else:
        lines: list[str] = []
        for line in reversed(before.splitlines()):
            match: re.Match[str] | None = MARKDOWN_DOC_LINE.match(line)
            if match is None:
                break
            lines.insert(0, match.group(1).strip())
        body = " ".join(lines)
    sentence: str = body.strip().split(". ")[0].strip()
    return sentence or None


def test_packages(test_java_path: str) -> set[str]:
    """Packages the scenarios declare: the test's own namespace (§FS-real-use-tests.2.2)."""
    packages: set[str] = set()
    for path in scenario_files(test_java_path):
        match: re.Match[str] | None = PACKAGE_DECLARATION.search(_read(path))
        if match is not None:
            packages.add(match.group(1))
    return packages
