# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import unittest
import urllib.error
from unittest.mock import patch
import zipfile

from utility_scripts import native_image_artifact as nia


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


if __name__ == "__main__":
    unittest.main()


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
