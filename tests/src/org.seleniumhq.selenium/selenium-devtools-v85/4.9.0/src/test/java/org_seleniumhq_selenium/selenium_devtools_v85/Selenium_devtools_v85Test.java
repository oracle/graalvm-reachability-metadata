/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_devtools_v85;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.devtools.CdpInfo;
import org.openqa.selenium.devtools.Command;
import org.openqa.selenium.devtools.Event;
import org.openqa.selenium.devtools.v85.V85CdpInfo;
import org.openqa.selenium.devtools.v85.browser.Browser;
import org.openqa.selenium.devtools.v85.browser.model.Bounds;
import org.openqa.selenium.devtools.v85.browser.model.BrowserContextID;
import org.openqa.selenium.devtools.v85.browser.model.PermissionDescriptor;
import org.openqa.selenium.devtools.v85.browser.model.PermissionSetting;
import org.openqa.selenium.devtools.v85.browser.model.PermissionType;
import org.openqa.selenium.devtools.v85.browser.model.WindowID;
import org.openqa.selenium.devtools.v85.browser.model.WindowState;
import org.openqa.selenium.devtools.v85.dom.DOM;
import org.openqa.selenium.devtools.v85.dom.model.Node;
import org.openqa.selenium.devtools.v85.emulation.Emulation;
import org.openqa.selenium.devtools.v85.emulation.model.ScreenOrientation;
import org.openqa.selenium.devtools.v85.fetch.Fetch;
import org.openqa.selenium.devtools.v85.fetch.model.HeaderEntry;
import org.openqa.selenium.devtools.v85.fetch.model.RequestId;
import org.openqa.selenium.devtools.v85.fetch.model.RequestPaused;
import org.openqa.selenium.devtools.v85.log.Log;
import org.openqa.selenium.devtools.v85.network.Network;
import org.openqa.selenium.devtools.v85.network.model.Cookie;
import org.openqa.selenium.devtools.v85.network.model.CookiePriority;
import org.openqa.selenium.devtools.v85.network.model.CookieSameSite;
import org.openqa.selenium.devtools.v85.network.model.Headers;
import org.openqa.selenium.devtools.v85.network.model.RequestWillBeSent;
import org.openqa.selenium.devtools.v85.page.Page;
import org.openqa.selenium.devtools.v85.page.model.FrameId;
import org.openqa.selenium.devtools.v85.page.model.TransitionType;
import org.openqa.selenium.devtools.v85.page.model.Viewport;
import org.openqa.selenium.devtools.v85.runtime.Runtime;
import org.openqa.selenium.devtools.v85.runtime.model.ExecutionContextId;
import org.openqa.selenium.devtools.v85.runtime.model.RemoteObject;
import org.openqa.selenium.devtools.v85.runtime.model.TimeDelta;
import org.openqa.selenium.devtools.v85.webauthn.WebAuthn;
import org.openqa.selenium.devtools.v85.webauthn.model.AuthenticatorId;
import org.openqa.selenium.devtools.v85.webauthn.model.AuthenticatorProtocol;
import org.openqa.selenium.devtools.v85.webauthn.model.AuthenticatorTransport;
import org.openqa.selenium.devtools.v85.webauthn.model.Credential;
import org.openqa.selenium.devtools.v85.webauthn.model.VirtualAuthenticatorOptions;
import org.openqa.selenium.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Selenium_devtools_v85Test {
    private final Json json = new Json();

    @Test
    void cdpInfoIsAdvertisedThroughServiceLoader() {
        List<CdpInfo> infos = ServiceLoader.load(CdpInfo.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertThat(new V85CdpInfo().getMajorVersion()).isEqualTo(85);
        assertThat(infos)
                .filteredOn(info -> info instanceof V85CdpInfo)
                .singleElement()
                .extracting(CdpInfo::getMajorVersion)
                .isEqualTo(85);
    }

    @Test
    void commandFactoriesExposeChromeDevToolsMethodsAndParameters() {
        ExecutionContextId contextId = new ExecutionContextId(7);
        TimeDelta timeout = new TimeDelta(250);
        Command<Runtime.EvaluateResponse> evaluate = Runtime.evaluate(
                "document.title",
                Optional.of("test-object-group"),
                Optional.of(true),
                Optional.empty(),
                Optional.of(contextId),
                Optional.of(true),
                Optional.empty(),
                Optional.empty(),
                Optional.of(false),
                Optional.empty(),
                Optional.of(timeout),
                Optional.empty(),
                Optional.empty(),
                Optional.of(true));

        assertThat(evaluate.getMethod()).isEqualTo("Runtime.evaluate");
        assertThat(evaluate.getSendsResponse()).isTrue();
        assertThat(evaluate.getParams())
                .containsEntry("expression", "document.title")
                .containsEntry("objectGroup", "test-object-group")
                .containsEntry("includeCommandLineAPI", true)
                .containsEntry("contextId", contextId)
                .containsEntry("returnByValue", true)
                .containsEntry("awaitPromise", false)
                .containsEntry("timeout", timeout)
                .containsEntry("allowUnsafeEvalBlockedByCSP", true)
                .doesNotContainKeys(
                        "silent", "generatePreview", "userGesture", "throwOnSideEffect", "disableBreaks", "replMode");

        FrameId frameId = new FrameId("frame-1");
        Command<Page.NavigateResponse> navigate = Page.navigate(
                "https://example.test/page",
                Optional.of("https://referrer.test/"),
                Optional.of(TransitionType.TYPED),
                Optional.of(frameId),
                Optional.empty());

        assertThat(navigate.getMethod()).isEqualTo("Page.navigate");
        assertThat(navigate.getParams())
                .containsEntry("url", "https://example.test/page")
                .containsEntry("referrer", "https://referrer.test/")
                .containsEntry("transitionType", TransitionType.TYPED)
                .containsEntry("frameId", frameId)
                .doesNotContainKey("referrerPolicy");

        Command<Node> document = DOM.getDocument(Optional.of(2), Optional.of(true));
        assertThat(document.getMethod()).isEqualTo("DOM.getDocument");
        assertThat(document.getParams()).containsEntry("depth", 2).containsEntry("pierce", true);

        Command<Void> disableRuntime = Runtime.disable().doesNotSendResponse();
        assertThat(disableRuntime.getMethod()).isEqualTo("Runtime.disable");
        assertThat(disableRuntime.getParams()).isEmpty();
        assertThat(disableRuntime.getSendsResponse()).isFalse();
    }

    @Test
    void networkFetchAndEmulationCommandsSerializeTypedValues() {
        Command<Void> network = Network.enable(Optional.of(1024), Optional.empty(), Optional.of(4096));
        assertThat(network.getMethod()).isEqualTo("Network.enable");
        assertThat(network.getParams())
                .containsEntry("maxTotalBufferSize", 1024)
                .containsEntry("maxPostDataSize", 4096)
                .doesNotContainKey("maxResourceBufferSize");

        HeaderEntry contentType = new HeaderEntry("Content-Type", "text/plain");
        Command<Void> fulfill = Fetch.fulfillRequest(
                new RequestId("fetch-1"),
                201,
                Optional.of(List.of(contentType)),
                Optional.empty(),
                Optional.of("Ym9keQ=="),
                Optional.of("Created"));

        assertThat(fulfill.getMethod()).isEqualTo("Fetch.fulfillRequest");
        assertThat(fulfill.getParams())
                .containsEntry("responseCode", 201)
                .containsEntry("body", "Ym9keQ==")
                .containsEntry("responsePhrase", "Created")
                .doesNotContainKey("binaryResponseHeaders");
        assertThat(json.toJson(fulfill.getParams()))
                .contains("\"requestId\"")
                .contains("\"fetch-1\"")
                .contains("\"Content-Type\"")
                .contains("text")
                .contains("plain");

        ScreenOrientation orientation = new ScreenOrientation(ScreenOrientation.Type.LANDSCAPEPRIMARY, 90);
        Viewport viewport = new Viewport(0, 0, 800, 600, 1);
        Command<Void> emulation = Emulation.setDeviceMetricsOverride(
                800,
                600,
                2,
                true,
                Optional.of(1),
                Optional.of(800),
                Optional.of(600),
                Optional.empty(),
                Optional.empty(),
                Optional.of(false),
                Optional.of(orientation),
                Optional.of(viewport));

        assertThat(emulation.getMethod()).isEqualTo("Emulation.setDeviceMetricsOverride");
        assertThat(emulation.getParams())
                .containsEntry("width", 800)
                .containsEntry("height", 600)
                .containsEntry("deviceScaleFactor", 2)
                .containsEntry("mobile", true)
                .containsEntry("screenOrientation", orientation)
                .containsEntry("viewport", viewport)
                .doesNotContainKeys("positionX", "positionY");
        assertThat(json.toJson(emulation.getParams()))
                .contains("\"type\"")
                .contains("\"landscapePrimary\"")
                .contains("\"angle\"")
                .contains("90")
                .contains("\"width\"")
                .contains("800");
    }

    @Test
    void browserDomainConfiguresPermissionsAndWindowBounds() {
        BrowserContextID contextId = new BrowserContextID("context-1");
        PermissionDescriptor clipboardRead = new PermissionDescriptor(
                "clipboard-read",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(true));
        Command<Void> permission = Browser.setPermission(
                clipboardRead,
                PermissionSetting.GRANTED,
                Optional.of("https://example.test"),
                Optional.of(contextId));

        assertThat(permission.getMethod()).isEqualTo("Browser.setPermission");
        assertThat(permission.getParams())
                .containsEntry("permission", clipboardRead)
                .containsEntry("setting", PermissionSetting.GRANTED)
                .containsEntry("origin", "https://example.test")
                .containsEntry("browserContextId", contextId);
        assertThat(json.toJson(permission.getParams()))
                .contains("\"name\"")
                .contains("\"clipboard-read\"")
                .contains("\"allowWithoutSanitization\"")
                .contains("true")
                .contains("\"setting\"")
                .contains("\"granted\"")
                .contains("\"browserContextId\"")
                .contains("\"context-1\"");

        Command<Void> grantPermissions = Browser.grantPermissions(
                List.of(PermissionType.GEOLOCATION, PermissionType.NOTIFICATIONS),
                Optional.of("https://example.test"),
                Optional.empty());
        assertThat(grantPermissions.getMethod()).isEqualTo("Browser.grantPermissions");
        assertThat(grantPermissions.getParams())
                .containsEntry("permissions", List.of(PermissionType.GEOLOCATION, PermissionType.NOTIFICATIONS))
                .containsEntry("origin", "https://example.test")
                .doesNotContainKey("browserContextId");
        assertThat(json.toJson(grantPermissions.getParams()))
                .contains("\"geolocation\"")
                .contains("\"notifications\"");

        WindowID windowId = new WindowID(11);
        Bounds bounds = new Bounds(
                Optional.of(10),
                Optional.of(20),
                Optional.of(1200),
                Optional.of(800),
                Optional.of(WindowState.MAXIMIZED));
        Command<Void> setBounds = Browser.setWindowBounds(windowId, bounds);
        assertThat(setBounds.getMethod()).isEqualTo("Browser.setWindowBounds");
        assertThat(setBounds.getParams()).containsEntry("windowId", windowId).containsEntry("bounds", bounds);
        assertThat(json.toJson(setBounds.getParams()))
                .contains("\"windowId\"")
                .contains("11")
                .contains("\"windowState\"")
                .contains("\"maximized\"");

        Browser.GetWindowForTargetResponse window = json.toType("""
                {
                  "windowId": 11,
                  "bounds": {
                    "left": 10,
                    "top": 20,
                    "width": 1200,
                    "height": 800,
                    "windowState": "maximized"
                  }
                }
                """, Browser.GetWindowForTargetResponse.class);

        assertThat(window.getWindowId()).hasToString("11");
        assertThat(window.getBounds().getLeft()).contains(10);
        assertThat(window.getBounds().getTop()).contains(20);
        assertThat(window.getBounds().getWidth()).contains(1200);
        assertThat(window.getBounds().getHeight()).contains(800);
        assertThat(window.getBounds().getWindowState()).contains(WindowState.MAXIMIZED);

        Browser.GetVersionResponse version = json.toType("""
                {
                  "protocolVersion": "1.3",
                  "product": "Chrome/85.0",
                  "revision": "revision-id",
                  "userAgent": "Mozilla/5.0",
                  "jsVersion": "8.5"
                }
                """, Browser.GetVersionResponse.class);

        assertThat(version.getProtocolVersion()).isEqualTo("1.3");
        assertThat(version.getProduct()).isEqualTo("Chrome/85.0");
        assertThat(version.getRevision()).isEqualTo("revision-id");
        assertThat(version.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(version.getJsVersion()).isEqualTo("8.5");
    }

    @Test
    void webAuthnDomainConfiguresVirtualAuthenticatorsAndCredentials() {
        VirtualAuthenticatorOptions options = new VirtualAuthenticatorOptions(
                AuthenticatorProtocol.CTAP2,
                AuthenticatorTransport.INTERNAL,
                Optional.of(true),
                Optional.of(true),
                Optional.of(false),
                Optional.of(true));
        Command<AuthenticatorId> addAuthenticator = WebAuthn.addVirtualAuthenticator(options);

        assertThat(addAuthenticator.getMethod()).isEqualTo("WebAuthn.addVirtualAuthenticator");
        assertThat(addAuthenticator.getParams()).containsEntry("options", options);
        assertThat(json.toJson(addAuthenticator.getParams()))
                .contains("\"protocol\"")
                .contains("\"ctap2\"")
                .contains("\"transport\"")
                .contains("\"internal\"")
                .contains("\"hasResidentKey\"")
                .contains("true")
                .contains("\"automaticPresenceSimulation\"")
                .contains("false");

        AuthenticatorId authenticatorId = new AuthenticatorId("authenticator-1");
        Credential credential = new Credential(
                "credential-id",
                true,
                Optional.of("example.test"),
                "private-key",
                Optional.of("user-handle"),
                3);
        Command<Void> addCredential = WebAuthn.addCredential(authenticatorId, credential);

        assertThat(addCredential.getMethod()).isEqualTo("WebAuthn.addCredential");
        assertThat(addCredential.getParams())
                .containsEntry("authenticatorId", authenticatorId)
                .containsEntry("credential", credential);
        assertThat(json.toJson(addCredential.getParams()))
                .contains("\"authenticatorId\"")
                .contains("\"authenticator-1\"")
                .contains("\"credentialId\"")
                .contains("\"credential-id\"")
                .contains("\"rpId\"")
                .contains("\"example.test\"")
                .contains("\"signCount\"")
                .contains("3");

        Command<Credential> getCredential = WebAuthn.getCredential(authenticatorId, "credential-id");
        assertThat(getCredential.getMethod()).isEqualTo("WebAuthn.getCredential");
        assertThat(getCredential.getParams())
                .containsEntry("authenticatorId", authenticatorId)
                .containsEntry("credentialId", "credential-id");

        Credential restoredCredential = json.toType("""
                {
                  "credentialId": "credential-id",
                  "isResidentCredential": true,
                  "rpId": "example.test",
                  "privateKey": "private-key",
                  "userHandle": "user-handle",
                  "signCount": 3
                }
                """, Credential.class);

        assertThat(restoredCredential.getCredentialId()).isEqualTo("credential-id");
        assertThat(restoredCredential.getIsResidentCredential()).isTrue();
        assertThat(restoredCredential.getRpId()).contains("example.test");
        assertThat(restoredCredential.getPrivateKey()).isEqualTo("private-key");
        assertThat(restoredCredential.getUserHandle()).contains("user-handle");
        assertThat(restoredCredential.getSignCount()).isEqualTo(3);
    }

    @Test
    void eventsExposeStableDevToolsEventNames() {
        List<Event<?>> events = List.of(
                Network.requestWillBeSent(),
                Network.responseReceived(),
                Page.frameNavigated(),
                Runtime.consoleAPICalled(),
                Fetch.requestPaused(),
                Log.entryAdded());

        assertThat(events)
                .extracting(Event::getMethod)
                .containsExactly(
                        "Network.requestWillBeSent",
                        "Network.responseReceived",
                        "Page.frameNavigated",
                        "Runtime.consoleAPICalled",
                        "Fetch.requestPaused",
                        "Log.entryAdded");
        assertThat(Network.requestWillBeSent()).hasToString("Network.requestWillBeSent");
    }

    @Test
    void jsonMapsNestedNetworkEventPayloadsToGeneratedModelTypes() {
        RequestWillBeSent event = json.toType("""
                {
                  "requestId": "network-1",
                  "loaderId": "loader-1",
                  "documentURL": "https://example.test/index.html",
                  "request": {
                    "url": "https://example.test/api#fragment",
                    "urlFragment": "#fragment",
                    "method": "POST",
                    "headers": {
                      "Accept": "application/json",
                      "X-Trace": "abc"
                    },
                    "postData": "name=value",
                    "hasPostData": true,
                    "initialPriority": "High",
                    "referrerPolicy": "strict-origin-when-cross-origin",
                    "isLinkPreload": false
                  },
                  "timestamp": 10.25,
                  "wallTime": 1000.5,
                  "initiator": {
                    "type": "parser",
                    "url": "https://example.test/index.html",
                    "lineNumber": 12
                  },
                  "type": "XHR",
                  "frameId": "frame-1",
                  "hasUserGesture": true
                }
                """, RequestWillBeSent.class);

        assertThat(event.getRequestId()).hasToString("network-1");
        assertThat(event.getLoaderId()).hasToString("loader-1");
        assertThat(event.getDocumentURL()).isEqualTo("https://example.test/index.html");
        assertThat(event.getRequest().getUrl()).isEqualTo("https://example.test/api#fragment");
        assertThat(event.getRequest().getUrlFragment()).contains("#fragment");
        assertThat(event.getRequest().getMethod()).isEqualTo("POST");
        assertThat(event.getRequest().getHeaders()).containsEntry("Accept", "application/json");
        assertThat(event.getRequest().getPostData()).contains("name=value");
        assertThat(event.getRequest().getHasPostData()).contains(true);
        assertThat(event.getRequest().getInitialPriority()).hasToString("High");
        assertThat(event.getRequest().getReferrerPolicy()).hasToString("strict-origin-when-cross-origin");
        assertThat(event.getInitiator().getType()).hasToString("parser");
        assertThat(event.getInitiator().getLineNumber())
                .hasValueSatisfying(number -> assertThat(number.intValue()).isEqualTo(12));
        assertThat(event.getType()).hasValueSatisfying(type -> assertThat(type).hasToString("XHR"));
        assertThat(event.getFrameId()).hasValueSatisfying(id -> assertThat(id).hasToString("frame-1"));
        assertThat(event.getHasUserGesture()).contains(true);
        assertThat(event.getRedirectResponse()).isEmpty();
    }

    @Test
    void jsonMapsFetchAndRuntimeResponsesToGeneratedModelTypes() {
        RequestPaused paused = json.toType("""
                {
                  "requestId": "fetch-1",
                  "request": {
                    "url": "https://example.test/data",
                    "method": "GET",
                    "headers": {"Accept": "text/plain"},
                    "initialPriority": "Medium",
                    "referrerPolicy": "no-referrer"
                  },
                  "frameId": "frame-1",
                  "resourceType": "Fetch",
                  "networkId": "network-1"
                }
                """, RequestPaused.class);

        assertThat(paused.getRequestId()).hasToString("fetch-1");
        assertThat(paused.getRequest().getUrl()).isEqualTo("https://example.test/data");
        assertThat(paused.getRequest().getHeaders()).containsEntry("Accept", "text/plain");
        assertThat(paused.getFrameId()).hasToString("frame-1");
        assertThat(paused.getResourceType()).hasToString("Fetch");
        assertThat(paused.getNetworkId()).hasValueSatisfying(id -> assertThat(id).hasToString("network-1"));
        assertThat(paused.getResponseStatusCode()).isEmpty();
        assertThat(paused.getResponseHeaders()).isEmpty();

        Runtime.EvaluateResponse evaluated = json.toType("""
                {
                  "result": {
                    "type": "number",
                    "value": 42,
                    "description": "forty-two"
                  }
                }
                """, Runtime.EvaluateResponse.class);

        assertThat(evaluated.getResult().getType()).isEqualTo(RemoteObject.Type.NUMBER);
        assertThat(evaluated.getResult().getValue()).hasValueSatisfying(value -> {
            assertThat(value).isInstanceOf(Number.class);
            assertThat(((Number) value).intValue()).isEqualTo(42);
        });
        assertThat(evaluated.getResult().getDescription()).contains("forty-two");
        assertThat(evaluated.getExceptionDetails()).isEmpty();

        Fetch.GetResponseBodyResponse body = json.toType("""
                {
                  "body": "hello",
                  "base64Encoded": false
                }
                """, Fetch.GetResponseBodyResponse.class);

        assertThat(body.getBody()).isEqualTo("hello");
        assertThat(body.getBase64Encoded()).isFalse();
    }

    @Test
    void generatedValueTypesExposeAccessorsValidationAndJsonShape() {
        Map<String, Object> headerValues = new LinkedHashMap<>();
        headerValues.put("Accept", "application/json");
        headerValues.put("Retry-After", 3);
        Headers headers = new Headers(headerValues);

        assertThat(headers).containsEntry("Accept", "application/json").containsEntry("Retry-After", 3);
        assertThat(headers.toJson()).containsEntry("Accept", "application/json").containsEntry("Retry-After", 3);
        assertThat(headers).hasToString("{Accept=application/json, Retry-After=3}");

        Cookie cookie = new Cookie(
                "session",
                "abc123",
                "example.test",
                "/",
                1234,
                16,
                true,
                true,
                false,
                Optional.of(CookieSameSite.LAX),
                CookiePriority.HIGH);

        assertThat(cookie.getName()).isEqualTo("session");
        assertThat(cookie.getValue()).isEqualTo("abc123");
        assertThat(cookie.getDomain()).isEqualTo("example.test");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getExpires()).isEqualTo(1234);
        assertThat(cookie.getSize()).isEqualTo(16);
        assertThat(cookie.getHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getSession()).isFalse();
        assertThat(cookie.getSameSite()).contains(CookieSameSite.LAX);
        assertThat(cookie.getPriority()).isEqualTo(CookiePriority.HIGH);
        assertThat(json.toJson(cookie))
                .contains("\"name\"")
                .contains("\"session\"")
                .contains("\"sameSite\"")
                .contains("\"Lax\"")
                .contains("\"priority\"")
                .contains("\"High\"");

        assertThat(RemoteObject.Type.fromString("bigint")).isEqualTo(RemoteObject.Type.BIGINT);
        assertThat(RemoteObject.Subtype.fromString("arraybuffer")).isEqualTo(RemoteObject.Subtype.ARRAYBUFFER);
        assertThatThrownBy(() -> new RemoteObject(
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type is required");
    }
}
