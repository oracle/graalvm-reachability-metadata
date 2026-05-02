/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.DeviceRotation;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.Keys;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Point;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.KeyInput;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogLevelMapping;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.SessionLogs;
import org.openqa.selenium.mobile.NetworkConnection.ConnectionType;

public class Selenium_apiTest {
    @Test
    void capabilitiesMergeSpecialCasesAndExposeStableViews() {
        MutableCapabilities capabilities = new MutableCapabilities();
        capabilities.setCapability("browserName", "firefox");
        capabilities.setCapability("javascriptEnabled", true);
        capabilities.setCapability("platform", "linux");
        capabilities.setCapability("unexpectedAlertBehaviour", "accept");
        capabilities.setCapability("temporary", "value");
        capabilities.setCapability("temporary", (Object) null);

        Map<String, Object> loggingMap = new LinkedHashMap<String, Object>();
        loggingMap.put("browser", "DEBUG");
        capabilities.setCapability("loggingPrefs", loggingMap);

        ImmutableCapabilities extraCapabilities = new ImmutableCapabilities(
                "version", "3.141",
                "custom:option", 42);
        MutableCapabilities merged = capabilities.merge(extraCapabilities);

        assertThat(merged).isSameAs(capabilities);
        assertThat(capabilities.getBrowserName()).isEqualTo("firefox");
        assertThat(capabilities.is("javascriptEnabled")).isTrue();
        assertThat(capabilities.getVersion()).isEqualTo("3.141");
        assertThat(capabilities.getPlatform()).isEqualTo(Platform.LINUX);
        assertThat(capabilities.getCapability("unhandledPromptBehavior")).isEqualTo("accept");
        assertThat(capabilities.getCapability("temporary")).isNull();
        assertThat(capabilities.getCapabilityNames())
                .contains("browserName", "javascriptEnabled", "platform", "loggingPrefs", "version", "custom:option")
                .doesNotContain("temporary");
        assertThat(capabilities.asMap()).containsEntry("custom:option", 42);
        assertThatThrownBy(() -> capabilities.asMap().put("newCapability", true))
                .isInstanceOf(UnsupportedOperationException.class);

        LoggingPreferences loggingPreferences = (LoggingPreferences) capabilities.getCapability("loggingPrefs");
        assertThat(loggingPreferences.getLevel("browser")).isEqualTo(Level.FINE);
        assertThat(ImmutableCapabilities.copyOf(capabilities)).isEqualTo(capabilities);
    }

    @Test
    void proxySupportsManualPacAutodetectAndCapabilitiesExtraction() {
        Proxy manualProxy = new Proxy()
                .setHttpProxy("proxy.example.test:8080")
                .setSslProxy("secure.example.test:8443")
                .setNoProxy("localhost, 127.0.0.1")
                .setSocksProxy("socks.example.test:1080")
                .setSocksVersion(5)
                .setSocksUsername("user")
                .setSocksPassword("secret");

        MutableCapabilities capabilities = new MutableCapabilities();
        capabilities.setCapability("proxy", manualProxy);

        Proxy extractedProxy = Proxy.extractFrom(capabilities);
        Map<String, Object> proxyJson = extractedProxy.toJson();

        assertThat(extractedProxy).isSameAs(manualProxy);
        assertThat(extractedProxy.getProxyType()).isEqualTo(Proxy.ProxyType.MANUAL);
        assertThat(proxyJson)
                .containsEntry("proxyType", "MANUAL")
                .containsEntry("httpProxy", "proxy.example.test:8080")
                .containsEntry("sslProxy", "secure.example.test:8443")
                .containsEntry("socksProxy", "socks.example.test:1080")
                .containsEntry("socksVersion", 5)
                .containsEntry("socksUsername", "user")
                .containsEntry("socksPassword", "secret");
        assertThat(proxyJson.get("noProxy")).isEqualTo(Arrays.asList("localhost", "127.0.0.1"));

        Map<String, Object> wireProxy = new LinkedHashMap<String, Object>();
        wireProxy.put("proxyType", "pac");
        wireProxy.put("proxyAutoconfigUrl", "https://proxy.example.test/proxy.pac");
        Proxy pacProxy = new Proxy(wireProxy);
        assertThat(pacProxy.getProxyType()).isEqualTo(Proxy.ProxyType.PAC);
        assertThat(pacProxy.getProxyAutoconfigUrl()).isEqualTo("https://proxy.example.test/proxy.pac");

        Proxy autodetectProxy = new Proxy().setAutodetect(true);
        assertThat(autodetectProxy.isAutodetect()).isTrue();
        assertThat(autodetectProxy.getProxyType()).isEqualTo(Proxy.ProxyType.AUTODETECT);
        assertThat(autodetectProxy.toJson()).containsEntry("autodetect", true);
    }

    @Test
    void cookiesValidateNormalizeAndRenderToWireFormat() {
        Date expiryWithMillis = new Date(1_700_000_000_999L);
        Cookie cookie = new Cookie.Builder("session", "abc123")
                .domain("example.test:443")
                .path("/app")
                .expiresOn(expiryWithMillis)
                .isSecure(true)
                .isHttpOnly(true)
                .build();

        cookie.validate();

        assertThat(cookie.getName()).isEqualTo("session");
        assertThat(cookie.getValue()).isEqualTo("abc123");
        assertThat(cookie.getDomain()).isEqualTo("example.test");
        assertThat(cookie.getPath()).isEqualTo("/app");
        assertThat(cookie.getExpiry()).isEqualTo(new Date(1_700_000_000_000L));
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.toJson())
                .containsEntry("name", "session")
                .containsEntry("value", "abc123")
                .containsEntry("domain", "example.test")
                .containsEntry("path", "/app")
                .containsEntry("secure", true)
                .containsEntry("httpOnly", true)
                .containsEntry("expiry", new Date(1_700_000_000_000L));
        assertThat(cookie.toString())
                .contains("session=abc123")
                .contains("; path=/app")
                .contains("; domain=example.test")
                .contains(";secure;");
        assertThat(cookie).isEqualTo(new Cookie("session", "abc123"));

        assertThatThrownBy(() -> new Cookie("bad;name", "value").validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cookie names cannot contain");
        assertThatThrownBy(() -> new Cookie("", "value").validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required attributes");
    }

    @Test
    void geometryPlatformAndRotationValueObjectsAreUsableWithoutADriver() {
        Dimension size = new Dimension(640, 480);
        Point origin = new Point(10, 20);
        Rectangle rectangle = new Rectangle(origin, size);

        assertThat(size.getWidth()).isEqualTo(640);
        assertThat(size.getHeight()).isEqualTo(480);
        assertThat(origin.moveBy(5, -10)).isEqualTo(new Point(15, 10));
        assertThat(rectangle.getPoint()).isEqualTo(origin);
        assertThat(rectangle.getDimension()).isEqualTo(size);

        rectangle.setX(30);
        rectangle.setY(40);
        rectangle.setWidth(800);
        rectangle.setHeight(600);

        assertThat(rectangle).isEqualTo(new Rectangle(30, 40, 600, 800));
        assertThat(rectangle.getPoint()).isEqualTo(new Point(30, 40));
        assertThat(rectangle.getDimension()).isEqualTo(new Dimension(800, 600));

        DeviceRotation rotation = new DeviceRotation(1, 2, 3);
        assertThat(rotation.parameters())
                .containsEntry("x", 1)
                .containsEntry("y", 2)
                .containsEntry("z", 3);
        Map<String, Number> rotationParameters = new LinkedHashMap<String, Number>();
        rotationParameters.put("x", 1);
        rotationParameters.put("y", 2);
        rotationParameters.put("z", 3);
        assertThat(new DeviceRotation(rotationParameters)).isEqualTo(rotation);

        assertThat(Platform.fromString("linux")).isEqualTo(Platform.LINUX);
        assertThat(Platform.LINUX.is(Platform.UNIX)).isTrue();
        assertThat(Platform.WIN10.is(Platform.WINDOWS)).isTrue();
        assertThat(Platform.extractFromSysProperty("Mac OS X", "10.14")).isEqualTo(Platform.MAC);
        assertThat(Platform.extractFromSysProperty("macOS 10.14", "10.14")).isEqualTo(Platform.MOJAVE);
    }

    @Test
    void outputTypesAndKeyboardKeysUseWireCompatibleRepresentations() {
        byte[] pngHeader = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        String base64 = OutputType.BASE64.convertFromPngBytes(pngHeader);

        assertThat(base64).isEqualTo("iVBORw0KGgo=");
        assertThat(OutputType.BASE64.convertFromBase64Png(base64)).isEqualTo(base64);
        assertThat(OutputType.BYTES.convertFromBase64Png(base64)).containsExactly(pngHeader);
        assertThat(OutputType.BYTES.convertFromPngBytes(pngHeader)).isSameAs(pngHeader);
        assertThat(OutputType.BASE64.toString()).isEqualTo("OutputType.BASE64");
        assertThat(OutputType.BYTES.toString()).isEqualTo("OutputType.BYTES");

        assertThat((CharSequence) Keys.ENTER).hasToString("\uE007");
        assertThat((CharSequence) Keys.ENTER).hasSize(1);
        assertThat(Keys.ENTER.charAt(0)).isEqualTo('\uE007');
        assertThat((Object) Keys.getKeyFromUnicode('\uE007')).isEqualTo(Keys.ENTER);
        assertThat(Keys.chord(Keys.CONTROL, "a"))
                .isEqualTo(Keys.CONTROL.toString() + "a" + Keys.NULL.toString());
        assertThat(Keys.chord(Arrays.<CharSequence>asList(Keys.SHIFT, "abc")))
                .isEqualTo(Keys.SHIFT.toString() + "abc" + Keys.NULL.toString());
    }

    @Test
    void locatorsProvideIdentityNullValidationAndHelpfulMisses() {
        By id = By.id("login");
        By sameId = By.id("login");
        By css = By.cssSelector("button.primary");

        assertThat(id).isEqualTo(sameId);
        assertThat(id.hashCode()).isEqualTo(sameId.hashCode());
        assertThat(id).hasToString("By.id: login");
        assertThat(css).hasToString("By.cssSelector: button.primary");
        assertThat(By.name("username")).hasToString("By.name: username");
        assertThat(By.className("field")).hasToString("By.className: field");
        assertThat(By.xpath("//button")).hasToString("By.xpath: //button");
        assertThat(By.linkText("Sign in")).hasToString("By.linkText: Sign in");
        assertThat(By.partialLinkText("Sign")).hasToString("By.partialLinkText: Sign");
        assertThat(By.tagName("button")).hasToString("By.tagName: button");

        assertThatThrownBy(() -> By.id(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id is null");
        assertThatThrownBy(() -> By.cssSelector(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selector is null");
        assertThatThrownBy(() -> locatorReturningNoElements().findElement(null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Cannot locate an element using")
                .hasMessageContaining("empty context");
    }

    @Test
    void loggingEntriesPreferencesAndLevelMappingsPreserveEvents() {
        LoggingPreferences basePreferences = new LoggingPreferences();
        basePreferences.enable("browser", Level.FINE);
        LoggingPreferences extraPreferences = new LoggingPreferences();
        extraPreferences.enable("driver", Level.WARNING);

        LoggingPreferences mergedPreferences = basePreferences.addPreferences(extraPreferences);

        assertThat(mergedPreferences).isSameAs(basePreferences);
        assertThat(basePreferences.getEnabledLogTypes()).containsExactlyInAnyOrder("browser", "driver");
        assertThat(basePreferences.getLevel("browser")).isEqualTo(Level.FINE);
        assertThat(basePreferences.getLevel("server")).isEqualTo(Level.OFF);
        assertThat(basePreferences.toJson())
                .containsEntry("browser", "DEBUG")
                .containsEntry("driver", "WARNING");
        assertThat(LogLevelMapping.toLevel("DEBUG")).isEqualTo(Level.FINE);
        assertThat(LogLevelMapping.getName(Level.CONFIG)).isEqualTo("DEBUG");
        assertThat(LogLevelMapping.normalize(Level.SEVERE)).isEqualTo(Level.SEVERE);

        LogEntry warning = new LogEntry(Level.WARNING, 101L, "slow script");
        LogEntry info = new LogEntry(Level.INFO, 102L, "page loaded");
        LogEntries entries = new LogEntries(Arrays.asList(warning, info));

        assertThat(entries.getAll()).containsExactly(warning, info);
        assertThat(entries.filter(Level.WARNING)).containsExactly(warning);
        assertThat(entries.toJson()).containsExactly(warning, info);
        assertThat(warning.toJson())
                .containsEntry("level", Level.WARNING)
                .containsEntry("timestamp", 101L)
                .containsEntry("message", "slow script");
        assertThat(warning.toString()).contains("[WARNING] slow script");
        assertThatThrownBy(() -> entries.getAll().add(new LogEntry(Level.SEVERE, 103L, "failure")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sessionLogsAggregateEntriesFromWireJsonByLogType() {
        Map<String, Object> browserEvent = new LinkedHashMap<String, Object>();
        browserEvent.put("level", "INFO");
        browserEvent.put("timestamp", 201L);
        browserEvent.put("message", "dom ready");
        Map<String, Object> driverEvent = new LinkedHashMap<String, Object>();
        driverEvent.put("level", "SEVERE");
        driverEvent.put("timestamp", 202L);
        driverEvent.put("message", "navigation failed");
        Map<String, Object> wireLogs = new LinkedHashMap<String, Object>();
        wireLogs.put("browser", Collections.singletonList(browserEvent));
        wireLogs.put("driver", Collections.singletonList(driverEvent));

        SessionLogs sessionLogs = SessionLogs.fromJSON(wireLogs);

        LogEntry browserLog = sessionLogs.getLogs("browser").getAll().get(0);
        assertThat(browserLog.getLevel()).isEqualTo(Level.INFO);
        assertThat(browserLog.getTimestamp()).isEqualTo(201L);
        assertThat(browserLog.getMessage()).isEqualTo("dom ready");
        assertThat(sessionLogs.getLogs("driver").getAll().get(0).getLevel()).isEqualTo(Level.SEVERE);
        assertThat(sessionLogs.getLogTypes()).containsExactlyInAnyOrder("browser", "driver");
        assertThat(sessionLogs.getLogs("server").getAll()).isEmpty();
        assertThat(sessionLogs.toJson()).containsKeys("browser", "driver");
        assertThat(sessionLogs.toJson().get("browser").getAll()).containsExactly(browserLog);
        assertThatThrownBy(() -> sessionLogs.getAll().put("server", new LogEntries(Collections.emptyList())))
                .isInstanceOf(UnsupportedOperationException.class);

        SessionLogs manuallyBuiltLogs = new SessionLogs();
        manuallyBuiltLogs.addLog("browser", sessionLogs.getLogs("browser"));
        assertThat(manuallyBuiltLogs.getAll()).containsOnlyKeys("browser");
        assertThat(manuallyBuiltLogs.getLogs("browser").getAll()).containsExactly(browserLog);
    }

    @Test
    void interactionsEncodeKeyboardPointerSequencesAndValidateArguments() {
        KeyInput keyboard = new KeyInput("keyboard");
        Sequence keySequence = new Sequence(keyboard, 1)
                .addAction(keyboard.createKeyDown('A'))
                .addAction(keyboard.createKeyUp('A'));

        Map<String, Object> encodedKeys = keySequence.encode();
        List<?> keyActions = (List<?>) encodedKeys.get("actions");

        assertThat(encodedKeys)
                .containsEntry("type", "key")
                .containsEntry("id", "keyboard");
        assertThat(keyActions).hasSize(3);
        assertThat(asStringObjectMap(keyActions.get(0))).containsEntry("type", "pause").containsEntry("duration", 0L);
        assertThat(asStringObjectMap(keyActions.get(1))).containsEntry("type", "keyDown").containsEntry("value", "A");
        assertThat(asStringObjectMap(keyActions.get(2))).containsEntry("type", "keyUp").containsEntry("value", "A");

        PointerInput mouse = new PointerInput(PointerInput.Kind.MOUSE, "mouse");
        Sequence pointerSequence = new Sequence(mouse, 0)
                .addAction(mouse.createPointerMove(Duration.ofMillis(250), PointerInput.Origin.viewport(), 10, 20))
                .addAction(mouse.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(mouse.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        Map<String, Object> encodedPointer = pointerSequence.toJson();
        Map<String, Object> pointerParameters = asStringObjectMap(encodedPointer.get("parameters"));
        List<?> pointerActions = (List<?>) encodedPointer.get("actions");

        assertThat(encodedPointer).containsEntry("type", "pointer").containsEntry("id", "mouse");
        assertThat(pointerParameters).containsEntry("pointerType", "mouse");
        assertThat(pointerActions).hasSize(3);
        assertThat(asStringObjectMap(pointerActions.get(0)))
                .containsEntry("type", "pointerMove")
                .containsEntry("duration", 250L)
                .containsEntry("origin", "viewport")
                .containsEntry("x", 10)
                .containsEntry("y", 20);
        assertThat(asStringObjectMap(pointerActions.get(1)))
                .containsEntry("type", "pointerDown")
                .containsEntry("button", 0);
        assertThat(asStringObjectMap(pointerActions.get(2)))
                .containsEntry("type", "pointerUp")
                .containsEntry("button", 0);

        assertThatThrownBy(() -> mouse.createPointerMove(Duration.ofMillis(-1), PointerInput.Origin.pointer(), 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duration value must be 0 or greater");
        assertThatThrownBy(() -> new Sequence(keyboard, 0).addAction(mouse.createPointerDown(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrong kind of input device");
    }

    @Test
    void mobileConnectionTypesExposeStateAndWireMask() {
        ConnectionType wifiAndData = new ConnectionType(true, true, false);

        assertThat(wifiAndData.isWifiEnabled()).isTrue();
        assertThat(wifiAndData.isDataEnabled()).isTrue();
        assertThat(wifiAndData.isAirplaneMode()).isFalse();
        assertThat(wifiAndData).isEqualTo(ConnectionType.ALL);
        assertThat(wifiAndData.hashCode()).isEqualTo(ConnectionType.ALL.hashCode());
        assertThat(wifiAndData.toJson()).isEqualTo(ConnectionType.ALL.toJson());
        assertThat(wifiAndData).hasToString(ConnectionType.ALL.toString());

        ConnectionType airplaneMode = new ConnectionType(ConnectionType.AIRPLANE_MODE.toJson());
        assertThat(airplaneMode.isAirplaneMode()).isTrue();
        assertThat(airplaneMode.isWifiEnabled()).isFalse();
        assertThat(airplaneMode.isDataEnabled()).isFalse();
        assertThat(airplaneMode).isEqualTo(ConnectionType.AIRPLANE_MODE);

        ConnectionType noConnection = new ConnectionType(-1);
        assertThat(noConnection).isEqualTo(ConnectionType.NONE);
        assertThat(noConnection.toJson()).isZero();
    }

    @Test
    void webdriverExceptionsIncludeAdditionalContextAndAlertPayload() {
        WebDriverException exception = new WebDriverException("browser failed");
        exception.addInfo(WebDriverException.DRIVER_INFO, "fake-driver 1.0");
        exception.addInfo(WebDriverException.SESSION_ID, "session-123");

        assertThat(exception.getMessage())
                .contains("browser failed")
                .contains("Driver info: fake-driver 1.0")
                .contains("Session ID: session-123");
        assertThat(exception.getAdditionalInformation())
                .contains("Driver info: fake-driver 1.0")
                .contains("Session ID: session-123");
        assertThat(exception.getSupportUrl()).isNull();
        assertThat(exception.getBuildInformation()).isNotNull();

        UnhandledAlertException alertException = new UnhandledAlertException("modal dialog", "Delete item?");
        assertThat(alertException.getAlertText()).isEqualTo("Delete item?");
        assertThat(alertException.getAlert()).containsEntry("text", "Delete item?");
        assertThat(alertException.getMessage()).contains("modal dialog: Delete item?");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static By locatorReturningNoElements() {
        return new By() {
            @Override
            public List<WebElement> findElements(SearchContext context) {
                return Collections.emptyList();
            }

            @Override
            public String toString() {
                return "empty context";
            }
        };
    }
}
