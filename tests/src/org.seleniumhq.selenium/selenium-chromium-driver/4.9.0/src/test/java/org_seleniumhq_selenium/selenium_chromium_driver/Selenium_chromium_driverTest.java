/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_chromium_driver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.AddHasCasting;
import org.openqa.selenium.chromium.AddHasCdp;
import org.openqa.selenium.chromium.AddHasLaunchApp;
import org.openqa.selenium.chromium.AddHasNetworkConditions;
import org.openqa.selenium.chromium.AddHasPermissions;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.chromium.ChromiumDriverInfo;
import org.openqa.selenium.chromium.ChromiumDriverLogLevel;
import org.openqa.selenium.chromium.ChromiumNetworkConditions;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.chromium.HasCasting;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.chromium.HasLaunchApp;
import org.openqa.selenium.chromium.HasNetworkConditions;
import org.openqa.selenium.chromium.HasPermissions;
import org.openqa.selenium.remote.AdditionalHttpCommands;
import org.openqa.selenium.remote.Browser;
import org.openqa.selenium.remote.CommandInfo;
import org.openqa.selenium.remote.ExecuteMethod;
import org.openqa.selenium.remote.http.HttpMethod;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Selenium_chromium_driverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void shouldExposeChromiumBrowserPredicate() {
        assertThat(ChromiumDriver.IS_CHROMIUM_BROWSER.test(Browser.CHROME.browserName())).isTrue();
        assertThat(ChromiumDriver.IS_CHROMIUM_BROWSER.test(Browser.EDGE.browserName())).isTrue();
        assertThat(ChromiumDriver.IS_CHROMIUM_BROWSER.test("msedge")).isTrue();
        assertThat(ChromiumDriver.IS_CHROMIUM_BROWSER.test(Browser.OPERA.browserName())).isTrue();
        assertThat(ChromiumDriver.IS_CHROMIUM_BROWSER.test(Browser.FIREFOX.browserName())).isFalse();
    }

    @Test
    public void shouldMapJavaLogLevelsToChromiumLogLevels() {
        assertThat(ChromiumDriverLogLevel.ALL.toString()).isEqualTo("all");
        assertThat(ChromiumDriverLogLevel.fromString("DEBUG")).isEqualTo(ChromiumDriverLogLevel.DEBUG);
        assertThat(ChromiumDriverLogLevel.fromString("warning")).isEqualTo(ChromiumDriverLogLevel.WARNING);
        assertThat(ChromiumDriverLogLevel.fromString("missing")).isNull();
        assertThat(ChromiumDriverLogLevel.fromString(null)).isNull();

        assertThat(ChromiumDriverLogLevel.fromLevel(Level.ALL)).isEqualTo(ChromiumDriverLogLevel.ALL);
        assertThat(ChromiumDriverLogLevel.fromLevel(Level.FINEST)).isEqualTo(ChromiumDriverLogLevel.DEBUG);
        assertThat(ChromiumDriverLogLevel.fromLevel(Level.FINER)).isEqualTo(ChromiumDriverLogLevel.DEBUG);
        assertThat(ChromiumDriverLogLevel.fromLevel(Level.FINE)).isEqualTo(ChromiumDriverLogLevel.DEBUG);
        assertThat(ChromiumDriverLogLevel.fromLevel(Level.INFO)).isEqualTo(ChromiumDriverLogLevel.INFO);
        assertThat(ChromiumDriverLogLevel.fromLevel(Level.WARNING)).isEqualTo(ChromiumDriverLogLevel.WARNING);
        assertThat(ChromiumDriverLogLevel.fromLevel(Level.SEVERE)).isEqualTo(ChromiumDriverLogLevel.SEVERE);
        assertThat(ChromiumDriverLogLevel.fromLevel(Level.OFF)).isEqualTo(ChromiumDriverLogLevel.OFF);
        assertThat(ChromiumDriverLogLevel.fromLevel(Level.CONFIG)).isEqualTo(ChromiumDriverLogLevel.ALL);
    }

    @Test
    public void shouldCollectChromiumOptionsIntoVendorCapability() throws IOException {
        Path extension = temporaryDirectory.resolve("extension.crx");
        byte[] extensionBytes = "packed extension".getBytes(StandardCharsets.UTF_8);
        Files.write(extension, extensionBytes);

        ChromiumOptions<?> options = new ChromiumOptions<>("browserName", "chrome", "goog:chromeOptions");
        options.setBinary("/opt/chromium/chrome");
        options.addArguments("--headless=new", "--user-data-dir=/tmp/selenium-profile");
        options.addArguments(List.of("--remote-allow-origins=https://example.test"));
        options.addExtensions(extension.toFile());
        options.addEncodedExtensions("already-encoded");
        options.setExperimentalOption("prefs", Map.of("download.default_directory", "/tmp/downloads"));
        options.setAndroidPackage("org.chromium.chrome");
        options.setAndroidActivity("Main");
        options.setAndroidDeviceSerialNumber("emulator-5554");
        options.setAndroidProcess("org.chromium.chrome:sandboxed_process0");
        options.setUseRunningAndroidApp(true);

        Map<String, Object> chromiumOptions = chromiumOptions(options);

        assertThat(options.getBrowserName()).isEqualTo("chrome");
        assertThat(chromiumOptions)
            .containsEntry("binary", "/opt/chromium/chrome")
            .containsEntry("androidPackage", "org.chromium.chrome")
            .containsEntry("androidActivity", "Main")
            .containsEntry("androidDeviceSerial", "emulator-5554")
            .containsEntry("androidProcess", "org.chromium.chrome:sandboxed_process0")
            .containsEntry("androidUseRunningApp", true);
        assertThat(chromiumOptions.get("prefs"))
            .isEqualTo(Map.of("download.default_directory", "/tmp/downloads"));
        assertThat(optionList(chromiumOptions, "args"))
            .containsExactly("--headless=new", "--user-data-dir=/tmp/selenium-profile",
                "--remote-allow-origins=https://example.test");
        assertThat(optionList(chromiumOptions, "extensions"))
            .containsExactly(Base64.getEncoder().encodeToString(extensionBytes), "already-encoded");
    }

    @Test
    public void shouldRejectInvalidChromiumOptionsInputs() throws IOException {
        ChromiumOptions<?> options = new ChromiumOptions<>("browserName", "chrome", "goog:chromeOptions");
        Path directory = Files.createDirectory(temporaryDirectory.resolve("not-a-file"));

        assertThatThrownBy(() -> options.setBinary((String) null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> options.setBinary((File) null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> options.addEncodedExtensions((String) null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> options.addExtensions(directory.toFile())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> options.setExperimentalOption(null, true)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> options.setAndroidPackage(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> options.setAndroidActivity(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> options.setAndroidDeviceSerialNumber(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> options.setAndroidProcess(null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void shouldKeepDefaultNetworkConditionsAndAllowUpdates() {
        ChromiumNetworkConditions conditions = new ChromiumNetworkConditions();

        assertThat(conditions.getOffline()).isFalse();
        assertThat(conditions.getLatency()).isEqualTo(Duration.ZERO);
        assertThat(conditions.getDownloadThroughput()).isEqualTo(-1);
        assertThat(conditions.getUploadThroughput()).isEqualTo(-1);

        conditions.setOffline(true);
        conditions.setLatency(Duration.ofMillis(125));
        conditions.setDownloadThroughput(1024);
        conditions.setUploadThroughput(256);

        assertThat(conditions.getOffline()).isTrue();
        assertThat(conditions.getLatency()).isEqualTo(Duration.ofMillis(125));
        assertThat(conditions.getDownloadThroughput()).isEqualTo(1024);
        assertThat(conditions.getUploadThroughput()).isEqualTo(256);
    }

    @Test
    public void shouldImplementNetworkConditionCommands() {
        RecordingExecuteMethod executeMethod = new RecordingExecuteMethod();
        executeMethod.nextResult = new HashMap<>(Map.of(
            ChromiumNetworkConditions.OFFLINE, true,
            ChromiumNetworkConditions.LATENCY, 200L,
            ChromiumNetworkConditions.DOWNLOAD_THROUGHPUT, 4096,
            ChromiumNetworkConditions.UPLOAD_THROUGHPUT, 4096));
        AddHasNetworkConditions provider = new AddHasNetworkConditions();
        HasNetworkConditions implementation = provider.getImplementation(chromeCapabilities(), executeMethod);

        ChromiumNetworkConditions readConditions = implementation.getNetworkConditions();

        assertThat(provider.getDescribedInterface()).isEqualTo(HasNetworkConditions.class);
        assertThat(provider.isApplicable().test(chromeCapabilities())).isTrue();
        assertThat(provider.isApplicable().test(firefoxCapabilities())).isFalse();
        assertThat(provider.getAdditionalCommands()).containsOnlyKeys(
            AddHasNetworkConditions.GET_NETWORK_CONDITIONS,
            AddHasNetworkConditions.SET_NETWORK_CONDITIONS,
            AddHasNetworkConditions.DELETE_NETWORK_CONDITIONS);
        assertThat(readConditions.getOffline()).isTrue();
        assertThat(readConditions.getLatency()).isEqualTo(Duration.ofMillis(200));
        assertThat(readConditions.getDownloadThroughput()).isEqualTo(4096);
        assertThat(executeMethod.lastCommand).isEqualTo(AddHasNetworkConditions.GET_NETWORK_CONDITIONS);
        assertThat(executeMethod.lastParameters).isNull();

        ChromiumNetworkConditions newConditions = new ChromiumNetworkConditions();
        newConditions.setOffline(false);
        newConditions.setLatency(Duration.ofMillis(33));
        newConditions.setDownloadThroughput(2048);
        newConditions.setUploadThroughput(512);
        implementation.setNetworkConditions(newConditions);

        assertThat(executeMethod.lastCommand).isEqualTo(AddHasNetworkConditions.SET_NETWORK_CONDITIONS);
        assertThat(executeMethod.lastParameters).containsOnlyKeys("network_conditions");
        assertThat(executeMethod.lastNestedMap("network_conditions"))
            .containsEntry(ChromiumNetworkConditions.OFFLINE, false)
            .containsEntry(ChromiumNetworkConditions.LATENCY, 33L)
            .containsEntry(ChromiumNetworkConditions.DOWNLOAD_THROUGHPUT, 2048)
            .containsEntry(ChromiumNetworkConditions.UPLOAD_THROUGHPUT, 512);

        implementation.deleteNetworkConditions();

        assertThat(executeMethod.lastCommand).isEqualTo(AddHasNetworkConditions.DELETE_NETWORK_CONDITIONS);
        assertThat(executeMethod.lastParameters).isNull();
        assertThatThrownBy(() -> implementation.setNetworkConditions(null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void shouldImplementPermissionCommand() {
        RecordingExecuteMethod executeMethod = new RecordingExecuteMethod();
        AddHasPermissions provider = new AddHasPermissions();
        HasPermissions implementation = provider.getImplementation(chromeCapabilities(), executeMethod);

        implementation.setPermission("geolocation", "granted");

        assertThat(provider.getDescribedInterface()).isEqualTo(HasPermissions.class);
        assertThat(provider.isApplicable().test(chromeCapabilities())).isTrue();
        assertThat(provider.isApplicable().test(firefoxCapabilities())).isFalse();
        assertThat(provider.getAdditionalCommands()).containsOnlyKeys(AddHasPermissions.SET_PERMISSION);
        assertThat(executeMethod.lastCommand).isEqualTo(AddHasPermissions.SET_PERMISSION);
        assertThat(executeMethod.lastNestedMap("descriptor")).containsEntry("name", "geolocation");
        assertThat(executeMethod.lastParameters).containsEntry("state", "granted");
        assertThatThrownBy(() -> implementation.setPermission(null, "granted")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> implementation.setPermission("geolocation", null))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void shouldImplementLaunchAppCommand() {
        RecordingExecuteMethod executeMethod = new RecordingExecuteMethod();
        AddHasLaunchApp provider = new AddHasLaunchApp();
        HasLaunchApp implementation = provider.getImplementation(chromeCapabilities(), executeMethod);

        implementation.launchApp("abcdefghijklmnopabcdefghijklmnop");

        assertThat(provider.getDescribedInterface()).isEqualTo(HasLaunchApp.class);
        assertThat(provider.isApplicable().test(chromeCapabilities())).isTrue();
        assertThat(provider.isApplicable().test(firefoxCapabilities())).isFalse();
        assertThat(provider.getAdditionalCommands()).containsOnlyKeys(AddHasLaunchApp.LAUNCH_APP);
        assertThat(executeMethod.lastCommand).isEqualTo(AddHasLaunchApp.LAUNCH_APP);
        assertThat(executeMethod.lastParameters).containsEntry("id", "abcdefghijklmnopabcdefghijklmnop");
        assertThatThrownBy(() -> implementation.launchApp(null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void shouldImplementCdpCommand() {
        RecordingExecuteMethod executeMethod = new RecordingExecuteMethod();
        executeMethod.nextResult = new HashMap<>(Map.of("frameTree", Map.of("frame", Map.of("id", "root"))));
        TestAddHasCdp provider = new TestAddHasCdp();
        HasCdp implementation = provider.getImplementation(chromeCapabilities(), executeMethod);

        Map<String, Object> result = implementation.executeCdpCommand(
            "Page.getFrameTree", Map.of("includeResources", true));

        assertThat(provider.getDescribedInterface()).isEqualTo(HasCdp.class);
        assertThat(provider.isApplicable().test(chromeCapabilities())).isTrue();
        assertThat(provider.isApplicable().test(firefoxCapabilities())).isFalse();
        assertThat(provider.getAdditionalCommands()).containsOnlyKeys(AddHasCdp.EXECUTE_CDP);
        assertThat(executeMethod.lastCommand).isEqualTo(AddHasCdp.EXECUTE_CDP);
        assertThat(executeMethod.lastParameters)
            .containsEntry("cmd", "Page.getFrameTree")
            .containsEntry("params", Map.of("includeResources", true));
        assertThat(result).containsKey("frameTree");
        assertThatThrownBy(() -> result.put("mutable", true)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> implementation.executeCdpCommand(null, Map.of())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> implementation.executeCdpCommand("Page.enable", null))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void shouldImplementCastingCommands() {
        RecordingExecuteMethod executeMethod = new RecordingExecuteMethod();
        executeMethod.nextResult = List.of(Map.of("id", "sink-1", "name", "Living Room"));
        TestAddHasCasting provider = new TestAddHasCasting();
        HasCasting implementation = provider.getImplementation(chromeCapabilities(), executeMethod);

        assertThat(implementation.getCastSinks()).containsExactly(Map.of("id", "sink-1", "name", "Living Room"));
        assertThat(executeMethod.lastCommand).isEqualTo(AddHasCasting.GET_CAST_SINKS);
        assertThat(executeMethod.lastParameters).isNull();

        implementation.selectCastSink("Living Room");
        assertSinkCommand(executeMethod, AddHasCasting.SET_CAST_SINK_TO_USE);
        implementation.startDesktopMirroring("Living Room");
        assertSinkCommand(executeMethod, AddHasCasting.START_CAST_DESKTOP_MIRRORING);
        implementation.startTabMirroring("Living Room");
        assertSinkCommand(executeMethod, AddHasCasting.START_CAST_TAB_MIRRORING);
        implementation.stopCasting("Living Room");
        assertSinkCommand(executeMethod, AddHasCasting.STOP_CASTING);

        executeMethod.nextResult = "No route to sink";
        assertThat(implementation.getCastIssueMessage()).isEqualTo("No route to sink");
        assertThat(executeMethod.lastCommand).isEqualTo(AddHasCasting.GET_CAST_ISSUE_MESSAGE);

        assertThat(provider.getDescribedInterface()).isEqualTo(HasCasting.class);
        assertThat(provider.isApplicable().test(chromeCapabilities())).isTrue();
        assertThat(provider.isApplicable().test(firefoxCapabilities())).isFalse();
        assertThat(provider.getAdditionalCommands()).containsOnlyKeys(
            AddHasCasting.GET_CAST_SINKS,
            AddHasCasting.SET_CAST_SINK_TO_USE,
            AddHasCasting.START_CAST_TAB_MIRRORING,
            AddHasCasting.START_CAST_DESKTOP_MIRRORING,
            AddHasCasting.GET_CAST_ISSUE_MESSAGE,
            AddHasCasting.STOP_CASTING);
        assertThatThrownBy(() -> implementation.selectCastSink(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> implementation.startDesktopMirroring(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> implementation.startTabMirroring(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> implementation.stopCasting(null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void shouldAdvertiseAutoServiceHttpCommands() {
        Set<String> commandNames = new HashSet<>();
        ServiceLoader.load(AdditionalHttpCommands.class)
            .forEach(commands -> commandNames.addAll(commands.getAdditionalCommands().keySet()));

        assertThat(commandNames)
            .contains(AddHasNetworkConditions.GET_NETWORK_CONDITIONS)
            .contains(AddHasNetworkConditions.SET_NETWORK_CONDITIONS)
            .contains(AddHasNetworkConditions.DELETE_NETWORK_CONDITIONS)
            .contains(AddHasPermissions.SET_PERMISSION)
            .contains(AddHasLaunchApp.LAUNCH_APP);
    }

    @Test
    public void shouldReportDriverInfoSessionLimitFromAvailableProcessors() {
        ChromiumDriverInfo info = new TestChromiumDriverInfo();

        assertThat(info.getMaximumSimultaneousSessions())
            .isEqualTo(Runtime.getRuntime().availableProcessors())
            .isPositive();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> chromiumOptions(ChromiumOptions<?> options) {
        return (Map<String, Object>) options.getCapability("goog:chromeOptions");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> optionList(Map<String, Object> options, String key) {
        return (List<Object>) options.get(key);
    }

    private static Capabilities chromeCapabilities() {
        return new ImmutableCapabilities("browserName", Browser.CHROME.browserName());
    }

    private static Capabilities firefoxCapabilities() {
        return new ImmutableCapabilities("browserName", Browser.FIREFOX.browserName());
    }

    private static void assertSinkCommand(RecordingExecuteMethod executeMethod, String expectedCommand) {
        assertThat(executeMethod.lastCommand).isEqualTo(expectedCommand);
        assertThat(executeMethod.lastParameters).containsEntry("sinkName", "Living Room");
    }

    private static class RecordingExecuteMethod implements ExecuteMethod {
        private Object nextResult;
        private String lastCommand;
        private Map<String, Object> lastParameters;

        @Override
        public Object execute(String commandName, Map<String, ?> parameters) {
            lastCommand = commandName;
            lastParameters = parameters == null ? null : new HashMap<>(parameters);
            return nextResult;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> lastNestedMap(String key) {
            return (Map<String, Object>) lastParameters.get(key);
        }
    }

    private static class TestAddHasCdp extends AddHasCdp {
        @Override
        public Map<String, CommandInfo> getAdditionalCommands() {
            return Map.of(EXECUTE_CDP, new CommandInfo("/session/:sessionId/chromium/send_command_and_get_result",
                HttpMethod.POST));
        }
    }

    private static class TestAddHasCasting extends AddHasCasting {
        @Override
        public Map<String, CommandInfo> getAdditionalCommands() {
            return Map.of(
                GET_CAST_SINKS, new CommandInfo("/session/:sessionId/goog/cast/get_sinks", HttpMethod.GET),
                SET_CAST_SINK_TO_USE, new CommandInfo("/session/:sessionId/goog/cast/set_sink_to_use", HttpMethod.POST),
                START_CAST_TAB_MIRRORING,
                    new CommandInfo("/session/:sessionId/goog/cast/start_tab_mirroring", HttpMethod.POST),
                START_CAST_DESKTOP_MIRRORING,
                    new CommandInfo("/session/:sessionId/goog/cast/start_desktop_mirroring", HttpMethod.POST),
                GET_CAST_ISSUE_MESSAGE,
                    new CommandInfo("/session/:sessionId/goog/cast/get_issue_message", HttpMethod.GET),
                STOP_CASTING, new CommandInfo("/session/:sessionId/goog/cast/stop_casting", HttpMethod.POST));
        }

        @Override
        public Predicate<Capabilities> isApplicable() {
            return caps -> ChromiumDriver.IS_CHROMIUM_BROWSER.test(caps.getBrowserName());
        }
    }

    private static class TestChromiumDriverInfo extends ChromiumDriverInfo {
        @Override
        public String getDisplayName() {
            return "Test Chromium";
        }

        @Override
        public Capabilities getCanonicalCapabilities() {
            return chromeCapabilities();
        }

        @Override
        public boolean isSupporting(Capabilities capabilities) {
            return ChromiumDriver.IS_CHROMIUM_BROWSER.test(capabilities.getBrowserName());
        }

        @Override
        public boolean isSupportingCdp() {
            return true;
        }

        @Override
        public boolean isSupportingBiDi() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public Optional<WebDriver> createDriver(Capabilities capabilities) throws SessionNotCreatedException {
            return Optional.empty();
        }
    }
}
