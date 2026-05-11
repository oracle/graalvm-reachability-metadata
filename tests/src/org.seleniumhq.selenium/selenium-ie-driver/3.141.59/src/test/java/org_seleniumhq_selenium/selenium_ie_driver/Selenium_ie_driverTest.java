/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_ie_driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.ie.ElementScrollBehavior;
import org.openqa.selenium.ie.InternetExplorerDriverInfo;
import org.openqa.selenium.ie.InternetExplorerDriverLogLevel;
import org.openqa.selenium.ie.InternetExplorerDriverService;
import org.openqa.selenium.ie.InternetExplorerOptions;

public class Selenium_ie_driverTest {
    private static final String IE_OPTIONS = "se:ieOptions";
    private static final String IE_BROWSER_NAME = "internet explorer";
    private static final String ATTACH_TIMEOUT = "browserAttachTimeout";
    private static final String CLEAN_SESSION = "ie.ensureCleanSession";
    private static final String COMMAND_SWITCHES = "ie.browserCommandLineSwitches";
    private static final String CREATE_PROCESS = "ie.forceCreateProcessApi";
    private static final String ELEMENT_SCROLL_BEHAVIOR = "elementScrollBehavior";
    private static final String ENABLE_FULL_PAGE_SCREENSHOT = "ie.enableFullPageScreenshot";
    private static final String ENABLE_PERSISTENT_HOVER = "enablePersistentHover";
    private static final String FILE_UPLOAD_DIALOG_TIMEOUT = "ie.fileUploadDialogTimeout";
    private static final String IGNORE_PROTECTED_MODE_SETTINGS = "ignoreProtectedModeSettings";
    private static final String IGNORE_ZOOM_SETTING = "ignoreZoomSetting";
    private static final String INITIAL_BROWSER_URL = "initialBrowserUrl";
    private static final String NATIVE_EVENTS = "nativeEvents";
    private static final String PER_PROCESS_PROXY = "ie.usePerProcessProxy";
    private static final String REQUIRE_WINDOW_FOCUS = "requireWindowFocus";
    private static final String SHELL_WINDOWS_API = "ie.forceShellWindowsApi";

    @Test
    void defaultOptionsExposeInternetExplorerBrowserAndVendorOptionsContainer() {
        InternetExplorerOptions options = new InternetExplorerOptions();

        assertThat(options.getBrowserName()).isEqualTo(IE_BROWSER_NAME);
        assertThat(options.getCapability("browserName")).isEqualTo(IE_BROWSER_NAME);
        assertThat(ieOptionsFrom(options)).isEmpty();
        assertThat(options.asMap()).containsKeys("browserName", IE_OPTIONS);
    }

    @Test
    void fluentOptionSettersPopulateTopLevelAndIeVendorCapabilities() {
        Proxy proxy = new Proxy().setHttpProxy("proxy.example.test:8080");
        InternetExplorerOptions options = new InternetExplorerOptions();

        InternetExplorerOptions returned = options
                .withAttachTimeout(Duration.ofMillis(250))
                .elementScrollTo(ElementScrollBehavior.BOTTOM)
                .enablePersistentHovering()
                .useCreateProcessApiToLaunchIe()
                .useShellWindowsApiToAttachToIe()
                .destructivelyEnsureCleanSession()
                .addCommandSwitches("-private", "-extoff")
                .addCommandSwitches("-k")
                .usePerProcessProxy()
                .withInitialBrowserUrl("about:blank")
                .requireWindowFocus()
                .waitForUploadDialogUpTo(Duration.ofSeconds(2))
                .introduceFlakinessByIgnoringSecurityDomains()
                .disableNativeEvents()
                .ignoreZoomSettings()
                .takeFullPageScreenshot()
                .setPageLoadStrategy(PageLoadStrategy.EAGER)
                .setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.ACCEPT)
                .setProxy(proxy);

        assertThat(returned).isSameAs(options);
        assertThat(options.getCapability(ATTACH_TIMEOUT)).isEqualTo(250L);
        assertThat(options.getCapability(ELEMENT_SCROLL_BEHAVIOR)).isEqualTo(1);
        assertThat(options.getCapability(ENABLE_PERSISTENT_HOVER)).isEqualTo(true);
        assertThat(options.getCapability(CREATE_PROCESS)).isEqualTo(true);
        assertThat(options.getCapability(SHELL_WINDOWS_API)).isEqualTo(true);
        assertThat(options.getCapability(CLEAN_SESSION)).isEqualTo(true);
        assertThat(options.getCapability(COMMAND_SWITCHES)).isEqualTo(Arrays.asList("-private", "-extoff", "-k"));
        assertThat(options.getCapability(PER_PROCESS_PROXY)).isEqualTo(true);
        assertThat(options.getCapability(INITIAL_BROWSER_URL)).isEqualTo("about:blank");
        assertThat(options.getCapability(REQUIRE_WINDOW_FOCUS)).isEqualTo(true);
        assertThat(options.getCapability(FILE_UPLOAD_DIALOG_TIMEOUT)).isEqualTo(2000L);
        assertThat(options.getCapability(IGNORE_PROTECTED_MODE_SETTINGS)).isEqualTo(true);
        assertThat(options.getCapability(NATIVE_EVENTS)).isEqualTo(false);
        assertThat(options.getCapability(IGNORE_ZOOM_SETTING)).isEqualTo(true);
        assertThat(options.getCapability(ENABLE_FULL_PAGE_SCREENSHOT)).isEqualTo(true);
        assertThat(options.getCapability("pageLoadStrategy")).isEqualTo(PageLoadStrategy.EAGER);
        assertThat(options.getCapability("unhandledPromptBehavior")).isEqualTo(UnexpectedAlertBehaviour.ACCEPT);
        assertThat(options.getCapability("proxy")).isSameAs(proxy);

        Map<String, Object> ieOptions = ieOptionsFrom(options);
        assertThat(ieOptions.get(COMMAND_SWITCHES).toString()).contains("-private", "-extoff", "-k");
        assertThat(ieOptions).containsEntry(NATIVE_EVENTS, false);
        assertThat(ieOptions).containsEntry(IGNORE_ZOOM_SETTING, true);
        assertThat(ieOptions).doesNotContainKey("pageLoadStrategy");
        assertThat(ieOptions).doesNotContainKey("proxy");
    }

    @Test
    void settingIeOptionsCapabilityExpandsKnownNonNullEntries() {
        InternetExplorerOptions options = new InternetExplorerOptions();

        options.setCapability(IE_OPTIONS, Map.of(
                IGNORE_ZOOM_SETTING, true,
                NATIVE_EVENTS, false,
                "unknown.ie.option", "ignored"));

        assertThat(options.getCapability(IGNORE_ZOOM_SETTING)).isEqualTo(true);
        assertThat(options.getCapability(NATIVE_EVENTS)).isEqualTo(false);
        assertThat(options.getCapability("unknown.ie.option")).isNull();

        MutableCapabilities nestedCapabilities = new MutableCapabilities();
        nestedCapabilities.setCapability(INITIAL_BROWSER_URL, "about:tabs");
        nestedCapabilities.setCapability(REQUIRE_WINDOW_FOCUS, true);
        options.setCapability(IE_OPTIONS, nestedCapabilities);

        assertThat(options.getCapability(INITIAL_BROWSER_URL)).isEqualTo("about:tabs");
        assertThat(options.getCapability(REQUIRE_WINDOW_FOCUS)).isEqualTo(true);
    }

    @Test
    void settingInvalidIeOptionsCapabilityFailsFast() {
        InternetExplorerOptions options = new InternetExplorerOptions();

        assertThatThrownBy(() -> options.setCapability(IE_OPTIONS, "not-a-map"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(IE_OPTIONS);
    }

    @Test
    void constructedAndMergedOptionsKeepInternetExplorerSpecificCapabilities() {
        MutableCapabilities sourceCapabilities = new MutableCapabilities();
        sourceCapabilities.setCapability(INITIAL_BROWSER_URL, "about:blank");
        sourceCapabilities.setCapability(IGNORE_ZOOM_SETTING, true);

        InternetExplorerOptions constructed = new InternetExplorerOptions(sourceCapabilities);
        InternetExplorerOptions merged = new InternetExplorerOptions().merge(sourceCapabilities);

        assertThat(constructed.getBrowserName()).isEqualTo(IE_BROWSER_NAME);
        assertThat(constructed.getCapability(INITIAL_BROWSER_URL)).isEqualTo("about:blank");
        assertThat(constructed.getCapability(IGNORE_ZOOM_SETTING)).isEqualTo(true);
        assertThat(merged.getBrowserName()).isEqualTo(IE_BROWSER_NAME);
        assertThat(merged.getCapability(INITIAL_BROWSER_URL)).isEqualTo("about:blank");
        assertThat(merged.getCapability(IGNORE_ZOOM_SETTING)).isEqualTo(true);
    }

    @Test
    void elementScrollBehaviorConvertsBetweenEnumAndDriverWireValues() {
        assertThat(ElementScrollBehavior.TOP.getValue()).isZero();
        assertThat(ElementScrollBehavior.TOP.toString()).isEqualTo("0");
        assertThat(ElementScrollBehavior.BOTTOM.getValue()).isOne();
        assertThat(ElementScrollBehavior.BOTTOM.toString()).isEqualTo("1");
        assertThat(ElementScrollBehavior.fromString("0")).isSameAs(ElementScrollBehavior.TOP);
        assertThat(ElementScrollBehavior.fromString("1")).isSameAs(ElementScrollBehavior.BOTTOM);
        assertThat(ElementScrollBehavior.fromString("missing")).isNull();
    }

    @Test
    void logLevelEnumExposesAllDriverSupportedLevels() {
        assertThat(InternetExplorerDriverLogLevel.values())
                .containsExactly(
                        InternetExplorerDriverLogLevel.TRACE,
                        InternetExplorerDriverLogLevel.DEBUG,
                        InternetExplorerDriverLogLevel.INFO,
                        InternetExplorerDriverLogLevel.WARN,
                        InternetExplorerDriverLogLevel.ERROR,
                        InternetExplorerDriverLogLevel.FATAL);
        assertThat(InternetExplorerDriverLogLevel.valueOf("INFO")).isSameAs(InternetExplorerDriverLogLevel.INFO);
    }

    @Test
    void serviceBuilderScoresInternetExplorerCapabilities() {
        InternetExplorerDriverService.Builder builder = new InternetExplorerDriverService.Builder();
        MutableCapabilities unsupported = new MutableCapabilities();
        MutableCapabilities browserOnly = new MutableCapabilities();
        browserOnly.setCapability("browserName", IE_BROWSER_NAME);
        MutableCapabilities optionsOnly = new MutableCapabilities();
        optionsOnly.setCapability(IE_OPTIONS, Collections.singletonMap(IGNORE_ZOOM_SETTING, true));

        assertThat(builder.score(unsupported)).isZero();
        assertThat(builder.score(browserOnly)).isOne();
        assertThat(builder.score(optionsOnly)).isOne();
        assertThat(builder.score(new InternetExplorerOptions())).isEqualTo(2);
    }

    @Test
    void serviceBuilderCreatesServiceFromExplicitExecutableAndPort(@TempDir Path tempDirectory) throws Exception {
        Path executable = Files.createFile(tempDirectory.resolve("IEDriverServer"));
        assertThat(executable.toFile().setExecutable(true)).isTrue();

        InternetExplorerDriverService service = new InternetExplorerDriverService.Builder()
                .usingDriverExecutable(executable.toFile())
                .usingPort(12345)
                .withLogFile(tempDirectory.resolve("iedriver.log").toFile())
                .withLogLevel(InternetExplorerDriverLogLevel.INFO)
                .withHost("127.0.0.1")
                .withExtractPath(tempDirectory.toFile())
                .withSilent(true)
                .withEnvironment(Collections.singletonMap("IE_DRIVER_TEST_ENV", "enabled"))
                .build();

        assertThat(service.getUrl().toString()).isEqualTo("http://localhost:12345");
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void defaultServiceUsesExecutableConfiguredThroughSystemProperty(@TempDir Path tempDirectory) throws Exception {
        Path executable = Files.createFile(tempDirectory.resolve("IEDriverServer"));
        assertThat(executable.toFile().setExecutable(true)).isTrue();

        String propertyName = InternetExplorerDriverService.IE_DRIVER_EXE_PROPERTY;
        String previousValue = System.getProperty(propertyName);
        try {
            System.setProperty(propertyName, executable.toString());

            InternetExplorerDriverService service = InternetExplorerDriverService.createDefaultService();

            assertThat(service.getUrl().getProtocol()).isEqualTo("http");
            assertThat(service.getUrl().getHost()).isEqualTo("localhost");
            assertThat(service.getUrl().getPort()).isPositive();
            assertThat(service.isRunning()).isFalse();
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousValue);
            }
        }
    }

    @Test
    void driverInfoAdvertisesCanonicalInternetExplorerCapabilities() {
        InternetExplorerDriverInfo info = new InternetExplorerDriverInfo();
        MutableCapabilities unsupported = new MutableCapabilities();
        unsupported.setCapability("browserName", "firefox");
        MutableCapabilities optionsOnly = new MutableCapabilities();
        optionsOnly.setCapability(
                IE_OPTIONS,
                Collections.singletonMap(IGNORE_ZOOM_SETTING, true));

        Capabilities canonicalCapabilities = info.getCanonicalCapabilities();

        assertThat(info.getDisplayName()).isEqualTo("Internet Explorer");
        assertThat(canonicalCapabilities.getBrowserName()).isEqualTo(IE_BROWSER_NAME);
        assertThat(info.isSupporting(canonicalCapabilities)).isTrue();
        assertThat(info.isSupporting(new InternetExplorerOptions())).isTrue();
        assertThat(info.isSupporting(optionsOnly)).isTrue();
        assertThat(info.isSupporting(unsupported)).isFalse();
        assertThat(info.getMaximumSimultaneousSessions()).isOne();
        assertThatCode(info::isAvailable).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ieOptionsFrom(InternetExplorerOptions options) {
        Object ieOptions = options.getCapability(IE_OPTIONS);
        assertThat(ieOptions).isInstanceOf(Map.class);
        return (Map<String, Object>) ieOptions;
    }
}
