# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dataclasses import dataclass
import os
import re
import subprocess


SCAFFOLD_PLACEHOLDER_TEXT = "This is just a placeholder, implement your test"
TEST_SOURCE_EXTENSIONS = (".java", ".kt", ".scala")
SCAFFOLD_PLACEHOLDER_TEST_PATTERNS = (
    re.compile(
        r"@Test\s+(?:public\s+)?void\s+test\s*\(\s*\)\s+throws\s+Exception\s*\{\s*"
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
)
TEST_ANNOTATION_PATTERN = re.compile(r"(?m)^\s*@Test\b")
NATIVE_IMAGE_DISABLED_ANNOTATION_PATTERN = re.compile(r"@\s*DisabledInNativeImage\b")
NATIVE_IMAGE_RUNTIME_PATTERNS = (
    re.compile(r"\b(?:NativeImageSupport\.)?isNativeImageRuntime\s*\("),
    re.compile(r"\b(?:org\.graalvm\.nativeimage\.)?ImageInfo\.inImage(?:Runtime)?Code\s*\("),
    re.compile(r"\borg\.graalvm\.nativeimage\.imagecode\b"),
    re.compile(r"\bSystem\.getProperty\s*\(\s*\"org\.graalvm\.nativeimage\.imagecode\""),
    re.compile(r"\bBoolean\.getBoolean\s*\(\s*\"org\.graalvm\.nativeimage\.imagecode\""),
)
BOOLEAN_NATIVE_ASSIGNMENT_PATTERN = re.compile(
    r"\b(?:boolean|Boolean|var)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?P<expr>[^;]*);"
)
NATIVE_HELPER_METHOD_PATTERN = re.compile(
    r"\b(?:boolean|Boolean)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\([^)]*\)\s*\{(?P<body>.*?)\}",
    re.DOTALL,
)
NATIVE_HELPER_NAME_PATTERN = re.compile(r"\bnative(?:Image)?\b|\binNativeImage\b|\bisNative\b", re.IGNORECASE)
IF_PATTERN = re.compile(r"\bif\s*\(")
SKIP_RETURN_PATTERN = re.compile(
    r"\breturn\s*(?:;|Optional\.empty\s*\(\s*\)\s*;|"
    r"(?:Collections\.)?empty(?:List|Set|Map)\s*\(\s*\)\s*;|"
    r"(?:List|Set|Map)\.of\s*\(\s*\)\s*;|null\s*;)"
)
ASSUMPTION_OR_ABORT_PATTERN = re.compile(
    r"\b(?:Assumptions\.)?assume(?:True|False|That)\s*\(|"
    r"\b(?:Assumptions\.)?abort\s*\(|"
    r"\bTestAbortedException\b"
)


@dataclass(frozen=True)
class PlaceholderOccurrence:
    file_path: str
    line_number: int


@dataclass(frozen=True)
class NativeImageSkipOccurrence:
    file_path: str
    line_number: int
    reason: str


@dataclass(frozen=True)
class ScaffoldPlaceholderCleanupResult:
    removed_files: list[str]
    remaining_placeholders: list[PlaceholderOccurrence]


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


def find_native_image_skip_guards(test_source_root: str) -> list[NativeImageSkipOccurrence]:
    """Return generated tests that skip or abort assertions only under native image."""
    if not os.path.isdir(test_source_root):
        return []

    occurrences: list[NativeImageSkipOccurrence] = []
    for file_path in _iter_test_source_files(test_source_root):
        occurrences.extend(_native_image_skip_occurrences(file_path))
    return occurrences


def format_native_image_skip_occurrence(
        occurrence: NativeImageSkipOccurrence,
        repo_path: str | None = None,
) -> str:
    display_path = occurrence.file_path
    if repo_path:
        display_path = os.path.relpath(occurrence.file_path, repo_path)
    return f"{display_path}:{occurrence.line_number}: {occurrence.reason}"


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


def _iter_test_source_files(test_source_root: str) -> list[str]:
    test_source_files: list[str] = []
    for root, _, files in os.walk(test_source_root):
        for file_name in files:
            if file_name.endswith(TEST_SOURCE_EXTENSIONS):
                test_source_files.append(os.path.join(root, file_name))
    return sorted(test_source_files)


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


def _native_image_skip_occurrences(file_path: str) -> list[NativeImageSkipOccurrence]:
    try:
        with open(file_path, "r", encoding="utf-8") as source_file:
            lines = source_file.readlines()
    except OSError:
        return []

    source = "".join(lines)
    native_names = _native_image_indicator_names(source)
    occurrences: list[NativeImageSkipOccurrence] = []
    for line_number, line in enumerate(lines, start=1):
        if NATIVE_IMAGE_DISABLED_ANNOTATION_PATTERN.search(line):
            occurrences.append(NativeImageSkipOccurrence(
                file_path,
                line_number,
                "@DisabledInNativeImage disables generated native-image coverage",
            ))

    for if_statement in _iter_if_statements(lines):
        if not _contains_native_image_runtime_guard(if_statement.text, native_names):
            continue
        for action_line, reason in _native_image_skip_actions(lines, if_statement):
            occurrences.append(NativeImageSkipOccurrence(file_path, action_line, reason))

    return _deduplicate_native_image_skip_occurrences(occurrences)


@dataclass(frozen=True)
class _IfStatement:
    line_number: int
    text: str
    end_index: int
    body_start_index: int
    body_end_index: int
    has_block: bool


def _native_image_indicator_names(source: str) -> set[str]:
    native_names: set[str] = set()
    for match in BOOLEAN_NATIVE_ASSIGNMENT_PATTERN.finditer(source):
        if _contains_native_image_runtime_guard(match.group("expr"), native_names):
            native_names.add(match.group("name"))

    for match in NATIVE_HELPER_METHOD_PATTERN.finditer(source):
        method_name = match.group("name")
        if not NATIVE_HELPER_NAME_PATTERN.search(method_name):
            continue
        body = match.group("body")
        if _contains_native_image_runtime_guard(body, native_names):
            native_names.add(method_name)
    return native_names


def _contains_native_image_runtime_guard(text: str, native_names: set[str]) -> bool:
    if "NativeImageSupport.isUnsupportedFeatureError" in text:
        text = text.replace("NativeImageSupport.isUnsupportedFeatureError", "")
    if any(pattern.search(text) for pattern in NATIVE_IMAGE_RUNTIME_PATTERNS):
        return True
    return any(re.search(rf"\b{re.escape(name)}\b\s*(?:\(\s*\))?", text) for name in native_names)


def _iter_if_statements(lines: list[str]) -> list[_IfStatement]:
    statements: list[_IfStatement] = []
    index = 0
    while index < len(lines):
        if lines[index].strip().startswith("//") or IF_PATTERN.search(lines[index]) is None:
            index += 1
            continue
        statement = _read_if_statement(lines, index)
        if statement is None:
            index += 1
            continue
        statements.append(statement)
        index += 1
    return statements


def _read_if_statement(lines: list[str], start_index: int) -> _IfStatement | None:
    text_parts: list[str] = []
    paren_depth = 0
    saw_if = False
    end_index = start_index
    for index in range(start_index, len(lines)):
        line = lines[index]
        if not saw_if:
            if_match = IF_PATTERN.search(line)
            if if_match is None:
                return None
            line_fragment = line[if_match.start():]
            saw_if = True
        else:
            line_fragment = line
        text_parts.append(line_fragment)
        paren_depth += line_fragment.count("(") - line_fragment.count(")")
        end_index = index
        if saw_if and paren_depth <= 0:
            break

    text = "".join(text_parts)
    brace_index = text.find("{")
    if brace_index != -1:
        body_end_index = _find_block_end(lines, end_index)
        return _IfStatement(
            start_index + 1,
            text,
            end_index,
            end_index + 1,
            body_end_index,
            True,
        )

    trailing = text[text.rfind(")") + 1:]
    if trailing.strip():
        return _IfStatement(start_index + 1, text, end_index, end_index, end_index, False)

    next_index = _next_significant_line_index(lines, end_index + 1)
    if next_index is None:
        return None
    if lines[next_index].strip().startswith("{"):
        body_end_index = _find_block_end(lines, next_index)
        return _IfStatement(
            start_index + 1,
            text,
            next_index,
            next_index + 1,
            body_end_index,
            True,
        )
    return _IfStatement(start_index + 1, text, end_index, next_index, next_index, False)


def _find_block_end(lines: list[str], start_index: int) -> int:
    depth = 0
    saw_open = False
    for index in range(start_index, len(lines)):
        for char in lines[index]:
            if char == "{":
                depth += 1
                saw_open = True
            elif char == "}":
                depth -= 1
                if saw_open and depth == 0:
                    return index
    return len(lines) - 1


def _next_significant_line_index(lines: list[str], start_index: int) -> int | None:
    for index in range(start_index, len(lines)):
        stripped = lines[index].strip()
        if not stripped or stripped.startswith("//"):
            continue
        return index
    return None


def _native_image_skip_actions(
        lines: list[str],
        if_statement: _IfStatement,
) -> list[tuple[int, str]]:
    actions: list[tuple[int, str]] = []
    ranges = [(if_statement.line_number, if_statement.text)]
    if if_statement.has_block:
        ranges.extend(
            (line_number, lines[line_number - 1])
            for line_number in range(if_statement.body_start_index + 1, if_statement.body_end_index + 1)
        )
    else:
        if if_statement.body_start_index == if_statement.end_index:
            body_text = if_statement.text[if_statement.text.rfind(")") + 1:]
            ranges = [(if_statement.line_number, body_text)]
        else:
            line_number = if_statement.body_start_index + 1
            ranges = [(line_number, lines[if_statement.body_start_index])]

    for line_number, text in ranges:
        if SKIP_RETURN_PATTERN.search(text):
            actions.append((line_number, "native-image guard returns before exercising assertions"))
        if ASSUMPTION_OR_ABORT_PATTERN.search(text):
            actions.append((line_number, "native-image guard aborts or assumes away generated test coverage"))
    return actions


def _deduplicate_native_image_skip_occurrences(
        occurrences: list[NativeImageSkipOccurrence],
) -> list[NativeImageSkipOccurrence]:
    seen: set[tuple[str, int, str]] = set()
    deduplicated: list[NativeImageSkipOccurrence] = []
    for occurrence in occurrences:
        key = (occurrence.file_path, occurrence.line_number, occurrence.reason)
        if key in seen:
            continue
        seen.add(key)
        deduplicated.append(occurrence)
    return deduplicated
