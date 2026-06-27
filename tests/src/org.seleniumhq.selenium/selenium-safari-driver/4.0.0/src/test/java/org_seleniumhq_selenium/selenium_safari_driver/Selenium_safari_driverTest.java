/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_safari_driver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.safari.ConnectionClosedException;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariDriverInfo;
import org.openqa.selenium.safari.SafariDriverService;
import org.openqa.selenium.safari.SafariOptions;
import org.openqa.selenium.safari.SafariTechPreviewDriverInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class Selenium_safari_driverTest {
    private static final String SAFARI_OPTIONS_CAPABILITY = "safari.options";

    @Test
    void safariOptionsExposeDefaultCapabilitiesAndSafariSpecificOptions() {
        SafariOptions options = new SafariOptions();

        assertThat(options.getBrowserName()).isEqualTo("safari");
        assertThat(options.getUseTechnologyPreview()).isFalse();
        assertThat(options.getAutomaticInspection()).isFalse();
        assertThat(options.getAutomaticProfiling()).isFalse();
        assertThat(options.asMap())
                .containsEntry("browserName", "safari")
                .containsKey(SAFARI_OPTIONS_CAPABILITY);
        assertThat(safariOptionsMap(options)).containsEntry("technologyPreview", false);

        assertThat(options.setAutomaticInspection(true)).isSameAs(options);
        assertThat(options.setAutomaticProfiling(true)).isSameAs(options);
        assertThat(options.setUseTechnologyPreview(true)).isSameAs(options);

        assertThat(options.getBrowserName()).isEqualTo("Safari Technology Preview");
        assertThat(options.getUseTechnologyPreview()).isTrue();
        assertThat(options.getAutomaticInspection()).isTrue();
        assertThat(options.getAutomaticProfiling()).isTrue();
        assertThat(options.asMap())
                .containsEntry("safari:automaticInspection", true)
                .containsEntry("safari:automaticProfiling", true);
        assertThat(safariOptionsMap(options)).containsEntry("technologyPreview", true);
    }

    @Test
    void safariOptionsAcceptSafariSpecificCapabilitiesThroughSetCapabilityOverloads() {
        SafariOptions options = new SafariOptions();

        options.setCapability("safari:automaticInspection", true);
        options.setCapability("safari:automaticProfiling", Boolean.TRUE);

        assertThat(options.getAutomaticInspection()).isTrue();
        assertThat(options.getAutomaticProfiling()).isTrue();
        assertThat(options.asMap())
                .containsEntry("safari:automaticInspection", true)
                .containsEntry("safari:automaticProfiling", true);
    }

    @Test
    void safariOptionsAllowTechnologyPreviewCapabilityToBeSetDirectly() {
        SafariOptions options = new SafariOptions();

        options.setCapability("technologyPreview", true);
        assertThat(options.getUseTechnologyPreview()).isTrue();
        assertThat(options.getBrowserName()).isEqualTo("Safari Technology Preview");
        assertThat(safariOptionsMap(options)).containsEntry("technologyPreview", true);
        assertThat(options.asMap()).doesNotContainKey("technologyPreview");

        options.setCapability("technologyPreview", Boolean.FALSE);
        assertThat(options.getUseTechnologyPreview()).isFalse();
        assertThat(options.getBrowserName()).isEqualTo("safari");
        assertThat(safariOptionsMap(options)).containsEntry("technologyPreview", false);
    }

    @Test
    void safariOptionsCanBeCreatedFromCapabilitiesAndNestedSafariOptionsMap() {
        SafariOptions original = new SafariOptions().setUseTechnologyPreview(true);
        assertThat(SafariOptions.fromCapabilities(original)).isSameAs(original);

        MutableCapabilities nestedOptions = new MutableCapabilities();
        nestedOptions.setCapability(SAFARI_OPTIONS_CAPABILITY, original);
        SafariOptions fromNestedOptions = SafariOptions.fromCapabilities(nestedOptions);
        assertThat(fromNestedOptions.getUseTechnologyPreview()).isTrue();
        assertThat(fromNestedOptions.getBrowserName()).isEqualTo("Safari Technology Preview");

        MutableCapabilities jsonLikeCapabilities = new MutableCapabilities();
        jsonLikeCapabilities.setCapability(SAFARI_OPTIONS_CAPABILITY, ImmutableMap.of("technologyPreview", true));
        SafariOptions fromJsonLikeMap = SafariOptions.fromCapabilities(jsonLikeCapabilities);

        assertThat(fromJsonLikeMap).isNotSameAs(original);
        assertThat(fromJsonLikeMap.getUseTechnologyPreview()).isTrue();
        assertThat(fromJsonLikeMap.getBrowserName()).isEqualTo("Safari Technology Preview");
        assertThat(safariOptionsMap(fromJsonLikeMap)).containsEntry("technologyPreview", true);
    }

    @Test
    void safariOptionsConstructorCopiesCapabilitiesAndMergeKeepsSafariOptionsType() {
        MutableCapabilities source = new MutableCapabilities();
        source.setCapability("custom:capability", "custom-value");
        source.setCapability(SAFARI_OPTIONS_CAPABILITY, ImmutableMap.of("technologyPreview", true));

        SafariOptions copied = new SafariOptions(source);
        assertThat(copied.getCapability("custom:capability")).isEqualTo("custom-value");
        assertThat(copied.getUseTechnologyPreview()).isTrue();

        Capabilities merged = copied.merge(new ImmutableCapabilities("another:capability", 42));
        assertThat(merged).isSameAs(copied);
        assertThat(merged.getCapability("another:capability")).isEqualTo(42);
    }

    @Test
    void safariOptionsSupportProxyCapability() {
        Proxy proxy = new Proxy();
        proxy.setHttpProxy("localhost:8080");

        SafariOptions options = new SafariOptions();
        assertThat(options.setProxy(proxy)).isSameAs(options);

        assertThat(options.getCapability("proxy")).isSameAs(proxy);
        assertThat(options.asMap()).containsEntry("proxy", proxy);
    }

    @Test
    void safariOptionsEqualityAndHashCodeIncludeTechnologyPreviewSetting() {
        SafariOptions standard = new SafariOptions();
        SafariOptions equivalentStandard = new SafariOptions().setUseTechnologyPreview(false);
        SafariOptions technologyPreview = new SafariOptions().setUseTechnologyPreview(true);

        assertThat(equivalentStandard).isEqualTo(standard);
        assertThat(equivalentStandard).hasSameHashCodeAs(standard);
        assertThat(technologyPreview).isNotEqualTo(standard);
        assertThat(technologyPreview.hashCode()).isNotEqualTo(standard.hashCode());
    }

    @Test
    void safariDriverInfoDescribesStandardSafariSupport() {
        SafariDriverInfo info = new SafariDriverInfo();

        assertThat(info.getDisplayName()).isEqualTo("Safari");
        assertThat(info.getMaximumSimultaneousSessions()).isEqualTo(1);
        assertThat(info.getCanonicalCapabilities().getBrowserName()).isEqualTo("safari");
        assertThat(info.isSupporting(new SafariOptions())).isTrue();
        assertThat(info.isSupporting(new ImmutableCapabilities("safari:automaticInspection", true))).isTrue();
        assertThat(info.isSupporting(new ImmutableCapabilities("safari.extension", "enabled"))).isTrue();
        assertThat(info.isSupporting(new ImmutableCapabilities("browserName", "firefox"))).isFalse();
        assertThatCode(() -> {
            info.isAvailable();
        }).doesNotThrowAnyException();
    }

    @Test
    void safariTechPreviewDriverInfoDescribesTechnologyPreviewSupport() {
        SafariTechPreviewDriverInfo info = new SafariTechPreviewDriverInfo();
        SafariOptions technologyPreview = new SafariOptions().setUseTechnologyPreview(true);

        assertThat(info.getDisplayName()).isEqualTo("Safari Technology Preview");
        assertThat(info.getMaximumSimultaneousSessions()).isEqualTo(1);
        assertThat(info.getCanonicalCapabilities().getBrowserName()).isEqualTo("Safari Technology Preview");
        assertThat(info.isSupporting(technologyPreview)).isTrue();
        assertThat(info.isSupporting(new ImmutableCapabilities("safari:automaticProfiling", true))).isTrue();
        assertThat(info.isSupporting(new ImmutableCapabilities("browserName", "safari"))).isFalse();
        assertThatCode(() -> {
            info.isAvailable();
        }).doesNotThrowAnyException();
    }

    @Test
    void safariDriverServiceBuilderScoresSafariCapabilities() {
        SafariDriverService.Builder builder = new SafariDriverService.Builder();

        assertThat(builder.score(new ImmutableCapabilities("browserName", "firefox"))).isZero();
        assertThat(builder.score(new SafariOptions())).isEqualTo(1);
        assertThat(builder.score(new SafariOptions().setUseTechnologyPreview(true))).isEqualTo(1);

        MutableCapabilities richSafariCapabilities = new MutableCapabilities();
        richSafariCapabilities.setCapability("browserName", "safari");
        richSafariCapabilities.setCapability(SAFARI_OPTIONS_CAPABILITY, ImmutableMap.of("technologyPreview", false));
        richSafariCapabilities.setCapability("se:safari:techPreview", false);
        assertThat(builder.score(richSafariCapabilities)).isEqualTo(3);
    }

    @Test
    void safariDriverServiceCanBeConstructedWithoutStartingExternalProcess() throws IOException {
        File executable = Files.createTempFile("safaridriver-test", ".sh").toFile();
        executable.deleteOnExit();
        assertThat(executable.setExecutable(true)).isTrue();

        SafariDriverService service = new SafariDriverService(
                executable,
                0,
                ImmutableList.of("--port", "0"),
                ImmutableMap.of("SAFARI_DRIVER_TEST", "true"));

        assertThat(service.getUrl().getProtocol()).isEqualTo("http");
        assertThat(service.getUrl().getHost()).isNotBlank();
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void safariSpecificExceptionsAndEnumsAreUsable() {
        WebDriverException exception = new ConnectionClosedException("connection closed by peer");

        assertThat(exception).isInstanceOf(ConnectionClosedException.class);
        assertThat(exception.getMessage()).contains("connection closed by peer");
        assertThat(SafariDriver.WindowType.values())
                .containsExactly(SafariDriver.WindowType.TAB, SafariDriver.WindowType.WINDOW);
        assertThat(SafariDriver.WindowType.valueOf("TAB")).isEqualTo(SafariDriver.WindowType.TAB);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> safariOptionsMap(SafariOptions options) {
        return (Map<String, Object>) options.asMap().get(SAFARI_OPTIONS_CAPABILITY);
    }
}
