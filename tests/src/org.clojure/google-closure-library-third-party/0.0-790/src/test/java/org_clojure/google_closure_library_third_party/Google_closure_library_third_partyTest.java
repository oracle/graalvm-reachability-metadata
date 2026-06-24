/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_clojure.google_closure_library_third_party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class Google_closure_library_third_partyTest {
    private static final Pattern JAVASCRIPT_OBJECT_ENTRY_PATTERN = Pattern.compile("'([^']+)'\\s*:\\s*([^,\\n]+)");

    private static final List<String> DISTRIBUTED_RESOURCES = List.of(
            "goog/caja/string/html/htmlparser.js",
            "goog/caja/string/html/htmlsanitizer.js",
            "goog/dojo/dom/query.js",
            "goog/dojo/dom/query_test.html",
            "goog/dojo/dom/query_test.js",
            "goog/jpeg_encoder/jpeg_encoder_basic.js",
            "goog/loremipsum/text/loremipsum.js",
            "goog/loremipsum/text/loremipsum_test.html",
            "goog/mochikit/async/deferred.js",
            "goog/mochikit/async/deferred_test.html",
            "goog/mochikit/async/deferredlist.js",
            "goog/mochikit/async/deferredlist_test.html",
            "goog/osapi/osapi.js",
            "goog/silverlight/clipboardbutton.js",
            "goog/silverlight/silverlight.js",
            "goog/silverlight/supporteduseragent.js",
            "README",
            "AUTHORS",
            "LICENSE");

    private static final Map<String, List<String>> MODULE_PROVIDES = Map.of(
            "goog/caja/string/html/htmlparser.js",
            List.of(
                    "goog.string.html.HtmlParser",
                    "goog.string.html.HtmlParser.EFlags",
                    "goog.string.html.HtmlParser.Elements",
                    "goog.string.html.HtmlParser.Entities",
                    "goog.string.html.HtmlSaxHandler"),
            "goog/caja/string/html/htmlsanitizer.js",
            List.of(
                    "goog.string.html.HtmlSanitizer",
                    "goog.string.html.HtmlSanitizer.AttributeType",
                    "goog.string.html.HtmlSanitizer.Attributes",
                    "goog.string.html.htmlSanitize"),
            "goog/dojo/dom/query.js",
            List.of("goog.dom.query"),
            "goog/jpeg_encoder/jpeg_encoder_basic.js",
            List.of("goog.crypt.JpegEncoder"),
            "goog/loremipsum/text/loremipsum.js",
            List.of("goog.text.LoremIpsum"),
            "goog/mochikit/async/deferred.js",
            List.of(
                    "goog.async.Deferred",
                    "goog.async.Deferred.AlreadyCalledError",
                    "goog.async.Deferred.CancelledError"),
            "goog/mochikit/async/deferredlist.js",
            List.of("goog.async.DeferredList"),
            "goog/osapi/osapi.js",
            List.of("goog.osapi"),
            "goog/silverlight/clipboardbutton.js",
            List.of(
                    "goog.silverlight.ClipboardButton",
                    "goog.silverlight.ClipboardButtonType",
                    "goog.silverlight.ClipboardEvent"));

    @Test
    void distributedResourceSetIsAvailableOnTheClasspath() throws IOException {
        for (String resourcePath : DISTRIBUTED_RESOURCES) {
            String resource = readResource(resourcePath);

            assertThat(resource)
                    .as("resource %s should be packaged and readable", resourcePath)
                    .isNotBlank();
        }
    }

    @Test
    void closureModulesDeclareExpectedPublicNamespaces() throws IOException {
        for (Map.Entry<String, List<String>> module : MODULE_PROVIDES.entrySet()) {
            String source = readResource(module.getKey());

            for (String providedNamespace : module.getValue()) {
                assertThat(source)
                        .as("%s should provide %s", module.getKey(), providedNamespace)
                        .contains("goog.provide('" + providedNamespace + "')");
            }
        }
    }

    @Test
    void closureModulesKeepExpectedThirdPartyIntegrationPoints() throws IOException {
        assertThat(readResource("goog/caja/string/html/htmlparser.js"))
                .contains("@fileoverview A Html SAX parser.")
                .contains("goog.string.html.HtmlParser = function()")
                .contains("goog.string.html.HtmlParser.prototype.parse = function(handler, htmlText)")
                .contains("goog.string.html.HtmlSaxHandler.prototype.startTag");

        assertThat(readResource("goog/caja/string/html/htmlsanitizer.js"))
                .contains("goog.require('goog.string.html.HtmlParser')")
                .contains("goog.string.html.htmlSanitize = function(")
                .contains("goog.string.html.HtmlSanitizer.prototype.sanitize");

        assertThat(readResource("goog/mochikit/async/deferred.js"))
                .contains("goog.async.Deferred.prototype.callback")
                .contains("goog.async.Deferred.prototype.errback")
                .contains("goog.async.Deferred.prototype.cancel")
                .contains("goog.async.Deferred.prototype.chainDeferred")
                .contains("goog.async.Deferred.prototype.awaitDeferred")
                .contains("goog.async.Deferred.prototype.branch");

        assertThat(readResource("goog/mochikit/async/deferredlist.js"))
                .contains("goog.require('goog.async.Deferred')")
                .contains("goog.async.DeferredList.prototype.handleCallback_")
                .contains("goog.async.DeferredList.prototype.errback")
                .contains("goog.async.DeferredList.gatherResults");
    }

    @Test
    void deferredModuleCoversCancellationErrorsAndChaining() throws IOException {
        assertThat(readResource("goog/mochikit/async/deferred.js"))
                .contains("goog.provide('goog.async.Deferred.CancelledError')")
                .contains("this.errback(new goog.async.Deferred.CancelledError(this))")
                .contains("goog.async.Deferred.CancelledError = function(deferred)")
                .contains("goog.async.Deferred.CancelledError.prototype.message = 'Deferred was cancelled'")
                .contains("throw new goog.async.Deferred.AlreadyCalledError(this)")
                .contains("goog.async.Deferred.succeed = function(res)")
                .contains("goog.async.Deferred.fail = function(res)");
    }

    @Test
    void dojoQuerySelectorEngineSupportsCssSelectorsAttributesAndPseudos() throws IOException {
        assertThat(readResource("goog/dojo/dom/query.js"))
                .contains("'*=': function(attr, value)")
                .contains("'^=': function(attr, value)")
                .contains("'$=': function(attr, value)")
                .contains("'~=': function(attr, value)")
                .contains("'|=': function(attr, value)")
                .contains("'=': function(attr, value)")
                .contains("'first-child': function()")
                .contains("'last-child': function()")
                .contains("'only-child': function(name, condition)")
                .contains("'empty': function(name, condition)")
                .contains("'contains': function(name, condition)")
                .contains("'not': function(name, condition)")
                .contains("'nth-child': function(name, condition)")
                .contains("goog.exportSymbol('goog.dom.query', goog.dom.query)")
                .contains("goog.exportSymbol('goog.dom.query.pseudos', goog.dom.query.pseudos)");
    }

    @Test
    void bundledJavascriptFixturesReferenceThePackagedModules() throws IOException {
        assertThat(readResource("goog/mochikit/async/deferred_test.html"))
                .contains("Closure Unit Tests - goog.async.Deferred")
                .contains("goog.require('goog.async.Deferred')")
                .contains("var Deferred = goog.async.Deferred")
                .contains("function testNormal()")
                .contains("function testCancel()")
                .contains("function testDeferredDependencies()")
                .contains("function testBranch()");

        assertThat(readResource("goog/mochikit/async/deferredlist_test.html"))
                .contains("Closure Unit Tests - goog.async.DeferredList")
                .contains("goog.require('goog.async.DeferredList')")
                .contains("var DeferredList = goog.async.DeferredList")
                .contains("function testDeferredList()")
                .contains("function testGatherResults()")
                .contains("function testErrorCancelsPendingChildrenWhenFireOnFirstError()");

        assertThat(readResource("goog/loremipsum/text/loremipsum_test.html"))
                .contains("goog.require('goog.text.LoremIpsum')")
                .contains("function testLoremIpsum()")
                .contains("generator.generateParagraph(true)");

        assertThat(readResource("goog/dojo/dom/query_test.js"))
                .contains("function testBasicSelectors()")
                .contains("function testNthChild()")
                .contains("function testAttributes()");

        assertThat(readResource("goog/dojo/dom/query_test.html"))
                .contains("<h1>testing goog.dom.query()</h1>")
                .contains("id=iframe-test");
    }

    @Test
    void htmlParserAndSanitizerRetainEscapingAndAttributePolicies() throws IOException {
        assertThat(readResource("goog/caja/string/html/htmlparser.js"))
                .contains("lt: '<'")
                .contains("gt: '>'")
                .contains("amp: '&'")
                .contains("nbsp: '\\240'")
                .contains("quot: '\"'")
                .contains("apos: '\\\''")
                .contains("'script': goog.string.html.HtmlParser.EFlags.UNSAFE |")
                .contains("'style': goog.string.html.HtmlParser.EFlags.UNSAFE |");

        Map<String, String> attributeTypes = extractJavascriptObjectEntries(
                readResource("goog/caja/string/html/htmlsanitizer.js"),
                "goog.string.html.HtmlSanitizer.Attributes = ");

        assertThat(attributeTypes)
                .contains(
                        entry("a::href", "goog.string.html.HtmlSanitizer.AttributeType.URI"),
                        entry("form::action", "goog.string.html.HtmlSanitizer.AttributeType.URI"),
                        entry("img::src", "goog.string.html.HtmlSanitizer.AttributeType.URI"),
                        entry("img::usemap", "goog.string.html.HtmlSanitizer.AttributeType.URI_FRAGMENT"),
                        entry("*::id", "goog.string.html.HtmlSanitizer.AttributeType.ID"),
                        entry("label::for", "goog.string.html.HtmlSanitizer.AttributeType.IDREF"),
                        entry("td::headers", "goog.string.html.HtmlSanitizer.AttributeType.IDREFS"),
                        entry("*::class", "goog.string.html.HtmlSanitizer.AttributeType.CLASSES"),
                        entry("a::name", "goog.string.html.HtmlSanitizer.AttributeType.GLOBAL_NAME"),
                        entry("input::name", "goog.string.html.HtmlSanitizer.AttributeType.LOCAL_NAME"),
                        entry("*::onclick", "goog.string.html.HtmlSanitizer.AttributeType.SCRIPT"),
                        entry("form::onsubmit", "goog.string.html.HtmlSanitizer.AttributeType.SCRIPT"),
                        entry("*::style", "goog.string.html.HtmlSanitizer.AttributeType.STYLE"),
                        entry("*::title", "0"))
                .doesNotContainKey("script::src");
    }

    @Test
    void optionalBrowserUtilitiesRetainTheirEntryPoints() throws IOException {
        assertThat(readResource("goog/loremipsum/text/loremipsum.js"))
                .contains("goog.text.LoremIpsum = function()")
                .contains("goog.text.LoremIpsum.prototype.generateParagraph")
                .contains("goog.text.LoremIpsum.prototype.generateSentence");

        assertThat(readResource("goog/jpeg_encoder/jpeg_encoder_basic.js"))
                .contains("JPEG encoder ported to JavaScript")
                .contains("goog.crypt.JpegEncoder = function(opt_quality)")
                .contains("this.encode = function(image,opt_quality)");

        assertThat(readResource("goog/dojo/dom/query.js"))
                .contains("goog.dom.query = (function()")
                .contains("This code was ported from the Dojo Toolkit")
                .contains("querySelectorAll");

        assertThat(readResource("goog/osapi/osapi.js"))
                .contains("Base OSAPI binding")
                .contains("goog.exportSymbol('osapi', osapi)")
                .contains("goog.osapi.handleGadgetRpcMethod = function(requests)")
                .contains("goog.osapi.init = function()");

        assertThat(readResource("goog/silverlight/clipboardbutton.js"))
                .contains("native clipboard access")
                .contains("goog.silverlight.ClipboardButton =")
                .contains("goog.silverlight.CopyButton =")
                .contains("goog.silverlight.PasteButton =")
                .contains("goog.silverlight.CopyButton.prototype.handleCopy_")
                .contains("goog.silverlight.PasteButton.prototype.handlePaste_");
    }

    @Test
    void projectDocumentationAndLicenseArePackaged() throws IOException {
        assertThat(readResource("README"))
                .contains("Closure Library is a powerful, low level JavaScript library")
                .contains("building complex and scalable web applications")
                .contains("http://code.google.com/closure/library");

        assertThat(readResource("AUTHORS"))
                .contains("Closure Library")
                .contains("Google Inc.");

        assertThat(readResource("LICENSE"))
                .contains("Apache License")
                .contains("Version 2.0, January 2004")
                .contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION");
    }

    @Test
    void packagedModulesRemainInExpectedThirdPartyGroups() {
        Map<String, Long> resourceCountsByGroup = DISTRIBUTED_RESOURCES.stream()
                .filter(resourcePath -> resourcePath.startsWith("goog/"))
                .collect(Collectors.groupingBy(
                        resourcePath -> resourcePath.substring(0, resourcePath.indexOf('/', "goog/".length())),
                        Collectors.counting()));

        assertThat(resourceCountsByGroup)
                .contains(
                        entry("goog/caja", 2L),
                        entry("goog/dojo", 3L),
                        entry("goog/jpeg_encoder", 1L),
                        entry("goog/loremipsum", 2L),
                        entry("goog/mochikit", 4L),
                        entry("goog/osapi", 1L),
                        entry("goog/silverlight", 3L));
    }

    private static Map<String, String> extractJavascriptObjectEntries(String source, String objectDeclaration) {
        int objectDeclarationStart = source.indexOf(objectDeclaration);
        assertThat(objectDeclarationStart)
                .as("object declaration %s should exist", objectDeclaration)
                .isNotNegative();

        int objectBodyStart = source.indexOf('{', objectDeclarationStart);
        int objectBodyEnd = source.indexOf("\n};", objectBodyStart);
        assertThat(objectBodyStart)
                .as("object declaration %s should have a body", objectDeclaration)
                .isNotNegative();
        assertThat(objectBodyEnd)
                .as("object declaration %s should have a closing marker", objectDeclaration)
                .isGreaterThan(objectBodyStart);

        String objectBody = source.substring(objectBodyStart + 1, objectBodyEnd);
        Matcher entryMatcher = JAVASCRIPT_OBJECT_ENTRY_PATTERN.matcher(objectBody);
        Map<String, String> entries = new LinkedHashMap<>();
        while (entryMatcher.find()) {
            entries.put(entryMatcher.group(1), entryMatcher.group(2).trim());
        }

        assertThat(entries)
                .as("object declaration %s should contain entries", objectDeclaration)
                .isNotEmpty();
        return entries;
    }

    private static String readResource(String resourcePath) throws IOException {
        ClassLoader classLoader = Google_closure_library_third_partyTest.class.getClassLoader();
        try (InputStream resourceStream = classLoader.getResourceAsStream(resourcePath)) {
            assertThat(resourceStream)
                    .as("resource %s should exist", resourcePath)
                    .isNotNull();
            return new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
