# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
from http.client import IncompleteRead
import unittest
import urllib.error
from unittest.mock import patch
import zipfile

from utility_scripts import native_image_artifact as nia
from utility_scripts.native_image_artifact import NativeImageEligibility


def _jar_with(*names: str) -> io.BytesIO:
    jar_bytes = io.BytesIO()
    with zipfile.ZipFile(jar_bytes, "w") as jar:
        for name in names:
            jar.writestr(name, b"")
    jar_bytes.seek(0)
    return jar_bytes


class NativeImageArtifactTests(unittest.TestCase):
    def test_module_info_only_artifact_uses_non_literal_no_class_reason(self) -> None:
        pom = """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.example</groupId>
  <artifactId>descriptor-only</artifactId>
  <version>1.0.0</version>
</project>
"""

        def fake_fetch_url(url: str) -> io.BytesIO | None:
            if url.endswith(".pom"):
                return io.BytesIO(pom.encode("utf-8"))
            if url.endswith(".jar"):
                return _jar_with("META-INF/versions/11/module-info.class")
            return None

        with patch.object(nia, "fetch_url", side_effect=fake_fetch_url):
            result = nia.inspect_maven_artifact("org.example:descriptor-only:1.0.0")

        self.assertTrue(result.not_for_native_image)
        self.assertIn("beyond module-info.class", result.reason or "")
        self.assertNotIn("contains no JVM class files", result.reason or "")
        self.assertIsNone(result.replacement)

    def test_netty_native_artifact_infers_classes_replacement_from_pom(self) -> None:
        pom = """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.netty</groupId>
  <artifactId>netty-tcnative-boringssl-static</artifactId>
  <version>2.0.73.Final</version>
  <dependencies>
    <dependency>
      <groupId>io.netty</groupId>
      <artifactId>netty-tcnative-classes</artifactId>
      <version>2.0.73.Final</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>io.netty</groupId>
      <artifactId>netty-jni-util</artifactId>
      <version>0.0.9.Final</version>
      <classifier>sources</classifier>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</project>
"""

        def fake_fetch_url(url: str) -> io.BytesIO | None:
            if url.endswith(".pom"):
                return io.BytesIO(pom.encode("utf-8"))
            if url.endswith(".jar"):
                return _jar_with(
                    "META-INF/versions/11/module-info.class",
                    "META-INF/maven/io.netty/netty-tcnative-boringssl-static/pom.xml",
                )
            return None

        with patch.object(nia, "fetch_url", side_effect=fake_fetch_url):
            result = nia.inspect_maven_artifact(
                "io.netty:netty-tcnative-boringssl-static:2.0.73.Final"
            )

        self.assertTrue(result.not_for_native_image)
        self.assertEqual(
            "io.netty:netty-tcnative-classes:2.0.73.Final",
            result.replacement,
        )


class PublishedContentTests(unittest.TestCase):
    """Content-only evidence and conservative defaults (§FS-not-for-native-image-record.1)."""

    def inspect(self, packaging: str | None = "jar", jar: bytes | Exception | None = None,
                coordinate: str = "org.example:lib:1.0.0", pom_extra: str = "") -> NativeImageEligibility:
        def fetch(url: str, timeout: int) -> io.BytesIO:
            self.assertEqual(20, timeout)
            if url.endswith(".pom") and packaging is not None:
                return io.BytesIO(f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
                    <packaging>{packaging}</packaging>{pom_extra}</project>""".encode("utf-8"))
            if url.endswith(".jar"):
                if isinstance(jar, Exception):
                    raise jar
                if jar is not None:
                    return io.BytesIO(jar)
            raise urllib.error.HTTPError(url, 404, "Not Found", {}, None)

        with patch.object(nia.urllib.request, "urlopen", side_effect=fetch):
            return nia.inspect_maven_artifact(coordinate, strict=True)

    def test_klib_and_aar_packaging_are_evidence(self) -> None:
        for packaging in ("klib", "aar"):
            with self.subTest(packaging=packaging):
                verdict: NativeImageEligibility = self.inspect(packaging)
                self.assertTrue(verdict.not_for_native_image)
                self.assertIn(f"`{packaging}`", verdict.reason or "")

    def test_pom_packaging_with_a_definitely_absent_jar_is_evidence(self) -> None:
        verdict: NativeImageEligibility = self.inspect("pom", coordinate="org.example:lib-js:1.0.0")
        self.assertTrue(verdict.not_for_native_image)
        self.assertIn("no artifact JAR is published", verdict.reason or "")
        self.assertEqual(nia.jvm_artifact_replacement("lib-js"), verdict.replacement)

    def test_a_relocation_pom_is_not_evidence(self) -> None:
        """The record would hide the moved artifact's earlier versions."""
        relocation: str = ("<distributionManagement><relocation><groupId>org.example.moved</groupId>"
                           "</relocation></distributionManagement>")
        for packaging in ("pom", "klib"):
            with self.subTest(packaging=packaging):
                self.assertFalse(self.inspect(packaging, pom_extra=relocation).not_for_native_image)

    def test_classless_jars_are_evidence(self) -> None:
        for names in (("META-INF/MANIFEST.MF",), ("META-INF/versions/11/module-info.class",)):
            with self.subTest(names=names):
                self.assertTrue(self.inspect(jar=_jar_with(*names).getvalue()).not_for_native_image)

    def test_a_js_name_with_a_real_jvm_jar_is_not_evidence(self) -> None:
        verdict: NativeImageEligibility = self.inspect(
            jar=_jar_with("org/jetbrains/kotlin/Plugin.class").getvalue(),
            coordinate="org.jetbrains.kotlin:kotlin-scripting-js:2.3.0")
        self.assertFalse(verdict.not_for_native_image)

    def test_missing_pom_and_unreadable_jar_are_undecided(self) -> None:
        self.assertFalse(self.inspect(None, _jar_with("README").getvalue()).not_for_native_image)
        self.assertFalse(self.inspect(jar=b"not a jar").not_for_native_image)

    def test_an_unreachable_repository_is_undecided(self) -> None:
        for failure in (urllib.error.URLError("no route"), IncompleteRead(b"<project>"),
                        urllib.error.HTTPError("https://example.invalid/lib.pom", 503, "Unavailable", {}, None)):
            with self.subTest(failure=failure):
                with patch.object(nia.urllib.request, "urlopen", side_effect=failure):
                    self.assertFalse(nia.inspect_maven_artifact("org.example:lib:1.0.0",
                                                               strict=True).not_for_native_image)

    def test_a_failed_jar_download_does_not_prove_a_pom_only_artifact(self) -> None:
        for failure in (urllib.error.URLError("no route"), OSError("connection reset"), IncompleteRead(b"PK"),
                        urllib.error.HTTPError("https://example.invalid/lib.jar", 503, "Unavailable", {}, None),
                        urllib.error.HTTPError("https://example.invalid/lib.jar", 403, "Forbidden", {}, None)):
            with self.subTest(failure=failure):
                self.assertFalse(self.inspect("pom", failure).not_for_native_image)

    def test_legacy_fetch_callers_still_receive_none_on_errors(self) -> None:
        for failure in (urllib.error.URLError("no route"),
                        urllib.error.HTTPError("https://example.invalid/lib.jar", 503, "Unavailable", {}, None)):
            with self.subTest(failure=failure):
                with patch.object(nia.urllib.request, "urlopen", side_effect=failure):
                    self.assertIsNone(nia.fetch_url("https://example.invalid/lib.jar"))


class ArtifactPublicationProbeTests(unittest.TestCase):
    """The existence probe behind the issue-form gate. §FS-forge-run-requirements.3"""

    def test_base_url_is_derived_entirely_from_the_coordinate(self) -> None:
        self.assertEqual(
            nia.artifact_base_url(nia.MAVEN_CENTRAL, "org.example.tools:widget:1.2.3"),
            f"{nia.MAVEN_CENTRAL}/org/example/tools/widget/1.2.3/widget-1.2.3",
        )

    def test_maven_central_hit_short_circuits_the_fallback(self) -> None:
        probed: list[str] = []

        def fake_probe(pom_url: str) -> bool:
            probed.append(pom_url)
            return True

        with patch.object(nia, "artifact_exists_in_repository", side_effect=fake_probe):
            self.assertIs(nia.artifact_is_published("org.example:widget:1.2.3"), True)

        self.assertEqual(len(probed), 1)
        self.assertTrue(probed[0].startswith(nia.MAVEN_CENTRAL))

    def test_confluent_fallback_answers_when_maven_central_has_nothing(self) -> None:
        answers = {nia.MAVEN_CENTRAL: False, nia.CONFLUENT_MAVEN: True}

        def fake_probe(pom_url: str) -> bool:
            return answers[nia.MAVEN_CENTRAL if pom_url.startswith(nia.MAVEN_CENTRAL) else nia.CONFLUENT_MAVEN]

        with patch.object(nia, "artifact_exists_in_repository", side_effect=fake_probe):
            self.assertIs(nia.artifact_is_published("org.example:widget:1.2.3"), True)

    def test_absent_from_every_repository_is_a_definite_no(self) -> None:
        with patch.object(nia, "artifact_exists_in_repository", return_value=False):
            self.assertIs(nia.artifact_is_published("org.example:widget:1.2.3"), False)

    def test_unreachable_repository_leaves_the_answer_undecided(self) -> None:
        def fake_probe(pom_url: str) -> bool | None:
            return False if pom_url.startswith(nia.MAVEN_CENTRAL) else None

        with patch.object(nia, "artifact_exists_in_repository", side_effect=fake_probe):
            self.assertIsNone(nia.artifact_is_published("org.example:widget:1.2.3"))

    def test_missing_pom_is_reported_as_absent_and_other_errors_as_undecided(self) -> None:
        def http_error(code: int) -> urllib.error.HTTPError:
            return urllib.error.HTTPError("https://example.invalid/a.pom", code, "boom", {}, None)

        with patch.object(nia.urllib.request, "urlopen", side_effect=http_error(404)):
            self.assertIs(nia.artifact_exists_in_repository("https://example.invalid/a.pom"), False)
        with patch.object(nia.urllib.request, "urlopen", side_effect=http_error(503)):
            self.assertIsNone(nia.artifact_exists_in_repository("https://example.invalid/a.pom"))
        with patch.object(nia.urllib.request, "urlopen", side_effect=OSError("no route")):
            self.assertIsNone(nia.artifact_exists_in_repository("https://example.invalid/a.pom"))


if __name__ == "__main__":
    unittest.main()
