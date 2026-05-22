# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dataclasses import dataclass
import os
import re
import subprocess


SCAFFOLD_PLACEHOLDER_TEXT = "This is just a placeholder, implement your test"
TEST_SOURCE_EXTENSIONS = (".java", ".kt", ".scala", ".groovy")
SCAFFOLD_PLACEHOLDER_TEST_PATTERNS = (
    re.compile(
        r"@Test\s+(?:public\s+)?void\s+test\s*\(\s*\)\s*(?:throws\s+Exception\s*)?\{\s*"
        + re.escape(f'System.out.println("{SCAFFOLD_PLACEHOLDER_TEXT}");')
        + r"\s*\}"
    ),
    re.compile(
        r"@Test\s+fun\s+test\s*\(\s*\)\s*\{\s*"
        + re.escape(f'println("{SCAFFOLD_PLACEHOLDER_TEXT}")')
        + r"\s*\}"
    ),
    re.compile(
        r"@Test\s+def\s+test\s*\(\s*\)\s*:\s*Unit\s*=\s*\{\s*"
        + re.escape(f'println("{SCAFFOLD_PLACEHOLDER_TEXT}")')
        + r"\s*\}"
    ),
    re.compile(
        r"@Test\s+(?:public\s+)?void\s+test\s*\(\s*\)\s*\{\s*"
        + re.escape(f'println "{SCAFFOLD_PLACEHOLDER_TEXT}"')
        + r"\s*\}"
    ),
)
TEST_ANNOTATION_PATTERN = re.compile(r"(?m)^\s*@Test\b")
TEST_BLOCK_ANNOTATION_PATTERN = re.compile(
    r"(?m)^\s*@(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b"
)
EXCEPTION_ASSERTION_PATTERN = re.compile(
    r"\b(?:assertThrows|assertThatThrownBy|assertThatExceptionOfType|assertFailsWith|expectThrows)\b"
    r"|\.hasCauseInstanceOf\s*\([^)]*(?:Exception|Error)\.class"
    r"|\.is(?:Exactly)?InstanceOf\s*\([^)]*(?:Exception|Error)\.class"
    r"|\bcatch\s*\([^)]*(?:Exception|Error)\b",
    re.IGNORECASE,
)
BROKEN_BEHAVIOR_CONTEXT_PATTERN = re.compile(
    r"\b(?:"
    r"known(?:\s+|[_-])?(?:broken|failure|regression|bug|defect)"
    r"|broken(?:\s+|[_-])?(?:behavior|path|implementation)"
    r"|version[- _]?specific"
    r"|library[- _]?version[- _]?specific"
    r"|regression"
    r"|buggy"
    r"|defect"
    r"|failure(?:\s+|[_-])?path"
    r"|fails?(?:\s+|[_-])?before"
    r"|known(?:\s+|[_-])?to(?:\s+|[_-])?fail"
    r"|this\s+version\s+(?:fails|throws|raises)"
    r")\b",
    re.IGNORECASE,
)
VERSIONED_FAILURE_CONTEXT_PATTERN = re.compile(
    r"\bversion\s+[`\"]?\d+(?:\.\d+){1,4}[^`\n\"]*"
    r"(?:fail|throw|exception|regression|broken)"
    r"|(?:fail|throw|exception|regression|broken)[^`\n\"]*"
    r"\bversion\s+[`\"]?\d+(?:\.\d+){1,4}",
    re.IGNORECASE,
)
NATIVE_IMAGE_UNSUPPORTED_PATTERN = re.compile(r"\bNativeImageSupport\.isUnsupportedFeatureError\s*\(")


@dataclass(frozen=True)
class PlaceholderOccurrence:
    file_path: str
    line_number: int


@dataclass(frozen=True)
class ScaffoldPlaceholderCleanupResult:
    removed_files: list[str]
    remaining_placeholders: list[PlaceholderOccurrence]


@dataclass(frozen=True)
class GeneratedTestValidityIssue:
    file_path: str
    line_number: int
    reason: str
    evidence: str


def cleanup_scaffold_placeholder_tests(
        test_source_root: str,
        repo_path: str,
        scaffold_commit_hash: str,
) -> ScaffoldPlaceholderCleanupResult:
    """Remove scaffold placeholder test files that do not contain additional tests."""
    if not os.path.isdir(test_source_root):
        return ScaffoldPlaceholderCleanupResult([], [])

    scaffold_test_files = _find_scaffold_test_files(test_source_root, repo_path, scaffold_commit_hash)
    placeholder_files = [
        file_path for file_path in scaffold_test_files
        if _file_contains_placeholder(file_path)
    ]
    scaffold_files = [
        file_path for file_path in placeholder_files
        if _contains_only_scaffold_junit_placeholder_test(file_path)
    ]

    removed_files: list[str] = []
    for file_path in scaffold_files:
        os.remove(file_path)
        removed_files.append(file_path)

    removed_file_set = set(removed_files)
    remaining_placeholders = [
        occurrence for occurrence in find_scaffold_placeholder_occurrences(test_source_root)
        if occurrence.file_path not in removed_file_set
    ]

    return ScaffoldPlaceholderCleanupResult(removed_files, remaining_placeholders)


def format_placeholder_occurrence(occurrence: PlaceholderOccurrence, repo_path: str | None = None) -> str:
    display_path = occurrence.file_path
    if repo_path:
        display_path = os.path.relpath(occurrence.file_path, repo_path)
    return f"{display_path}:{occurrence.line_number}"


def find_scaffold_placeholder_occurrences(test_source_root: str) -> list[PlaceholderOccurrence]:
    """Return all scaffold placeholder text occurrences in generated test sources."""
    if not os.path.isdir(test_source_root):
        return []

    occurrences: list[PlaceholderOccurrence] = []
    for root_dir, _, file_names in os.walk(test_source_root):
        for file_name in file_names:
            if not file_name.endswith(TEST_SOURCE_EXTENSIONS):
                continue
            occurrences.extend(_placeholder_occurrences(os.path.join(root_dir, file_name)))
    return occurrences


def collect_generated_test_validity_issues(test_source_root: str) -> list[GeneratedTestValidityIssue]:
    """Find generated tests that look like they codify a known broken library path."""
    if not os.path.isdir(test_source_root):
        return []

    issues: list[GeneratedTestValidityIssue] = []
    for root_dir, _, file_names in os.walk(test_source_root):
        for file_name in file_names:
            if not file_name.endswith(TEST_SOURCE_EXTENSIONS):
                continue
            file_path = os.path.join(root_dir, file_name)
            issues.extend(_collect_file_generated_test_validity_issues(file_path))
    return issues


def format_generated_test_validity_issue(
        issue: GeneratedTestValidityIssue,
        repo_path: str | None = None,
) -> str:
    display_path = issue.file_path
    if repo_path:
        display_path = os.path.relpath(issue.file_path, repo_path)
    return f"{display_path}:{issue.line_number}: {issue.reason}: {issue.evidence}"


def _find_scaffold_test_files(test_source_root: str, repo_path: str, scaffold_commit_hash: str) -> list[str]:
    relative_source_root = os.path.relpath(test_source_root, repo_path)
    result = subprocess.run(
        [
            "git",
            "diff-tree",
            "--root",
            "--no-commit-id",
            "--name-only",
            "--diff-filter=A",
            "-r",
            scaffold_commit_hash,
            "--",
            relative_source_root,
        ],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        return []
    return [
        os.path.join(repo_path, relative_path)
        for relative_path in result.stdout.splitlines()
        if relative_path.endswith(TEST_SOURCE_EXTENSIONS)
    ]


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


def _contains_only_scaffold_junit_placeholder_test(file_path: str) -> bool:
    try:
        with open(file_path, "r", encoding="utf-8") as source_file:
            source = source_file.read()
    except OSError:
        return False
    return (
        any(pattern.search(source) for pattern in SCAFFOLD_PLACEHOLDER_TEST_PATTERNS)
        and len(TEST_ANNOTATION_PATTERN.findall(source)) == 1
    )


def _collect_file_generated_test_validity_issues(file_path: str) -> list[GeneratedTestValidityIssue]:
    try:
        with open(file_path, "r", encoding="utf-8") as source_file:
            lines = source_file.readlines()
    except OSError:
        return []

    issues: list[GeneratedTestValidityIssue] = []
    for start_index, end_index in _iter_test_block_ranges(lines):
        block_lines = lines[start_index:end_index]
        block_text = "".join(block_lines)
        if not _looks_like_broken_behavior_exception_target(block_text):
            continue
        evidence_index, evidence = _find_broken_behavior_evidence(block_lines)
        issues.append(
            GeneratedTestValidityIssue(
                file_path=file_path,
                line_number=start_index + evidence_index + 1,
                reason="test appears to assert a known version-specific broken behavior path",
                evidence=evidence,
            )
        )
    return issues


def _iter_test_block_ranges(lines: list[str]) -> list[tuple[int, int]]:
    annotation_indexes = [
        index
        for index, line in enumerate(lines)
        if TEST_BLOCK_ANNOTATION_PATTERN.search(line)
    ]
    ranges: list[tuple[int, int]] = []
    for position, annotation_index in enumerate(annotation_indexes):
        start_index = _include_leading_comment_context(lines, annotation_index)
        end_index = annotation_indexes[position + 1] if position + 1 < len(annotation_indexes) else len(lines)
        ranges.append((start_index, end_index))
    return ranges


def _include_leading_comment_context(lines: list[str], annotation_index: int) -> int:
    start_index = annotation_index
    lower_bound = max(0, annotation_index - 12)
    while start_index > lower_bound:
        previous = lines[start_index - 1].strip()
        if not previous or previous.startswith(("//", "/*", "*")) or previous.endswith("*/"):
            start_index -= 1
            continue
        break
    return start_index


def _looks_like_broken_behavior_exception_target(block_text: str) -> bool:
    if NATIVE_IMAGE_UNSUPPORTED_PATTERN.search(block_text):
        return False
    if EXCEPTION_ASSERTION_PATTERN.search(block_text) is None:
        return False
    context_text = _with_camel_case_word_boundaries(block_text)
    return (
        BROKEN_BEHAVIOR_CONTEXT_PATTERN.search(context_text) is not None
        or VERSIONED_FAILURE_CONTEXT_PATTERN.search(context_text) is not None
    )


def _find_broken_behavior_evidence(block_lines: list[str]) -> tuple[int, str]:
    for index, line in enumerate(block_lines):
        stripped = line.strip()
        context_line = _with_camel_case_word_boundaries(stripped)
        if (
                BROKEN_BEHAVIOR_CONTEXT_PATTERN.search(context_line)
                or VERSIONED_FAILURE_CONTEXT_PATTERN.search(context_line)
        ):
            return index, _trim_evidence(stripped)
    for index, line in enumerate(block_lines):
        stripped = line.strip()
        if EXCEPTION_ASSERTION_PATTERN.search(stripped):
            return index, _trim_evidence(stripped)
    return 0, "(test block)"


def _trim_evidence(value: str) -> str:
    value = value.strip()
    if len(value) <= 160:
        return value
    return value[:157].rstrip() + "..."


def _with_camel_case_word_boundaries(value: str) -> str:
    return re.sub(r"(?<=[a-z])(?=[A-Z])", " ", value)
