# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import shutil
import subprocess
import sys
import tarfile
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass
from email.message import Message
from typing import Any

from utility_scripts.metadata_index import is_not_for_native_image_entry
from utility_scripts.stage_logger import log_stage
from utility_scripts.task_logs import build_task_log_path, display_log_path

SOURCE_CONTEXT_FIELD_BY_TYPE = {
    "main": "source-code-url",
    "test": "test-code-url",
    "documentation": "documentation-url",
}

SOURCE_CONTEXT_LABEL_BY_TYPE = {
    "main": "Main sources",
    "test": "Test sources",
    "documentation": "Documentation sources",
}

DEFAULT_POPULATE_AGENT_COMMAND = "codex -a never exec -s danger-full-access"
DEFAULT_TEST_LANGUAGE = "java"
VERSION_PLACEHOLDER = "$version$"
TEST_SOURCE_DIR_BY_LANGUAGE = {
    "java": "java",
    "kotlin": "kotlin",
    "scala": "scala",
}
TEST_FILE_EXTENSIONS_BY_LANGUAGE = {
    "java": (".java",),
    "kotlin": (".kt",),
    "scala": (".scala",),
}
DEFAULT_TEST_FILE_EXTENSIONS = TEST_FILE_EXTENSIONS_BY_LANGUAGE[DEFAULT_TEST_LANGUAGE]


@dataclass(frozen=True)
class SourceArtifactContext:
    source_type: str
    url: str | None
    local_dir: str | None
    read_only_files: list[str]
    available: bool
    reason: str | None = None


@dataclass(frozen=True)
class PreparedSourceContext:
    coordinate: str
    repository_url: str | None
    artifacts: list[SourceArtifactContext]

    @property
    def read_only_files(self) -> list[str]:
        files: list[str] = []
        for artifact in self.artifacts:
            files.extend(artifact.read_only_files)
        return files

    @property
    def is_available(self) -> bool:
        return any(artifact.available for artifact in self.artifacts)

    def to_prompt_overview(self) -> str:
        lines = [f"Coordinate: {self.coordinate}"]
        lines.append(f"Repository URL: {self.repository_url or 'N/A'}")
        if not self.artifacts:
            lines.append("No source context was requested for this strategy.")
            return "\n".join(lines)

        for artifact in self.artifacts:
            label = SOURCE_CONTEXT_LABEL_BY_TYPE.get(artifact.source_type, artifact.source_type)
            lines.append(f"{label}:")
            lines.append(f"- URL: {artifact.url or 'N/A'}")
            if artifact.available:
                lines.append(f"- Local path: {display_log_path(artifact.local_dir)}")
                lines.append(f"- Files available: {len(artifact.read_only_files)}")
            else:
                lines.append(f"- Status: unavailable ({artifact.reason or 'not found'})")
        return "\n".join(lines)


@dataclass(frozen=True)
class TestSourceLayout:
    language: str
    source_dir_name: str
    source_root: str
    file_extensions: tuple[str, ...]

    @property
    def display_language(self) -> str:
        return self.language[:1].upper() + self.language[1:]


def normalize_source_context_types(raw_value: Any) -> list[str]:
    if raw_value is None:
        return []
    if isinstance(raw_value, str):
        values = [raw_value]
    elif isinstance(raw_value, list):
        values = raw_value
    else:
        print(f"ERROR: Unsupported source-context-types value: {raw_value!r}", file=sys.stderr)
        raise SystemExit(1)

    normalized: list[str] = []
    for value in values:
        if not isinstance(value, str):
            print(f"ERROR: source-context-types entries must be strings: {value!r}", file=sys.stderr)
            raise SystemExit(1)
        normalized_value = value.strip().lower()
        if normalized_value not in SOURCE_CONTEXT_FIELD_BY_TYPE:
            print(f"ERROR: Unsupported source context type: {normalized_value}", file=sys.stderr)
            raise SystemExit(1)
        if normalized_value not in normalized:
            normalized.append(normalized_value)
    return normalized


def populate_artifact_urls(reachability_repo_path: str, coordinate: str, agent_command: str = DEFAULT_POPULATE_AGENT_COMMAND) -> None:
    log_path = build_task_log_path("populate-artifact-urls", coordinate, "populate_artifact_urls.log")
    log_path_display = display_log_path(log_path)
    log_stage("populate-artifact-urls", f"Populating artifact URLs for {coordinate}; output: {log_path_display}")
    command = [
        "./gradlew",
        "populateArtifactURLs",
        f"--coordinates={coordinate}",
        f"--agent-command={agent_command}",
    ]
    result = subprocess.run(
        command,
        cwd=reachability_repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(result.stdout)
    if result.returncode != 0:
        print(
            f"ERROR: populateArtifactURLs failed for {coordinate}. See {log_path_display} for details.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    log_stage("populate-artifact-urls", f"Artifact URLs populated for {coordinate}")


def discover_artifact_metadata(
        reachability_repo_path: str,
        coordinate: str,
        agent_command: str = DEFAULT_POPULATE_AGENT_COMMAND,
) -> None:
    log_path = build_task_log_path("discover-artifact-metadata", coordinate, "discover_artifact_metadata.log")
    log_path_display = display_log_path(log_path)
    log_stage("discover-artifact-metadata", f"Discovering artifact metadata for {coordinate}; output: {log_path_display}")
    command = [
        "./gradlew",
        "discoverArtifactMetadata",
        f"--coordinates={coordinate}",
        f"--agent-command={agent_command}",
    ]
    result = subprocess.run(
        command,
        cwd=reachability_repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    with open(log_path, "w", encoding="utf-8") as log_file:
        log_file.write(result.stdout)
    if result.returncode != 0:
        print(
            f"ERROR: discoverArtifactMetadata failed for {coordinate}. See {log_path_display} for details.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    log_stage("discover-artifact-metadata", f"Artifact metadata discovered for {coordinate}")


def prepare_source_contexts(
        repo_root: str,
        reachability_repo_path: str,
        coordinate: str,
        source_context_types: list[str],
) -> PreparedSourceContext:
    index_entry = load_index_entry(reachability_repo_path, coordinate)
    metadata_version = index_entry.get("metadata-version")
    if not isinstance(metadata_version, str) or not metadata_version.strip():
        print(f"ERROR: Missing metadata-version in index.json entry for {coordinate}", file=sys.stderr)
        raise SystemExit(1)
    base_dir = os.path.join(repo_root, "local_repositories", "source_context", *_coordinate_parts(coordinate))
    os.makedirs(base_dir, exist_ok=True)

    artifacts: list[SourceArtifactContext] = []
    for source_type in source_context_types:
        field_name = SOURCE_CONTEXT_FIELD_BY_TYPE[source_type]
        url = render_url_template(normalize_url_value(index_entry.get(field_name)), metadata_version)
        if url is None:
            log_stage("source-context", f"{source_type}: unavailable, no URL in index.json")
            artifacts.append(SourceArtifactContext(source_type, None, None, [], False, "no URL in index.json"))
            continue
        artifacts.append(download_source_artifact(base_dir, source_type, url))

    prepared_context = PreparedSourceContext(
        coordinate=coordinate,
        repository_url=normalize_url_value(index_entry.get("repository-url")),
        artifacts=artifacts,
    )
    available_artifacts = [artifact for artifact in prepared_context.artifacts if artifact.available]
    log_stage(
        "source-context",
        "Ready for {coordinate}: {available}/{total} artifact types available".format(
            coordinate=coordinate,
            available=len(available_artifacts),
            total=len(prepared_context.artifacts),
        ),
    )
    return prepared_context


def load_index_entry(reachability_repo_path: str, coordinate: str) -> dict[str, Any]:
    group, artifact, version = _coordinate_parts(coordinate)
    index_path = os.path.join(reachability_repo_path, "metadata", group, artifact, "index.json")
    index_path_display = os.path.relpath(index_path, reachability_repo_path)
    if not os.path.isfile(index_path):
        print(f"ERROR: Missing metadata index file: {index_path_display}", file=sys.stderr)
        raise SystemExit(1)

    with open(index_path, "r", encoding="utf-8") as index_file:
        entries = json.load(index_file)

    if any(is_not_for_native_image_entry(entry) for entry in entries):
        print(
            f"ERROR: {group}:{artifact} is marked not-for-native-image in {index_path_display}.",
            file=sys.stderr,
        )
        raise SystemExit(1)

    for entry in entries:
        if entry.get("metadata-version") == version:
            return entry
        tested_versions = entry.get("tested-versions") or []
        if version in tested_versions:
            return entry

    print(f"ERROR: No metadata index entry found for {coordinate} in {index_path_display}", file=sys.stderr)
    raise SystemExit(1)


def resolve_test_source_layout(reachability_repo_path: str, coordinate: str, module_dir: str) -> TestSourceLayout:
    index_entry = load_index_entry(reachability_repo_path, coordinate)
    language = resolve_test_language_name(index_entry)
    source_dir_name = TEST_SOURCE_DIR_BY_LANGUAGE.get(language, language or DEFAULT_TEST_LANGUAGE)
    expected_source_root = os.path.join(module_dir, "src", "test", source_dir_name)
    resolved_source_root = expected_source_root

    if not os.path.isdir(resolved_source_root):
        fallback_source_root = _discover_existing_test_source_root(module_dir)
        if fallback_source_root is not None:
            resolved_source_root = fallback_source_root
            source_dir_name = os.path.basename(fallback_source_root)
            language = source_dir_name.strip().lower() or DEFAULT_TEST_LANGUAGE

    return TestSourceLayout(
        language=language,
        source_dir_name=source_dir_name,
        source_root=resolved_source_root,
        file_extensions=TEST_FILE_EXTENSIONS_BY_LANGUAGE.get(language, DEFAULT_TEST_FILE_EXTENSIONS),
    )


def resolve_test_language_name(index_entry: dict[str, Any]) -> str:
    language_value = index_entry.get("language")
    if language_value is None:
        return DEFAULT_TEST_LANGUAGE
    if not isinstance(language_value, dict):
        print(f"ERROR: Invalid language entry in index.json: {language_value!r}", file=sys.stderr)
        raise SystemExit(1)

    language_name = language_value.get("name")
    if not isinstance(language_name, str):
        print(f"ERROR: Missing language.name in index.json: {language_value!r}", file=sys.stderr)
        raise SystemExit(1)

    normalized_name = language_name.strip().lower()
    if not normalized_name:
        print(f"ERROR: Empty language.name in index.json: {language_value!r}", file=sys.stderr)
        raise SystemExit(1)
    return normalized_name


def normalize_url_value(value: Any) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        return None
    normalized = value.strip()
    if not normalized or normalized.upper() == "N/A":
        return None
    return normalized


def render_url_template(url: str | None, version: str) -> str | None:
    if url is None:
        return None
    return url.replace(VERSION_PLACEHOLDER, version)


def download_source_artifact(base_dir: str, source_type: str, url: str) -> SourceArtifactContext:
    log_stage("source-context", f"Downloading {source_type} from {url}")
    target_dir = os.path.join(base_dir, source_type)
    if os.path.isdir(target_dir):
        shutil.rmtree(target_dir)
    os.makedirs(target_dir, exist_ok=True)

    download_path = os.path.join(target_dir, _filename_from_url(url))
    try:
        headers = _download_file(url, download_path)
        extracted_files = extract_downloaded_artifact(download_path, target_dir, headers)
    except Exception as exc:  # noqa: BLE001
        log_stage("source-context", f"{source_type}: unavailable ({exc})")
        return SourceArtifactContext(source_type, url, None, [], False, str(exc))

    log_stage(
        "source-context",
        "{source_type}: ready with {count} files at {target_dir}".format(
            source_type=source_type,
            count=len(extracted_files),
            target_dir=display_log_path(target_dir),
        ),
    )
    return SourceArtifactContext(
        source_type=source_type,
        url=url,
        local_dir=target_dir,
        read_only_files=sorted(extracted_files),
        available=bool(extracted_files),
        reason=None if extracted_files else "download produced no readable files",
    )


def extract_downloaded_artifact(download_path: str, target_dir: str, headers: Message | None) -> list[str]:
    lower_name = os.path.basename(download_path).lower()
    extracted_dir = os.path.join(target_dir, "extracted")
    os.makedirs(extracted_dir, exist_ok=True)

    if lower_name.endswith((".jar", ".zip")):
        with zipfile.ZipFile(download_path) as archive:
            archive.extractall(extracted_dir)
        return _list_all_files(extracted_dir)

    if lower_name.endswith((".tar.gz", ".tgz")):
        with tarfile.open(download_path, "r:gz") as archive:
            _safe_extract_tar(archive, extracted_dir)
        return _list_all_files(extracted_dir)

    content_type = ""
    if headers is not None:
        content_type = headers.get_content_type()
    single_file_name = _single_file_name(lower_name, content_type)
    single_file_path = os.path.join(extracted_dir, single_file_name)
    shutil.copyfile(download_path, single_file_path)
    return [single_file_path]


def _download_file(url: str, download_path: str) -> Message | None:
    request = urllib.request.Request(url, headers={"User-Agent": "metadata-forge/0.1"})
    with urllib.request.urlopen(request) as response, open(download_path, "wb") as output_file:
        shutil.copyfileobj(response, output_file)
        return response.headers


def _safe_extract_tar(archive: tarfile.TarFile, target_dir: str) -> None:
    target_root = os.path.realpath(target_dir)
    for member in archive.getmembers():
        member_path = os.path.realpath(os.path.join(target_dir, member.name))
        if not member_path.startswith(target_root + os.sep) and member_path != target_root:
            raise ValueError(f"Refusing to extract unsafe tar entry: {member.name}")
    archive.extractall(target_dir)


def _single_file_name(lower_name: str, content_type: str) -> str:
    if lower_name.endswith((".html", ".htm")) or content_type == "text/html":
        return "index.html"
    if lower_name.endswith((".md", ".markdown")) or content_type == "text/markdown":
        return "README.md"
    if lower_name.endswith(".txt") or content_type.startswith("text/"):
        return "content.txt"
    return os.path.basename(lower_name) or "content.bin"


def _filename_from_url(url: str) -> str:
    parsed = urllib.parse.urlparse(url)
    basename = os.path.basename(parsed.path)
    if basename:
        return basename
    return "downloaded"


def _coordinate_parts(coordinate: str) -> tuple[str, str, str]:
    parts = coordinate.split(":", 2)
    if len(parts) != 3:
        print(f"ERROR: Invalid coordinates format: {coordinate}", file=sys.stderr)
        raise SystemExit(1)
    return parts[0], parts[1], parts[2]


def _discover_existing_test_source_root(module_dir: str) -> str | None:
    test_dir = os.path.join(module_dir, "src", "test")
    if not os.path.isdir(test_dir):
        return None

    candidates: list[str] = []
    for entry in sorted(os.listdir(test_dir)):
        candidate = os.path.join(test_dir, entry)
        if not os.path.isdir(candidate) or entry == "resources":
            continue
        candidates.append(candidate)
    if len(candidates) == 1:
        return candidates[0]
    return None


def _list_all_files(directory_path: str) -> list[str]:
    files: list[str] = []
    for root_dir, _, file_names in os.walk(directory_path):
        for file_name in file_names:
            files.append(os.path.join(root_dir, file_name))
    return files
