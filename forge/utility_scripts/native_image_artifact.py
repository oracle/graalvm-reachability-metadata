# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Conservative checks for artifacts that are not Native Image metadata targets."""

from dataclasses import dataclass
import json
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


MAVEN_CENTRAL = "https://repo1.maven.org/maven2"


@dataclass(frozen=True)
class NativeImageEligibility:
    """Eligibility result for a Maven artifact."""

    not_for_native_image: bool
    reason: str | None = None
    replacement: str | None = None


@dataclass(frozen=True)
class MavenDependency:
    """Dependency coordinates declared by a Maven POM."""

    group: str
    artifact: str
    version: str
    scope: str | None = None
    classifier: str | None = None


@dataclass(frozen=True)
class MavenPomMetadata:
    """Small subset of Maven POM metadata used for artifact classification."""

    packaging: str | None
    dependencies: tuple[MavenDependency, ...]


def discovery_file_path(repo_path: str, coordinate: str) -> str:
    """Return the Gradle build-local discovery file path for a coordinate."""
    sanitized = coordinate.replace(":", "-").replace("/", "-")
    return os.path.join(repo_path, "build", "discovered-artifact-metadata", f"{sanitized}.json")


def load_discovered_eligibility(repo_path: str, coordinate: str) -> NativeImageEligibility:
    """Read not-for-native-image eligibility from the discovery file when present."""
    path = discovery_file_path(repo_path, coordinate)
    if not os.path.isfile(path):
        return NativeImageEligibility(False)
    try:
        with open(path, "r", encoding="utf-8") as discovery_file:
            metadata = json.load(discovery_file)
    except (OSError, json.JSONDecodeError):
        return NativeImageEligibility(False)
    if metadata.get("not-for-native-image") is not True:
        return NativeImageEligibility(False)
    reason = str(metadata.get("reason") or "").strip()
    if not reason:
        reason = "Artifact discovery identified this as not applicable to GraalVM Native Image metadata."
    replacement = str(metadata.get("replacement") or "").strip() or None
    return NativeImageEligibility(True, reason, replacement)


def evaluate_native_image_eligibility(repo_path: str, coordinate: str) -> NativeImageEligibility:
    """Return a marker result only for certain non-Native-Image artifacts."""
    discovered = load_discovered_eligibility(repo_path, coordinate)
    if discovered.not_for_native_image:
        return discovered

    coordinate_result = classify_coordinate_name(coordinate)
    if coordinate_result.not_for_native_image:
        return coordinate_result

    return inspect_maven_artifact(coordinate)


def classify_coordinate_name(coordinate: str) -> NativeImageEligibility:
    """Classify certain platform artifacts by coordinate naming conventions."""
    try:
        group, artifact, _version = coordinate.split(":", 2)
    except ValueError:
        print(f"ERROR: Invalid coordinates format: {coordinate}", file=sys.stderr)
        raise SystemExit(1)

    if group == "org.scala-js" or "_sjs" in artifact or "scalajs" in artifact.lower():
        return NativeImageEligibility(
            True,
            "Scala.js artifact; it is not a JVM library consumed by native-image.",
            jvm_artifact_replacement(artifact),
        )

    android_groups = ("com.android", "androidx.")
    if group.startswith(android_groups):
        return NativeImageEligibility(
            True,
            "Android artifact; it targets the Android runtime or Android toolchain rather than JVM Native Image.",
        )

    kotlin_native_suffixes = (
        "-iosarm64",
        "-iosx64",
        "-iossimulatorarm64",
        "-linuxx64",
        "-linuxarm64",
        "-macosx64",
        "-macosarm64",
        "-mingwx64",
        "-tvosarm64",
        "-tvosx64",
        "-tvossimulatorarm64",
        "-watchosarm32",
        "-watchosarm64",
        "-watchosdevicearm64",
        "-watchossimulatorarm64",
        "-wasm-js",
        "-wasm-wasi",
    )
    if artifact.endswith(kotlin_native_suffixes):
        return NativeImageEligibility(
            True,
            "Kotlin Native/Wasm artifact; it is not a JVM library consumed by native-image.",
            jvm_artifact_replacement(artifact),
        )

    if artifact.endswith("-js") and group.startswith(("org.jetbrains.kotlin", "org.jetbrains.kotlinx")):
        return NativeImageEligibility(
            True,
            "Kotlin/JavaScript artifact; it is not a JVM library consumed by native-image.",
            jvm_artifact_replacement(artifact),
        )

    return NativeImageEligibility(False)


def inspect_maven_artifact(coordinate: str) -> NativeImageEligibility:
    """Inspect Maven packaging and artifact contents when available."""
    group, artifact, version = coordinate.split(":", 2)
    base_path = "/".join([group.replace(".", "/"), artifact, version])
    base_url = f"{MAVEN_CENTRAL}/{base_path}/{artifact}-{version}"

    pom_metadata = read_maven_pom_metadata(f"{base_url}.pom")
    packaging = pom_metadata.packaging if pom_metadata else None
    if packaging in {"aar", "klib"}:
        return NativeImageEligibility(
            True,
            f"Maven packaging is `{packaging}`, not a JVM JAR consumed by native-image.",
            jvm_artifact_replacement(artifact),
        )

    jar_bytes = fetch_url(f"{base_url}.jar")
    if jar_bytes is None:
        return NativeImageEligibility(False)

    try:
        with zipfile.ZipFile(jar_bytes) as jar:
            names = jar.namelist()
    except zipfile.BadZipFile:
        return NativeImageEligibility(False)

    class_files = [
        name for name in names
        if name.endswith(".class") and not name.endswith("module-info.class")
    ]
    if class_files:
        return NativeImageEligibility(False)

    replacement = (
        netty_classes_replacement(group, artifact, pom_metadata)
        or jvm_artifact_replacement(artifact)
    )

    if any(name.endswith(".sjsir") for name in names):
        return NativeImageEligibility(
            True,
            "Artifact JAR contains Scala.js IR and no JVM class files.",
            replacement,
        )

    if any(name.endswith(".klib") or name.startswith("default/linkdata/") for name in names):
        return NativeImageEligibility(
            True,
            "Artifact contains Kotlin Native metadata and no JVM class files.",
            replacement,
        )

    module_info_files = [
        name for name in names
        if name.endswith("module-info.class")
    ]
    if module_info_files:
        reason = (
            "Artifact JAR contains no application/library JVM class files beyond "
            "module-info.class, so there is no library code for native-image metadata."
        )
    else:
        reason = (
            "Artifact JAR contains no JVM class files, so there is no library code "
            "for native-image metadata."
        )

    return NativeImageEligibility(
        True,
        reason,
        replacement,
    )


def read_maven_packaging(pom_url: str) -> str | None:
    """Read the Maven POM packaging value, defaulting to jar when omitted."""
    metadata = read_maven_pom_metadata(pom_url)
    return metadata.packaging if metadata else None


def read_maven_pom_metadata(pom_url: str) -> MavenPomMetadata | None:
    """Read the Maven POM packaging and direct dependencies."""
    pom_bytes = fetch_url(pom_url)
    if pom_bytes is None:
        return None
    try:
        root = ET.fromstring(pom_bytes.read())
    except ET.ParseError:
        return None
    namespace_match = re.match(r"\{.*\}", root.tag)
    namespace = namespace_match.group(0) if namespace_match else ""
    packaging = root.findtext(f"{namespace}packaging")
    dependencies: list[MavenDependency] = []
    for dependency in root.findall(f"{namespace}dependencies/{namespace}dependency"):
        dependency_group = _find_stripped_text(dependency, namespace, "groupId")
        dependency_artifact = _find_stripped_text(dependency, namespace, "artifactId")
        dependency_version = _find_stripped_text(dependency, namespace, "version")
        if not dependency_group or not dependency_artifact or not dependency_version:
            continue
        dependencies.append(
            MavenDependency(
                dependency_group,
                dependency_artifact,
                dependency_version,
                _find_stripped_text(dependency, namespace, "scope"),
                _find_stripped_text(dependency, namespace, "classifier"),
            )
        )
    return MavenPomMetadata(packaging.strip() if packaging else "jar", tuple(dependencies))


def _find_stripped_text(element: ET.Element, namespace: str, child: str) -> str | None:
    """Return trimmed text for an XML child element."""
    text = element.findtext(f"{namespace}{child}")
    return text.strip() if text else None


def fetch_url(url: str):
    """Fetch a URL into an in-memory binary stream, returning None for missing artifacts."""
    try:
        with urllib.request.urlopen(url, timeout=20) as response:
            import io

            return io.BytesIO(response.read())
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        return None
    except (OSError, urllib.error.URLError):
        return None


def jvm_artifact_replacement(artifact: str) -> str | None:
    """Suggest the likely JVM artifact when a platform suffix is obvious."""
    replacements = [
        r"_sjs\d+(?:\.\d+)?$",
        r"-(?:js|wasm-js|wasm-wasi)$",
        r"-(?:iosarm64|iosx64|iossimulatorarm64|linuxx64|linuxarm64|macosx64|macosarm64|mingwx64)$",
        r"-(?:tvosarm64|tvosx64|tvossimulatorarm64|watchosarm32|watchosarm64|watchosdevicearm64|watchossimulatorarm64)$",
    ]
    for pattern in replacements:
        replacement = re.sub(pattern, "", artifact)
        if replacement != artifact and replacement:
            return f"Use `{replacement}` if a JVM artifact is available."
    return None


def netty_classes_replacement(
        group: str,
        artifact: str,
        pom_metadata: MavenPomMetadata | None,
) -> str | None:
    """Infer Netty native package replacements from compile dependencies."""
    if pom_metadata is None or not group.startswith("io.netty"):
        return None
    if "-native" not in artifact and "tcnative" not in artifact:
        return None

    for dependency in pom_metadata.dependencies:
        if dependency.group != group:
            continue
        if dependency.artifact == artifact or "-classes" not in dependency.artifact:
            continue
        if dependency.classifier:
            continue
        if dependency.scope not in (None, "", "compile"):
            continue
        return f"{dependency.group}:{dependency.artifact}:{dependency.version}"
    return None
