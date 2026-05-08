/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import io.smallrye.config.common.AbstractDelegatingConverter;
import io.smallrye.config.common.AbstractSimpleDelegatingConverter;
import io.smallrye.config.common.MapBackedConfigSource;
import io.smallrye.config.common.utils.ConfigSourceUtil;
import io.smallrye.config.common.utils.StringUtil;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Smallrye_config_commonTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mapBackedConfigSourceUsesOrdinalNameAndReadOnlyProperties() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("server.host", "localhost");
        values.put("server.port", "8080");

        TestConfigSource source = new TestConfigSource("test-source", values);

        assertThat(source.getName()).isEqualTo("test-source");
        assertThat(source.toString()).isEqualTo("test-source");
        assertThat(source.getOrdinal()).isEqualTo(100);
        assertThat(source.getValue("server.host")).isEqualTo("localhost");
        assertThat(source.getPropertyNames()).containsExactly("server.host", "server.port");
        assertThat(source.getProperties()).containsEntry("server.port", "8080");
        assertThatThrownBy(() -> source.getProperties().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapBackedConfigSourceReadsOrdinalFromPropertiesAndHandlesProfiledNames() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ConfigSourceUtil.CONFIG_ORDINAL_KEY, "275");
        values.put("%dev.server.host", "dev.local");
        values.put("server.host", "localhost");

        TestConfigSource source = new TestConfigSource("profiled-source", values, 50);

        assertThat(source.getOrdinal()).isEqualTo(275);
        assertThat(source.getValue("%dev.server.host")).isEqualTo("dev.local");
        assertThat(ConfigSourceUtil.hasProfiledName(source.getPropertyNames())).isTrue();
    }

    @Test
    void mapBackedConfigSourceCanCopyInputProperties() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "original");

        TestConfigSource copy = new TestConfigSource("copy", values, 100, true);
        TestConfigSource view = new TestConfigSource("view", values, 100, false);
        values.put("name", "updated");

        assertThat(copy.getValue("name")).isEqualTo("original");
        assertThat(view.getValue("name")).isEqualTo("updated");
    }

    @Test
    void mapBackedConfigSourceFastRejectsProfiledLookupWhenNoProfiledNamesExist() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("server.host", "localhost");

        TestConfigSource source = new TestConfigSource("plain-source", values);
        values.put("%dev.server.host", "dev.local");

        assertThat(source.getValue("%dev.server.host")).isNull();
        assertThat(source.getValue("server.host")).isEqualTo("localhost");
    }

    @Test
    void configSourceUtilConvertsPropertiesAndUrlsToMaps() throws IOException {
        Properties properties = new Properties();
        properties.put("alpha", "one");
        properties.put("numeric", 123);

        assertThat(ConfigSourceUtil.propertiesToMap(properties))
                .containsEntry("alpha", "one")
                .containsEntry("numeric", "123");

        Path file = temporaryDirectory.resolve("config.properties");
        Files.writeString(file, "app.name=smallrye\nconfig_ordinal=450\n");
        URL url = file.toUri().toURL();

        Map<String, String> loaded = ConfigSourceUtil.urlToMap(url);
        assertThat(loaded).containsEntry("app.name", "smallrye");
        assertThat(ConfigSourceUtil.getOrdinalFromMap(loaded, 100)).isEqualTo(450);
        assertThat(ConfigSourceUtil.getOrdinalFromMap(Map.of("app.name", "smallrye"), 100)).isEqualTo(100);
        assertThat(ConfigSourceUtil.hasProfiledName(loaded.keySet())).isFalse();
    }

    @Test
    void configSourceUtilSkipsPropertiesThatCannotBeConvertedToStrings() {
        Properties properties = new Properties();
        properties.put("kept.key", "kept-value");
        properties.put(new UnstringifiableObject(), "ignored-value");
        properties.put("ignored.key", new UnstringifiableObject());

        assertThat(ConfigSourceUtil.propertiesToMap(properties))
                .containsOnly(Map.entry("kept.key", "kept-value"));
    }

    @Test
    void stringUtilSplitsCommaSeparatedValuesWithEscapes() {
        assertThat(StringUtil.split(null)).isEmpty();
        assertThat(StringUtil.split("")).isEmpty();
        assertThat(StringUtil.split("alpha,beta,gamma")).containsExactly("alpha", "beta", "gamma");
        assertThat(StringUtil.split("alpha,,beta\\,gamma,delta\\\\omega"))
                .containsExactly("alpha", "beta,gamma", "delta\\omega");
    }

    @Test
    void stringUtilNormalizesCharactersForEnvironmentStyleNames() {
        assertThat(StringUtil.isAsciiLetterOrDigit('A')).isTrue();
        assertThat(StringUtil.isAsciiLetterOrDigit('9')).isTrue();
        assertThat(StringUtil.isAsciiLetterOrDigit('_')).isFalse();
        assertThat(StringUtil.isAsciiLetterOrDigit('\u00e9')).isFalse();

        assertThat(StringUtil.replaceNonAlphanumericByUnderscores("server.host[0]"))
                .isEqualTo("server_host_0_");
        assertThat(StringUtil.replaceNonAlphanumericByUnderscores("quoted\"")).isEqualTo("quoted__");

        StringBuilder builder = new StringBuilder("prefix_");
        assertThat(StringUtil.replaceNonAlphanumericByUnderscores("a-b", builder)).isEqualTo("prefix_a_b");

        StringUtil.ResizableByteArray bytes = new StringUtil.ResizableByteArray(1);
        assertThat(StringUtil.replaceNonAlphanumericByUnderscores("x.y", bytes)).isEqualTo("x_y");
        assertThat(StringUtil.replaceNonAlphanumericByUnderscores("longer.value", bytes)).isEqualTo("longer_value");
    }

    @Test
    void stringUtilConvertsEnvironmentNamesToDottedPropertyNames() {
        assertThat(StringUtil.toLowerCaseAndDotted("")).isEmpty();
        assertThat(StringUtil.toLowerCaseAndDotted("SERVER_HOST")).isEqualTo("server.host");
        assertThat(StringUtil.toLowerCaseAndDotted("_DEV_SERVER_HOST")).isEqualTo("%dev.server.host");
        assertThat(StringUtil.toLowerCaseAndDotted("SERVERS_0_HOST")).isEqualTo("servers[0]host");
        assertThat(StringUtil.toLowerCaseAndDotted("QUOTED__VALUE__")).isEqualTo("quoted.\"value\"");
    }

    @Test
    void stringUtilChecksPathsAndNumericSegments() {
        assertThat(StringUtil.isInPath("", "server.host")).isTrue();
        assertThat(StringUtil.isInPath("server", "server.host")).isTrue();
        assertThat(StringUtil.isInPath("server", "server[0].host")).isTrue();
        assertThat(StringUtil.isInPath("server-host", "server.host.name")).isTrue();
        assertThat(StringUtil.isInPath("server", "serverhost")).isFalse();

        assertThat(StringUtil.isNumeric("12345")).isTrue();
        assertThat(StringUtil.isNumeric("12a45")).isFalse();
        assertThat(StringUtil.isNumeric("prefix123suffix", 6, 3)).isTrue();
        assertThat(StringUtil.isNumeric("prefix123suffix", 6, 4)).isFalse();
        assertThat(StringUtil.isNumericEquals("00123", 2, 3, "abc123xyz", 3, 3)).isTrue();
        assertThat(StringUtil.isNumericEquals("123", "124")).isFalse();
    }

    @Test
    void stringUtilHandlesQuotedIndexedAndSkeweredNames() {
        assertThat(StringUtil.unquoted("\"server.host\"")).isEqualTo("server.host");
        assertThat(StringUtil.unquoted("prefix.\"server.host\"", 7)).isEqualTo("server.host");
        assertThat(StringUtil.unquoted("prefix.\"server.host\".suffix", 7, 20)).isEqualTo("server.host");
        assertThat(StringUtil.unquoted("server.host")).isEqualTo("server.host");
        assertThatThrownBy(() -> StringUtil.unquoted("server", -1, 2))
                .isInstanceOf(StringIndexOutOfBoundsException.class);

        assertThat(StringUtil.index("servers[12]")).isEqualTo(12);
        assertThat(StringUtil.unindexed("servers[12]")).isEqualTo("servers");
        assertThat(StringUtil.unindexed("servers[name]")).isEqualTo("servers[name]");
        assertThatThrownBy(() -> StringUtil.index("servers[name]"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(StringUtil.skewer("myConfigValue")).isEqualTo("my-config-value");
        assertThat(StringUtil.skewer("HTTPServerPort", '_')).isEqualTo("http_server_port");
        assertThat(StringUtil.skewer("server.hosts[0].dnsName"))
                .isEqualTo("server.hosts[0].dns-name");
    }

    @Test
    void stringUtilSkewersWildcardPropertyPaths() {
        assertThat(StringUtil.skewer("foo.barBaz[*]")).isEqualTo("foo.bar-baz[*]");
        assertThat(StringUtil.skewer("serviceRoutes[*].targetURL")).isEqualTo("service-routes[*].target-url");
        assertThat(StringUtil.skewer("serviceRoutes[*].targetURL", '_')).isEqualTo("service_routes[*].target_url");
    }

    @Test
    void abstractConvertersExposeAndUseTheirDelegates() {
        Converter<String> trimmingDelegate = value -> value == null ? null : value.trim();
        IntegerConverter integerConverter = new IntegerConverter(trimmingDelegate);

        assertThat(integerConverter.getDelegate()).isSameAs(trimmingDelegate);
        assertThat(integerConverter.convert(" 42 ")).isEqualTo(42);

        UppercaseConverter uppercaseConverter = new UppercaseConverter(trimmingDelegate);
        assertThat(uppercaseConverter.getDelegate()).isSameAs(trimmingDelegate);
        assertThat(uppercaseConverter.convert(" smallrye ")).isEqualTo("SMALLRYE");
    }

    private static final class UnstringifiableObject {
        @Override
        public String toString() {
            throw new IllegalStateException("This value should be ignored by propertiesToMap");
        }
    }

    private static final class TestConfigSource extends MapBackedConfigSource {
        private TestConfigSource(String name, Map<String, String> propertyMap) {
            super(name, propertyMap);
        }

        private TestConfigSource(String name, Map<String, String> propertyMap, int ordinal) {
            super(name, propertyMap, ordinal);
        }

        private TestConfigSource(String name, Map<String, String> propertyMap, int ordinal, boolean copy) {
            super(name, propertyMap, ordinal, copy);
        }
    }

    private static final class IntegerConverter extends AbstractDelegatingConverter<String, Integer> {
        private IntegerConverter(Converter<? extends String> delegate) {
            super(delegate);
        }

        @Override
        public Integer convert(String value) {
            String converted = getDelegate().convert(value);
            return converted == null ? null : Integer.valueOf(converted);
        }
    }

    private static final class UppercaseConverter extends AbstractSimpleDelegatingConverter<String> {
        private UppercaseConverter(Converter<? extends String> delegate) {
            super(delegate);
        }

        @Override
        public String convert(String value) {
            String converted = getDelegate().convert(value);
            return converted == null ? null : converted.toUpperCase(Locale.ROOT);
        }
    }
}
