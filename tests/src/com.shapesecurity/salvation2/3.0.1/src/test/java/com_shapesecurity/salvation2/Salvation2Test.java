/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_shapesecurity.salvation2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shapesecurity.salvation2.Directive;
import com.shapesecurity.salvation2.Directives.PluginTypesDirective;
import com.shapesecurity.salvation2.Directives.ReportUriDirective;
import com.shapesecurity.salvation2.Directives.SandboxDirective;
import com.shapesecurity.salvation2.Directives.SourceExpressionDirective;
import com.shapesecurity.salvation2.FetchDirectiveKind;
import com.shapesecurity.salvation2.Policy;
import com.shapesecurity.salvation2.PolicyInOrigin;
import com.shapesecurity.salvation2.PolicyList;
import com.shapesecurity.salvation2.URLs.GUID;
import com.shapesecurity.salvation2.URLs.URI;
import com.shapesecurity.salvation2.URLs.URLWithScheme;
import com.shapesecurity.salvation2.Values.Hash;
import com.shapesecurity.salvation2.Values.Host;
import com.shapesecurity.salvation2.Values.MediaType;
import com.shapesecurity.salvation2.Values.Nonce;
import com.shapesecurity.salvation2.Values.RFC7230Token;
import com.shapesecurity.salvation2.Values.Scheme;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class Salvation2Test {
    @Test
    void parsesValueObjectsAndCanonicalizesWhereSpecified() {
        Scheme scheme = Scheme.parseScheme("HTTPS:").orElseThrow();
        assertThat(scheme.value).isEqualTo("https");
        assertThat(scheme.toString()).isEqualTo("https:");
        assertThat(Scheme.parseScheme("not a scheme")).isEmpty();

        Host host = Host.parseHost("HTTPS://Example.COM:443/assets/app.js").orElseThrow();
        assertThat(host.scheme).isEqualTo("https");
        assertThat(host.host).isEqualTo("example.com");
        assertThat(host.port).isEqualTo(443);
        assertThat(host.path).isEqualTo("/assets/app.js");
        assertThat(host.toString()).isEqualTo("https://example.com/assets/app.js");
        assertThat(Host.parseHost("https://bad host")).isEmpty();

        URI uri = URI.parseURI("https://Example.com:8443/app/index.html").orElseThrow();
        assertThat(uri.scheme).isEqualTo("https");
        assertThat(uri.host).isEqualTo("example.com");
        assertThat(uri.port).isEqualTo(8443);
        assertThat(uri.path).isEqualTo("/app/index.html");
        assertThat(URI.defaultPortForProtocol("wss")).isEqualTo(443);
        assertThat(URI.parseURI("example.com/path")).isEmpty();

        GUID guid = GUID.parseGUID("data:text/plain,hello").orElseThrow();
        assertThat(guid.scheme).isEqualTo("data");
        assertThat(guid.host).isNull();
        assertThat(guid.path).isEqualTo("text/plain,hello");

        Nonce nonce = Nonce.parseNonce("'nonce-AbC123+/='").orElseThrow();
        assertThat(nonce.base64ValuePart).isEqualTo("AbC123+/=");
        assertThat(nonce.toString()).isEqualTo("'nonce-AbC123+/='");
        assertThat(Nonce.parseNonce("nonce-AbC123")).isEmpty();

        Hash hash = Hash.parseHash("'sha384-AbC123+/='").orElseThrow();
        assertThat(hash.algorithm).isEqualTo(Hash.Algorithm.SHA384);
        assertThat(hash.base64ValuePart).isEqualTo("AbC123+/=");
        assertThat(hash.toString()).isEqualTo("'sha384-AbC123+/='");
        assertThat(Hash.parseHash("'sha999-AbC123='")).isEmpty();

        MediaType mediaType = MediaType.parseMediaType("Application/PDF").orElseThrow();
        assertThat(mediaType.type).isEqualTo("application");
        assertThat(mediaType.subtype).isEqualTo("pdf");
        assertThat(mediaType.toString()).isEqualTo("application/pdf");

        RFC7230Token token = RFC7230Token.parseRFC7230Token("endpoint-1").orElseThrow();
        assertThat(token.value).isEqualTo("endpoint-1");
        assertThat(token.toString()).isEqualTo("endpoint-1");
        assertThat(RFC7230Token.parseRFC7230Token("bad token")).isEmpty();
    }

    @Test
    void parsesPolicyListsReportsErrorsAndPreservesSerializablePolicyText() {
        List<PolicyListError> errors = new ArrayList<>();
        PolicyList policyList = Policy.parseSerializedCSPList(
                " default-src 'none' 'self'; report-uri /csp /csp; bad_directive value, script-src 'nonce-@@@'",
                (severity, message, policyIndex, directiveIndex, valueIndex) -> errors.add(
                        new PolicyListError(message, policyIndex)));

        assertThat(policyList.policies).hasSize(2);
        assertThat(policyList.toString()).isEqualTo(
                "default-src 'none' 'self'; report-uri /csp /csp; bad_directive value, script-src 'nonce-@@@'");
        assertThat(errors)
                .anySatisfy(error -> {
                    assertThat(error.policyIndex).isZero();
                    assertThat(error.message).contains("'none' must not be combined");
                })
                .anySatisfy(error -> {
                    assertThat(error.policyIndex).isZero();
                    assertThat(error.message).contains("report-uri directive has been deprecated");
                })
                .anySatisfy(error -> assertThat(error.message).contains("Duplicate report-to URI"))
                .anySatisfy(error -> assertThat(error.message).contains("Directive name bad_directive contains"))
                .anySatisfy(error -> {
                    assertThat(error.policyIndex).isOne();
                    assertThat(error.message).contains("Unrecognised nonce");
                });

        Policy normalized = Policy.parseSerializedCSP(
                "\tdefault-src   'self' ;  script-src  https:  ",
                Policy.PolicyErrorConsumer.ignored);
        assertThat(normalized.toString()).isEqualTo("default-src 'self'; script-src https:");

        assertThatThrownBy(() -> Policy.parseSerializedCSP(
                "default-src 'self', script-src 'self'",
                Policy.PolicyErrorConsumer.ignored))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parseSerializedCSPList");
        assertThatThrownBy(() -> Policy.parseSerializedCSP(
                "script-src caf\u00e9.example",
                Policy.PolicyErrorConsumer.ignored))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("string is not ascii");
    }

    @Test
    void exposesDirectiveSpecificStateAndSupportsPolicyManipulation() {
        Policy policy = Policy.parseSerializedCSP(
                "script-src 'nonce-AbC123' 'strict-dynamic' 'unsafe-eval' https://cdn.example.com; "
                        + "sandbox allow-scripts allow-forms; plugin-types application/pdf; report-to endpoint",
                Policy.PolicyErrorConsumer.ignored);

        SourceExpressionDirective script = policy.getFetchDirective(FetchDirectiveKind.ScriptSrc).orElseThrow();
        assertThat(script.getNonces()).containsExactly(Nonce.parseNonce("'nonce-AbC123'").orElseThrow());
        assertThat(script.strictDynamic()).isTrue();
        assertThat(script.unsafeEval()).isTrue();
        assertThat(script.getHosts()).containsExactly(Host.parseHost("https://cdn.example.com").orElseThrow());

        script.setUnsafeInline(true);
        script.setStrictDynamic(false);
        script.addScheme(Scheme.parseScheme("data:").orElseThrow(), Directive.ManipulationErrorConsumer.ignored);
        script.addHost(
                Host.parseHost("https://static.example.com/assets/").orElseThrow(),
                Directive.ManipulationErrorConsumer.ignored);
        script.addNonce(Nonce.parseNonce("'nonce-ZYX987'").orElseThrow(), Directive.ManipulationErrorConsumer.ignored);
        assertThat(script.unsafeInline()).isTrue();
        assertThat(script.strictDynamic()).isFalse();
        assertThat(script.getSchemes()).contains(Scheme.parseScheme("data:").orElseThrow());
        assertThat(script.getHosts()).contains(Host.parseHost("https://static.example.com/assets/").orElseThrow());
        assertThat(script.getValues()).contains(
                "'unsafe-inline'",
                "data:",
                "https://static.example.com/assets/",
                "'nonce-ZYX987'");

        assertThat(script.removeNonce(Nonce.parseNonce("'nonce-ZYX987'").orElseThrow())).isTrue();
        assertThat(script.removeScheme(Scheme.parseScheme("data:").orElseThrow())).isTrue();
        assertThat(script.removeHost(Host.parseHost("https://static.example.com/assets/").orElseThrow())).isTrue();
        assertThat(script.removeHost(Host.parseHost("https://missing.example.com").orElseThrow())).isFalse();

        SandboxDirective sandbox = policy.sandbox().orElseThrow();
        assertThat(sandbox.allowScripts()).isTrue();
        assertThat(sandbox.allowForms()).isTrue();
        sandbox.setAllowScripts(false);
        sandbox.setAllowDownloads(true);
        assertThat(sandbox.allowScripts()).isFalse();
        assertThat(sandbox.allowDownloads()).isTrue();
        assertThat(sandbox.getValues()).contains("allow-downloads").doesNotContain("allow-scripts");

        PluginTypesDirective pluginTypes = policy.pluginTypes().orElseThrow();
        MediaType svg = MediaType.parseMediaType("image/svg+xml").orElseThrow();
        pluginTypes.addMediaType(svg, Directive.ManipulationErrorConsumer.ignored);
        assertThat(pluginTypes.getMediaTypes()).contains(
                MediaType.parseMediaType("application/pdf").orElseThrow(),
                svg);
        assertThat(pluginTypes.removeMediaType(svg)).isTrue();
        assertThat(policy.allowsPlugin(Optional.of(svg))).isFalse();

        policy.setBlockAllMixedContent(true);
        policy.setUpgradeInsecureRequests(true);
        policy.setReportTo(RFC7230Token.parseRFC7230Token("new-endpoint").orElseThrow());
        assertThat(policy.blockAllMixedContent()).isTrue();
        assertThat(policy.upgradeInsecureRequests()).isTrue();
        assertThat(policy.reportTo()).contains(RFC7230Token.parseRFC7230Token("new-endpoint").orElseThrow());
        assertThat(policy.toString()).contains(
                "block-all-mixed-content",
                "upgrade-insecure-requests",
                "report-to new-endpoint");

        assertThat(policy.remove("sandbox")).isTrue();
        assertThat(policy.sandbox()).isEmpty();
        policy.setReportTo(null);
        assertThat(policy.reportTo()).isEmpty();
        assertThat(policy.remove("missing-directive")).isFalse();
    }

    @Test
    void supportsReportUriDirectiveMutationIncludingDuplicateRemoval() {
        List<String> messages = new ArrayList<>();
        Policy policy = Policy.parseSerializedCSP(
                "report-uri https://reports.example.com/csp https://reports.example.com/csp",
                (severity, message, directiveIndex, valueIndex) -> messages.add(message));
        ReportUriDirective reportUri = policy.reportUri().orElseThrow();

        assertThat(messages).anySatisfy(message -> assertThat(message).contains("deprecated"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("Duplicate report-to URI"));
        assertThat(reportUri.getUris()).containsExactly(
                "https://reports.example.com/csp", "https://reports.example.com/csp");

        reportUri.addUri("/relative-endpoint", Directive.ManipulationErrorConsumer.ignored);
        assertThat(reportUri.getUris()).contains("/relative-endpoint");
        assertThat(reportUri.removeUri("https://reports.example.com/csp")).isTrue();
        assertThat(reportUri.getUris()).containsExactly("/relative-endpoint");
        assertThat(reportUri.getValues()).containsExactly("/relative-endpoint");
        assertThat(reportUri.removeUri("https://reports.example.com/csp")).isFalse();
    }

    @Test
    void evaluatesSourceListsWithFallbacksSchemesHostsPortsAndPaths() {
        URLWithScheme origin = uri("https://app.example.com/index.html");
        Policy policy = Policy.parseSerializedCSP(
                "default-src 'self'; "
                        + "img-src https://images.example.com https://cdn.example.com/assets/; "
                        + "connect-src wss://api.example.com:*; "
                        + "frame-ancestors 'self'; "
                        + "form-action https://forms.example.com; "
                        + "object-src 'none'",
                Policy.PolicyErrorConsumer.ignored);
        PolicyInOrigin policyInOrigin = new PolicyInOrigin(policy, origin);

        assertThat(policy.allowsImage(
                Optional.of(uri("https://images.example.com/logo.png")),
                Optional.of(origin))).isTrue();
        assertThat(policy.allowsImage(
                Optional.of(uri("https://cdn.example.com/assets/icons/logo.png")),
                Optional.of(origin))).isTrue();
        assertThat(policy.allowsImage(
                Optional.of(uri("https://cdn.example.com/private/logo.png")),
                Optional.of(origin))).isFalse();
        assertThat(policy.allowsImage(Optional.empty(), Optional.of(origin))).isFalse();

        assertThat(policy.allowsConnection(
                Optional.of(uri("wss://api.example.com:9443/socket")),
                Optional.of(origin))).isTrue();
        assertThat(policy.allowsConnection(
                Optional.of(uri("wss://other.example.com/socket")),
                Optional.of(origin))).isFalse();

        assertThat(policyInOrigin.allowsFontFromSource(uri("https://app.example.com/fonts/app.woff2"))).isTrue();
        assertThat(policyInOrigin.allowsFontFromSource(uri("https://fonts.example.com/font.woff2"))).isFalse();
        assertThat(policyInOrigin.allowsObjectFromSource(uri("https://app.example.com/app.swf"))).isFalse();
        assertThat(policyInOrigin.allowsFrameAncestor(uri("https://app.example.com/parent"))).isTrue();
        assertThat(policyInOrigin.allowsFrameAncestor(uri("https://evil.example.com/parent"))).isFalse();
        assertThat(policyInOrigin.allowsFormAction(uri("https://forms.example.com/post"))).isTrue();
        assertThat(policyInOrigin.allowsFormAction(uri("https://app.example.com/post"))).isFalse();

        SourceExpressionDirective images = policy.getFetchDirective(FetchDirectiveKind.ImgSrc).orElseThrow();
        assertThat(Policy.doesUrlMatchSourceListInOrigin(
                uri("https://cdn.example.com/assets/app.js"),
                images,
                Optional.of(origin))).isTrue();
    }

    @Test
    void evaluatesInlineAndExternalScriptStylePermissions() {
        String scriptSource = "alert('salvation2');";
        String scriptHash = sha256(scriptSource);
        URLWithScheme origin = uri("https://app.example.com/index.html");
        Policy policy = Policy.parseSerializedCSP(
                "script-src 'nonce-AbC123' 'sha256-" + scriptHash + "' 'unsafe-hashes' 'strict-dynamic'; "
                        + "style-src 'unsafe-inline'",
                Policy.PolicyErrorConsumer.ignored);

        assertThat(policy.allowsInlineScript(Optional.of("AbC123"), Optional.empty(), Optional.of(true))).isTrue();
        assertThat(policy.allowsInlineScript(
                Optional.of("wrong"),
                Optional.of(scriptSource),
                Optional.of(true))).isTrue();
        assertThat(policy.allowsInlineScript(
                Optional.empty(),
                Optional.of("alert('blocked');"),
                Optional.of(true))).isFalse();
        assertThat(policy.allowsScriptAsAttribute(Optional.of(scriptSource))).isTrue();
        assertThat(policy.allowsScriptAsAttribute(Optional.of("alert('blocked');"))).isFalse();
        assertThat(policy.allowsEval()).isFalse();
        assertThat(policy.allowsInlineStyle(Optional.empty(), Optional.of("body { color: red; }"))).isTrue();

        Policy externalPolicy = Policy.parseSerializedCSP(
                "script-src 'none'; script-src-elem https://scripts.example.com",
                Policy.PolicyErrorConsumer.ignored);
        assertThat(externalPolicy.allowsExternalScript(
                Optional.empty(),
                Optional.empty(),
                Optional.of(uri("https://scripts.example.com/main.js")),
                Optional.of(true),
                Optional.of(origin))).isTrue();
        assertThat(externalPolicy.allowsExternalScript(
                Optional.empty(),
                Optional.empty(),
                Optional.of(uri("https://cdn.example.com/main.js")),
                Optional.of(true),
                Optional.of(origin))).isFalse();

        Policy integrityPolicy = Policy.parseSerializedCSP(
                "script-src 'sha256-" + scriptHash + "'", Policy.PolicyErrorConsumer.ignored);
        assertThat(integrityPolicy.allowsExternalScript(
                Optional.empty(),
                Optional.of("sha256-" + scriptHash),
                Optional.empty(),
                Optional.of(true),
                Optional.of(origin))).isTrue();
        assertThat(integrityPolicy.allowsExternalScript(
                Optional.empty(),
                Optional.of("sha256-" + sha256("different") + " sha256-" + scriptHash),
                Optional.empty(),
                Optional.of(true),
                Optional.of(origin))).isFalse();
    }

    @Test
    void evaluatesNavigationJavascriptUrlAndSandboxRestrictions() {
        URLWithScheme origin = uri("https://app.example.com/index.html");
        Policy policy = Policy.parseSerializedCSP(
                "navigate-to https://allowed.example.com 'unsafe-allow-redirects'; "
                        + "form-action https://forms.example.com; "
                        + "script-src 'sha256-" + sha256("javascript:alert(1)") + "' 'unsafe-hashes'",
                Policy.PolicyErrorConsumer.ignored);

        assertThat(policy.allowsNavigation(
                Optional.of(uri("https://allowed.example.com/start")),
                Optional.of(false),
                Optional.empty(),
                Optional.of(origin))).isTrue();
        assertThat(policy.allowsNavigation(
                Optional.of(uri("https://allowed.example.com/start")),
                Optional.of(true),
                Optional.of(uri("https://allowed.example.com/finish")),
                Optional.of(origin))).isTrue();
        assertThat(policy.allowsNavigation(
                Optional.of(uri("https://allowed.example.com/start")),
                Optional.of(true),
                Optional.of(uri("https://blocked.example.com/finish")),
                Optional.of(origin))).isFalse();
        assertThat(policy.allowsFormAction(
                Optional.of(uri("https://forms.example.com/post")),
                Optional.of(false),
                Optional.empty(),
                Optional.of(origin))).isTrue();

        Policy javascriptPolicy = Policy.parseSerializedCSP(
                "script-src 'sha256-" + sha256("javascript:alert(1)") + "' 'unsafe-hashes'",
                Policy.PolicyErrorConsumer.ignored);
        assertThat(javascriptPolicy.allowsJavascriptUrlNavigation(
                Optional.of("alert(1)"),
                Optional.of(origin))).isTrue();
        assertThat(javascriptPolicy.allowsJavascriptUrlNavigation(
                Optional.of("alert(2)"),
                Optional.of(origin))).isFalse();

        Policy sandboxed = Policy.parseSerializedCSP(
                "sandbox; script-src 'unsafe-inline'; form-action https://forms.example.com",
                Policy.PolicyErrorConsumer.ignored);
        assertThat(sandboxed.allowsInlineScript(Optional.empty(), Optional.empty(), Optional.of(true))).isFalse();
        assertThat(sandboxed.allowsFormAction(
                Optional.of(uri("https://forms.example.com/post")),
                Optional.of(false),
                Optional.empty(),
                Optional.of(origin))).isFalse();
    }

    @Test
    void addsDirectivesThroughPublicApiAndUsesThemForPolicyEvaluation() {
        Policy policy = Policy.parseSerializedCSP("default-src 'none'", Policy.PolicyErrorConsumer.ignored);
        List<String> messages = new ArrayList<>();

        Directive addedBaseUri = policy.add(
                "base-uri",
                List.of("'self'"),
                (severity, message, valueIndex) -> messages.add(message));
        assertThat(addedBaseUri).isInstanceOf(SourceExpressionDirective.class);
        SourceExpressionDirective baseUri = (SourceExpressionDirective) addedBaseUri;
        assertThat(policy.baseUri()).containsSame(baseUri);
        assertThat(policy.baseUri().orElseThrow().self()).isTrue();

        Directive addedManifestSource = policy.add(
                "manifest-src",
                List.of("https://assets.example.com/manifests/"),
                (severity, message, valueIndex) -> messages.add(message));
        assertThat(addedManifestSource).isInstanceOf(SourceExpressionDirective.class);
        SourceExpressionDirective manifestSource = (SourceExpressionDirective) addedManifestSource;
        assertThat(policy.getFetchDirective(FetchDirectiveKind.ManifestSrc)).containsSame(manifestSource);

        URLWithScheme origin = uri("https://app.example.com/index.html");
        assertThat(policy.allowsApplicationManifest(
                Optional.of(uri("https://assets.example.com/manifests/site.webmanifest")),
                Optional.of(origin))).isTrue();
        assertThat(policy.allowsApplicationManifest(
                Optional.of(uri("https://assets.example.com/private/site.webmanifest")),
                Optional.of(origin))).isFalse();
        assertThat(policy.allowsApplicationManifest(Optional.empty(), Optional.of(origin))).isFalse();

        policy.add(
                "manifest-src",
                List.of("https://duplicate.example.com"),
                (severity, message, valueIndex) -> messages.add(message));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("Duplicate directive"));
        assertThat(policy.getFetchDirective(FetchDirectiveKind.ManifestSrc)).containsSame(manifestSource);
    }

    @Test
    void evaluatesElementAndAttributeSpecificStylePermissions() {
        String styleAttribute = "color: red;";
        String styleHash = sha256(styleAttribute);
        URLWithScheme origin = uri("https://app.example.com/index.html");
        Policy policy = Policy.parseSerializedCSP(
                "style-src 'self'; "
                        + "style-src-elem https://styles.example.com; "
                        + "style-src-attr 'sha256-" + styleHash + "' 'unsafe-hashes'",
                Policy.PolicyErrorConsumer.ignored);

        assertThat(policy.getGoverningDirectiveForEffectiveDirective(FetchDirectiveKind.StyleSrcElem))
                .contains(policy.getFetchDirective(FetchDirectiveKind.StyleSrcElem).orElseThrow());
        assertThat(policy.getGoverningDirectiveForEffectiveDirective(FetchDirectiveKind.StyleSrcAttr))
                .contains(policy.getFetchDirective(FetchDirectiveKind.StyleSrcAttr).orElseThrow());
        assertThat(policy.allowsExternalStyle(
                Optional.empty(),
                Optional.of(uri("https://styles.example.com/main.css")),
                Optional.of(origin))).isTrue();
        assertThat(policy.allowsExternalStyle(
                Optional.empty(),
                Optional.of(uri("https://app.example.com/main.css")),
                Optional.of(origin))).isFalse();
        assertThat(policy.allowsStyleAsAttribute(Optional.of(styleAttribute))).isTrue();
        assertThat(policy.allowsStyleAsAttribute(Optional.of("color: blue;"))).isFalse();

        Policy fallbackPolicy = Policy.parseSerializedCSP("style-src 'self'", Policy.PolicyErrorConsumer.ignored);
        assertThat(fallbackPolicy.getGoverningDirectiveForEffectiveDirective(FetchDirectiveKind.StyleSrcElem))
                .contains(fallbackPolicy.getFetchDirective(FetchDirectiveKind.StyleSrc).orElseThrow());
        assertThat(fallbackPolicy.allowsExternalStyle(
                Optional.empty(),
                Optional.of(uri("https://app.example.com/main.css")),
                Optional.of(origin))).isTrue();
    }

    @Test
    void mapsDirectiveNamesToFetchDirectiveKinds() {
        assertThat(FetchDirectiveKind.fromString("script-src")).isEqualTo(FetchDirectiveKind.ScriptSrc);
        assertThat(FetchDirectiveKind.ScriptSrc.repr).isEqualTo("script-src");
        assertThat(FetchDirectiveKind.fromString("not-a-fetch-directive")).isNull();

        Policy policy = Policy.parseSerializedCSP(
                "default-src 'none'; child-src https://children.example.com",
                Policy.PolicyErrorConsumer.ignored);
        assertThat(policy.getGoverningDirectiveForEffectiveDirective(FetchDirectiveKind.FrameSrc))
                .contains(policy.getFetchDirective(FetchDirectiveKind.ChildSrc).orElseThrow());
        assertThat(policy.getGoverningDirectiveForEffectiveDirective(FetchDirectiveKind.WorkerSrc))
                .contains(policy.getFetchDirective(FetchDirectiveKind.ChildSrc).orElseThrow());
    }

    private static URI uri(String value) {
        return URI.parseURI(value).orElseThrow();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static final class PolicyListError {
        private final String message;
        private final int policyIndex;

        private PolicyListError(String message, int policyIndex) {
            this.message = message;
            this.policyIndex = policyIndex;
        }
    }
}
