# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dataclasses import dataclass
import os
import subprocess


SCAFFOLD_PLACEHOLDER_TEXT = "This is just a placeholder, implement your test"
TEST_SOURCE_EXTENSIONS = (".java", ".kt", ".scala")


@dataclass(frozen=True)
class PlaceholderOccurrence:
    file_path: str
    line_number: int


@dataclass(frozen=True)
class ScaffoldPlaceholderCleanupResult:
    removed_files: list[str]
    remaining_placeholders: list[PlaceholderOccurrence]


def cleanup_scaffold_placeholder_tests(
        test_source_root: str,
        repo_path: str,
        scaffold_commit_hash: str,
) -> ScaffoldPlaceholderCleanupResult:
    """Remove placeholder test files that are unchanged since the scaffold commit."""
    if not os.path.isdir(test_source_root):
        return ScaffoldPlaceholderCleanupResult([], [])

    placeholder_files = _find_placeholder_test_files(test_source_root)
    scaffold_files = [
        file_path for file_path in placeholder_files
        if _is_unchanged_since_commit(file_path, repo_path, scaffold_commit_hash)
    ]
    non_placeholder_files = [
        file_path for file_path in _list_test_source_files(test_source_root)
        if file_path not in placeholder_files
    ]

    removed_files: list[str] = []
    if non_placeholder_files:
        for file_path in scaffold_files:
            os.remove(file_path)
            removed_files.append(file_path)

    remaining_placeholders: list[PlaceholderOccurrence] = []
    removed_file_set = set(removed_files)
    for file_path in placeholder_files:
        if file_path in removed_file_set:
            continue
        remaining_placeholders.extend(_placeholder_occurrences(file_path))

    return ScaffoldPlaceholderCleanupResult(removed_files, remaining_placeholders)


def format_placeholder_occurrence(occurrence: PlaceholderOccurrence, repo_path: str | None = None) -> str:
    display_path = occurrence.file_path
    if repo_path:
        display_path = os.path.relpath(occurrence.file_path, repo_path)
    return f"{display_path}:{occurrence.line_number}"


def _find_placeholder_test_files(test_source_root: str) -> list[str]:
    return [
        file_path for file_path in _list_test_source_files(test_source_root)
        if _file_contains_placeholder(file_path)
    ]


def _list_test_source_files(test_source_root: str) -> list[str]:
    test_files: list[str] = []
    for root_dir, _, file_names in os.walk(test_source_root):
        for file_name in file_names:
            if file_name.endswith(TEST_SOURCE_EXTENSIONS):
                test_files.append(os.path.join(root_dir, file_name))
    return sorted(test_files)


def _file_contains_placeholder(file_path: str) -> bool:
    try:
        with open(file_path, "r", encoding="utf-8") as source_file:
            return SCAFFOLD_PLACEHOLDER_TEXT in source_file.read()
    except OSError:
        return False


def _placeholder_occurrences(file_path: str) -> list[PlaceholderOccurrence]:
    occurrences: list[PlaceholderOccurrence] = []
    try:
        with open(file_path, "r", encoding="utf-8") as source_file:
            for line_number, line in enumerate(source_file, start=1):
                if SCAFFOLD_PLACEHOLDER_TEXT in line:
                    occurrences.append(PlaceholderOccurrence(file_path, line_number))
    except OSError:
        return []
    return occurrences


def _is_unchanged_since_commit(file_path: str, repo_path: str, commit_hash: str) -> bool:
    relative_path = os.path.relpath(file_path, repo_path)
    result = subprocess.run(
        ["git", "diff", "--quiet", commit_hash, "--", relative_path],
        cwd=repo_path,
        check=False,
    )
    return result.returncode == 0
