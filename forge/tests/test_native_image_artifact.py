# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import io
import unittest
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
