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
            "goog/dojo/dom/query.js",
            "goog/dojo/dom/query_test.js",
            "goog/dojo/dom/query_test_dom.html",
            "goog/mochikit/BUILD",
            "goog/mochikit/LICENSE",
            "goog/mochikit/async/BUILD",
            "goog/mochikit/async/deferred.js",
            "goog/mochikit/async/deferred_async_test.js",
            "goog/mochikit/async/deferred_async_test_dom.html",
            "goog/mochikit/async/deferred_test.js",
            "goog/mochikit/async/deferredlist.js",
            "goog/mochikit/async/deferredlist_test.js",
            "README.md",
            "AUTHORS",
            "LICENSE");

    private static final Map<String, List<String>> MODULE_PROVIDES = Map.of(
            "goog/mochikit/async/deferred.js",
            List.of(
                    "goog.async.Deferred",
                    "goog.async.Deferred.AlreadyCalledError",
                    "goog.async.Deferred.CanceledError"),
            "goog/mochikit/async/deferredlist.js",
            List.of("goog.async.DeferredList"),
            "goog/dojo/dom/query.js",
            List.of("goog.dom.query"));

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
        assertThat(readResource("goog/mochikit/async/deferred_test.js"))
                .contains("goog.module('goog.async.deferredTest')")
                .contains("const Deferred = goog.require('goog.async.Deferred')")
                .contains("testNormal()")
                .contains("testCancel()")
                .contains("testDeferredDependencies()");

        assertThat(readResource("goog/mochikit/async/deferred_async_test.js"))
                .contains("goog.module('goog.async.deferredAsyncTest')")
                .contains("const Deferred = goog.require('goog.async.Deferred')")
                .contains("testErrorStack()");

        assertThat(readResource("goog/mochikit/async/deferredlist_test.js"))
                .contains("goog.module('goog.async.deferredListTest')")
                .contains("const DeferredList = goog.require('goog.async.DeferredList')")
                .contains("testDeferredList()")
                .contains("testGatherResults()");

        assertThat(readResource("goog/mochikit/async/deferred_async_test_dom.html"))
                .contains("goog.async.Deferred.LONG_STACK_TRACES = true;");

        assertThat(readResource("goog/dojo/dom/query_test.js"))
                .contains("function testBasicSelectors()")
                .contains("function testNthChild()")
                .contains("function testAttributes()");

        assertThat(readResource("goog/dojo/dom/query_test_dom.html"))
                .contains("<h1>testing goog.dom.query()</h1>")
                .contains("id=iframe-test");
    }

    @Test
    void projectDocumentationAndLicenseArePackaged() throws IOException {
        assertThat(readResource("README.md"))
                .contains("# Closure Library")
                .contains("*actively* maintained by the Clojure team")
                .contains("Google stopped contributing to Closure Library on August 2024")
                .contains("Previous version of this README can be found here");

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
                        entry("goog/dojo", 3L),
                        entry("goog/mochikit", 9L));
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
