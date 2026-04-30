/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_package_url.packageurl_java;

import java.util.Map;
import java.util.TreeMap;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import com.github.packageurl.PackageURLBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class Packageurl_javaTest {
    @Test
    void parsesMavenPackageUrlIntoComponentsAndCanonicalForm() throws Exception {
        PackageURL packageURL = new PackageURL(
                "pkg:maven/org.apache.commons/commons-lang3@3.12.0?type=jar&classifier=sources#META-INF/maven");

        assertThat(packageURL.getScheme()).isEqualTo("pkg");
        assertThat(packageURL.getType()).isEqualTo(PackageURL.StandardTypes.MAVEN);
        assertThat(packageURL.getNamespace()).isEqualTo("org.apache.commons");
        assertThat(packageURL.getName()).isEqualTo("commons-lang3");
        assertThat(packageURL.getVersion()).isEqualTo("3.12.0");
        assertThat(packageURL.getSubpath()).isEqualTo("META-INF/maven");
        assertThat(packageURL.getQualifiers()).containsExactly(
                entry("classifier", "sources"),
                entry("type", "jar"));
        assertThat(packageURL.canonicalize()).isEqualTo(
                "pkg:maven/org.apache.commons/commons-lang3@3.12.0?classifier=sources&type=jar#META-INF/maven");
        assertThat(packageURL.toString()).isEqualTo(packageURL.canonicalize());
    }

    @Test
    void decodesInputAndReencodesReservedCharactersDuringCanonicalization() throws Exception {
        PackageURL packageURL = new PackageURL(
                "pkg:npm/%40scope/my%20package@1.0.0%2Bmeta?download_url=https%3A%2F%2Fexample.com%2Farchive.tar.gz#dist/files");

        assertThat(packageURL.getNamespace()).isEqualTo("@scope");
        assertThat(packageURL.getName()).isEqualTo("my package");
        assertThat(packageURL.getVersion()).isEqualTo("1.0.0+meta");
        assertThat(packageURL.getQualifiers()).containsExactly(
                entry("download_url", "https://example.com/archive.tar.gz"));
        assertThat(packageURL.canonicalize()).isEqualTo(
                "pkg:npm/%40scope/my%20package@1.0.0%2Bmeta?download_url=https%3A%2F%2Fexample.com%2Farchive.tar.gz#dist/files");
        assertThat(PackageURL.uriDecode("name%20with%2Freserved%3Fcharacters")).isEqualTo(
                "name with/reserved?characters");
    }

    @Test
    void parsesQualifierValuesContainingEqualsSignsWithoutTruncatingThem() throws Exception {
        PackageURL packageURL = new PackageURL(
                "pkg:generic/acme/tokenized-package?checksum=sha256:abc=def=&signature=key=value==");

        assertThat(packageURL.getQualifiers()).containsExactly(
                entry("checksum", "sha256:abc=def="),
                entry("signature", "key=value=="));
        assertThat(packageURL.canonicalize()).isEqualTo(
                "pkg:generic/acme/tokenized-package?checksum=sha256%3Aabc%3Ddef%3D&signature=key%3Dvalue%3D%3D");
    }

    @Test
    void builderCreatesPackageUrlsAndExposesDefensiveQualifierCopies() throws Exception {
        PackageURLBuilder builder = PackageURLBuilder.aPackageURL()
                .withType(PackageURL.StandardTypes.DOCKER)
                .withNamespace("Library")
                .withName("Alpine")
                .withVersion("3.18")
                .withQualifier("arch", "x86_64")
                .withQualifier("repository_url", "registry.example.com/library/alpine")
                .withSubpath("layers/config");

        TreeMap<String, String> qualifiersSnapshot = builder.getQualifiers();
        qualifiersSnapshot.put("distro", "ignored");

        PackageURL packageURL = builder.build();

        assertThat(builder.getQualifier("arch")).isEqualTo("x86_64");
        assertThat(packageURL.getNamespace()).isEqualTo("Library");
        assertThat(packageURL.getName()).isEqualTo("Alpine");
        assertThat(packageURL.getQualifiers()).containsExactly(
                entry("arch", "x86_64"),
                entry("repository_url", "registry.example.com/library/alpine"));
        assertThat(packageURL.canonicalize()).isEqualTo(
                "pkg:docker/Library/Alpine@3.18?arch=x86_64&repository_url=registry.example.com%2Flibrary%2Falpine#layers/config");
        assertThatThrownBy(() -> packageURL.getQualifiers().put("distro", "alpine"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toBuilderAllowsTargetedChangesToExistingPackageUrl() throws Exception {
        PackageURL original = new PackageURL(
                "pkg:generic/acme/widget@1.0.0?checksum=sha256%3Aabc123&download_url=https%3A%2F%2Fexample.com%2Fwidget.zip#bin/widget");

        PackageURL changed = original.toBuilder()
                .withVersion("1.1.0")
                .withoutQualifier("download_url")
                .withQualifier("checksum", "sha256:def456")
                .build();
        PackageURL withoutQualifiers = changed.toBuilder()
                .withNoQualifiers()
                .build();

        assertThat(changed.canonicalize()).isEqualTo(
                "pkg:generic/acme/widget@1.1.0?checksum=sha256%3Adef456#bin/widget");
        assertThat(withoutQualifiers.getQualifiers()).isNull();
        assertThat(withoutQualifiers.canonicalize()).isEqualTo("pkg:generic/acme/widget@1.1.0#bin/widget");
        assertThat(original.canonicalize()).isEqualTo(
                "pkg:generic/acme/widget@1.0.0?checksum=sha256%3Aabc123&download_url=https%3A%2F%2Fexample.com%2Fwidget.zip#bin/widget");
    }

    @Test
    void constructorsNormalizeTypeSpecificNamespaceAndNameRules() throws Exception {
        PackageURL github = new PackageURL(PackageURL.StandardTypes.GITHUB, "Package-URL");
        PackageURL pypi = PackageURLBuilder.aPackageURL()
                .withType(PackageURL.StandardTypes.PYPI)
                .withNamespace("Example")
                .withName("My_Package")
                .withVersion("2.0")
                .build();
        PackageURL rpm = PackageURLBuilder.aPackageURL()
                .withType(PackageURL.StandardTypes.RPM)
                .withNamespace("FEDORA")
                .withName("OpenSSL")
                .build();

        assertThat(github.canonicalize()).isEqualTo("pkg:github/package-url");
        assertThat(pypi.getName()).isEqualTo("my-package");
        assertThat(pypi.canonicalize()).isEqualTo("pkg:pypi/Example/my-package@2.0");
        assertThat(rpm.getNamespace()).isEqualTo("fedora");
        assertThat(rpm.getName()).isEqualTo("OpenSSL");
    }

    @Test
    void coordinateAndCanonicalComparisonsHaveDifferentScopes() throws Exception {
        PackageURL first = new PackageURL(
                "pkg:maven/org.example/app@1.0.0?classifier=sources&type=jar#META-INF/native-image");
        PackageURL sameCoordinates = new PackageURL(
                "pkg:maven/org.example/app@1.0.0?type=jar&classifier=sources#different/path");
        PackageURL differentVersion = new PackageURL("pkg:maven/org.example/app@1.1.0?classifier=sources&type=jar");

        assertThat(first.getCoordinates()).isEqualTo("pkg:maven/org.example/app@1.0.0");
        assertThat(first.isCoordinatesEquals(sameCoordinates)).isTrue();
        assertThat(first.isCoordinatesEquals(differentVersion)).isFalse();
        assertThat(first.isCanonicalEquals(sameCoordinates)).isFalse();
        assertThat(first).isNotEqualTo(sameCoordinates);
    }

    @Test
    void canonicalComparisonIgnoresQualifierInputOrder() throws Exception {
        PackageURL first = new PackageURL("pkg:generic/example/tool@1?a=1&b=2&c=3");
        PackageURL second = new PackageURL("pkg:generic/example/tool@1?c=3&a=1&b=2");

        assertThat(first).isEqualTo(second);
        assertThat(first.isCanonicalEquals(second)).isTrue();
        assertThat(first.canonicalize()).isEqualTo("pkg:generic/example/tool@1?a=1&b=2&c=3");
    }

    @Test
    void parsesQualifierKeysCaseInsensitivelyAndCanonicalizesThem() throws Exception {
        PackageURL packageURL = new PackageURL(
                "pkg:generic/example/tool@1?Download_URL=archive&Build=release");

        assertThat(packageURL.getQualifiers()).containsExactly(
                entry("build", "release"),
                entry("download_url", "archive"));
        assertThat(packageURL.canonicalize()).isEqualTo(
                "pkg:generic/example/tool@1?build=release&download_url=archive");
    }

    @Test
    void validatesPackageUrlSpecificationConstraints() {
        Map<String, ThrowingFactory> invalidInputs = Map.of(
                "maven package URLs require a namespace", () -> new PackageURL("pkg:maven/commons-lang3@3.12.0"),
                "types cannot start with a number", () -> new PackageURL("pkg:1invalid/name"),
                "qualifier keys must use allowed characters", () -> PackageURLBuilder.aPackageURL()
                        .withType(PackageURL.StandardTypes.GENERIC)
                        .withName("component")
                        .withQualifier("bad key", "value")
                        .build(),
                "qualifier values cannot be empty", () -> PackageURLBuilder.aPackageURL()
                        .withType(PackageURL.StandardTypes.GENERIC)
                        .withName("component")
                        .withQualifier("empty", "")
                        .build(),
                "constructor subpaths cannot contain traversal segments", () -> PackageURLBuilder.aPackageURL()
                        .withType(PackageURL.StandardTypes.GENERIC)
                        .withName("component")
                        .withSubpath("safe/../secret")
                        .build(),
                "purls require the pkg scheme", () -> new PackageURL("generic/name"));

        invalidInputs.forEach((description, factory) -> assertThatExceptionOfType(MalformedPackageURLException.class)
                .as(description)
                .isThrownBy(factory::create));
    }

    @FunctionalInterface
    private interface ThrowingFactory {
        Object create() throws MalformedPackageURLException;
    }
}
