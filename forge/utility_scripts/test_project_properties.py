# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Point a copied test project at the version that now owns it.

A test directory copied from another version keeps that version in its
scaffold-owned `gradle.properties`. The harness overrides those values from the
environment, so tests still pass, but the directory must read as its owner's
project. §FS-library-update-tested-version-split
"""

import os


def rewrite_test_project_gradle_properties(
        test_dir: str,
        group: str,
        artifact: str,
        version: str,
) -> None:
    """Update scaffold-owned Gradle properties after copying a test project."""
    gradle_properties_path = os.path.join(test_dir, "gradle.properties")
    if not os.path.isfile(gradle_properties_path):
        return

    expected_values = {
        "library.coordinates": f"{group}:{artifact}:{version}",
        "library.version": version,
        "metadata.dir": f"{group}/{artifact}/{version}/",
    }
    with open(gradle_properties_path, "r", encoding="utf-8") as properties_file:
        lines = properties_file.readlines()

    updated_lines: list[str] = []
    for line in lines:
        line_ending = "\n" if line.endswith("\n") else ""
        content = line[:-1] if line_ending else line
        stripped = content.lstrip()
        if not stripped or stripped.startswith("#"):
            updated_lines.append(line)
            continue
        key_separator = "=" if "=" in stripped else ":" if ":" in stripped else None
        if key_separator is None:
            updated_lines.append(line)
            continue
        key = stripped.split(key_separator, 1)[0].strip()
        if key not in expected_values:
            updated_lines.append(line)
            continue
        indent = content[:len(content) - len(stripped)]
        updated_lines.append(f"{indent}{key} = {expected_values[key]}{line_ending}")

    updated_content = "".join(updated_lines)
    original_content = "".join(lines)
    if updated_content != original_content:
        with open(gradle_properties_path, "w", encoding="utf-8") as properties_file:
            properties_file.write(updated_content)
