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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class Google_closure_library_third_partyTest {
    private static final List<String> DISTRIBUTED_RESOURCES = List.of(
            "goog/caja/string/html/htmlparser.js",
            "goog/caja/string/html/htmlsanitizer.js",
            "goog/dojo/dom/query.js",
            "goog/dojo/dom/query_test.html",
            "goog/dojo/dom/query_test.js",
            "goog/loremipsum/text/loremipsum.js",
            "goog/loremipsum/text/loremipsum_test.html",
            "goog/mochikit/async/deferred.js",
            "goog/mochikit/async/deferred_async_test.html",
            "goog/mochikit/async/deferred_test.html",
            "goog/mochikit/async/deferredlist.js",
            "goog/mochikit/async/deferredlist_test.html",
            "goog/osapi/osapi.js",
            "goog/svgpan/svgpan.js",
            "README.md",
            "AUTHORS",
            "LICENSE");

    private static final Map<String, List<String>> MODULE_PROVIDES = Map.of(
            "goog/caja/string/html/htmlparser.js",
            List.of("goog.string.html.HtmlParser", "goog.string.html.HtmlSaxHandler"),
            "goog/caja/string/html/htmlsanitizer.js",
            List.of("goog.string.html.HtmlSanitizer", "goog.string.html.htmlSanitize"),
            "goog/dojo/dom/query.js",
            List.of("goog.dom.query"),
            "goog/loremipsum/text/loremipsum.js",
            List.of("goog.text.LoremIpsum"),
            "goog/mochikit/async/deferred.js",
            List.of(
                    "goog.async.Deferred",
                    "goog.async.Deferred.AlreadyCalledError",
                    "goog.async.Deferred.CanceledError"),
            "goog/mochikit/async/deferredlist.js",
            List.of("goog.async.DeferredList"),
            "goog/osapi/osapi.js",
            List.of("goog.osapi"),
            "goog/svgpan/svgpan.js",
            List.of("svgpan.SvgPan"));

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
        assertThat(readResource("goog/mochikit/async/deferred.js"))
                .contains("goog.require('goog.Promise')")
                .contains("goog.async.Deferred.prototype.callback")
                .contains("goog.async.Deferred.prototype.errback")
                .contains("goog.async.Deferred.prototype.cancel")
                .contains("goog.async.Deferred.prototype.then")
                .contains("goog.async.Deferred.fromPromise")
                .contains("goog.async.Deferred.when");

        assertThat(readResource("goog/mochikit/async/deferredlist.js"))
                .contains("goog.require('goog.async.Deferred')")
                .contains("goog.async.DeferredList.prototype.handleCallback_")
                .contains("goog.async.DeferredList.prototype.errback")
                .contains("goog.async.DeferredList.gatherResults");

        assertThat(readResource("goog/dojo/dom/query.js"))
                .contains("goog.dom.query = (function()")
                .contains("This code was ported from the Dojo Toolkit")
                .contains("querySelectorAll")
                .contains("goog.exportSymbol('goog.dom.query', goog.dom.query)")
                .contains("goog.exportSymbol('goog.dom.query.pseudos', goog.dom.query.pseudos)");
    }

    @Test
    void deferredModuleCoversCancellationErrorsAndPromiseIntegration() throws IOException {
        assertThat(readResource("goog/mochikit/async/deferred.js"))
                .contains("goog.define('goog.async.Deferred.STRICT_ERRORS', false)")
                .contains("goog.define('goog.async.Deferred.LONG_STACK_TRACES', false)")
                .contains("goog.async.Deferred.prototype.makeStackTraceLong_")
                .contains("goog.async.Deferred.AlreadyCalledError.prototype.name = 'AlreadyCalledError'")
                .contains("goog.async.Deferred.CanceledError.prototype.name = 'CanceledError'")
                .contains("goog.Thenable.addImplementation(goog.async.Deferred)")
                .contains("goog.async.Deferred.assertNoErrors = function()");
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
                .contains("'nth-child': function(name, condition)");
    }

    @Test
    void bundledJavascriptFixturesReferenceThePackagedModules() throws IOException {
        assertThat(readResource("goog/mochikit/async/deferred_test.html"))
                .contains("goog.require('goog.async.Deferred')")
                .contains("function testNormal()")
                .contains("function testCancel()")
                .contains("function testDeferredDependencies()");

        assertThat(readResource("goog/mochikit/async/deferred_async_test.html"))
                .contains("goog.require('goog.async.Deferred')")
                .contains("goog.async.Deferred.LONG_STACK_TRACES = true;")
                .contains("function testErrorStack()");

        assertThat(readResource("goog/mochikit/async/deferredlist_test.html"))
                .contains("goog.require('goog.async.DeferredList')")
                .contains("function testDeferredList()")
                .contains("function testGatherResults()");

        assertThat(readResource("goog/loremipsum/text/loremipsum_test.html"))
                .contains("goog.require('goog.text.LoremIpsum')")
                .contains("function testLoremIpsum()");

        assertThat(readResource("goog/dojo/dom/query_test.js"))
                .contains("function testBasicSelectors()")
                .contains("function testNthChild()")
                .contains("function testAttributes()");

        assertThat(readResource("goog/dojo/dom/query_test.html"))
                .contains("<h1>testing goog.dom.query()</h1>")
                .contains("id=iframe-test");
    }

    @Test
    void projectDocumentationAndLicenseArePackaged() throws IOException {
        assertThat(readResource("README.md"))
                .contains("# Closure Library")
                .contains("Closure Library is a powerful, low-level JavaScript library")
                .contains("https://developers.google.com/closure/library")
                .contains("npm install google-closure-library");

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
                        entry("goog/loremipsum", 2L),
                        entry("goog/mochikit", 5L),
                        entry("goog/osapi", 1L),
                        entry("goog/svgpan", 1L));
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
