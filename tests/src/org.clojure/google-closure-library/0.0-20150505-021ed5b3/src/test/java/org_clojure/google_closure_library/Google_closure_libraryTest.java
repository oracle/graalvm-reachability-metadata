/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_clojure.google_closure_library;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class Google_closure_libraryTest {
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "goog\\.addDependency\\('([^']+)', \\[([^]]*)], \\[([^]]*)], (true|false)\\);");
    private static final Pattern QUOTED_SYMBOL_PATTERN = Pattern.compile("'([^']+)'");

    @Test
    void baseBootstrapResourceContainsClosureNamespaceLoaderApi() throws Exception {
        String baseJs = readResource("goog/base.js");

        assertThat(baseJs)
                .contains("@provideGoog")
                .contains("var COMPILED = false;")
                .contains("var goog = goog || {};")
                .contains("goog.provide = function(name)")
                .contains("goog.module = function(name)")
                .contains("goog.require = function(name)")
                .contains("goog.addDependency = function(relPath, provides, requires, opt_isModule)")
                .contains("goog.getObjectByName = function(name, opt_obj)")
                .contains("goog.inherits = function(childCtor, parentCtor)")
                .contains("goog.defineClass = function(superClass, def)");
    }

    @Test
    void depsCatalogMapsImportantNamespacesToPackagedResources() throws Exception {
        Map<String, Dependency> dependenciesByProvide = dependenciesByProvide();

        assertDependency(dependenciesByProvide, "goog.array", "array/array.js", "goog.asserts");
        assertDependency(dependenciesByProvide, "goog.object", "object/object.js");
        assertDependency(dependenciesByProvide, "goog.string", "string/string.js");
        assertDependency(dependenciesByProvide, "goog.uri.utils", "uri/utils.js", "goog.asserts", "goog.string");
        assertDependency(dependenciesByProvide, "goog.json.Serializer", "json/json.js");
        assertDependency(dependenciesByProvide, "goog.date.Date", "date/date.js", "goog.i18n.DateTimeSymbols");
        assertDependency(dependenciesByProvide, "goog.crypt", "crypt/crypt.js", "goog.array");
        assertDependency(dependenciesByProvide, "goog.structs.Map", "structs/map.js", "goog.iter.Iterator",
                "goog.object");
        assertDependency(dependenciesByProvide, "goog.events.EventTarget", "events/eventtarget.js", "goog.Disposable",
                "goog.events.ListenerMap");
        assertDependency(dependenciesByProvide, "goog.Promise", "promise/promise.js", "goog.Thenable",
                "goog.async.run");
        assertDependency(dependenciesByProvide, "goog.html.SafeHtml", "html/safehtml.js", "goog.html.SafeUrl",
                "goog.string.TypedString");
        assertDependency(dependenciesByProvide, "goog.ui.Component", "ui/component.js", "goog.events.EventTarget",
                "goog.ui.IdGenerator");
    }

    @Test
    void coreModulesExposeExpectedClosureLibraryApis() throws Exception {
        assertResourceContains("goog/array/array.js", "goog.provide('goog.array');", "goog.array.indexOf =",
                "goog.array.forEach =", "goog.array.map =", "goog.array.reduce =", "goog.array.binarySearch =",
                "goog.array.stableSort =");
        assertResourceContains("goog/object/object.js", "goog.provide('goog.object');",
                "goog.object.forEach = function", "goog.object.filter = function", "goog.object.map = function",
                "goog.object.getKeys = function", "goog.object.clone = function", "goog.object.create = function");
        assertResourceContains("goog/string/string.js", "goog.provide('goog.string');",
                "goog.string.startsWith = function", "goog.string.endsWith = function", "goog.string.subs = function",
                "goog.string.htmlEscape = function",
                "goog.string.compareVersions = function", "goog.string.toCamelCase = function");
        assertResourceContains("goog/uri/utils.js", "goog.provide('goog.uri.utils');",
                "goog.uri.utils.buildFromEncodedParts = function", "goog.uri.utils.split = function",
                "goog.uri.utils.getScheme = function", "goog.uri.utils.getPathEncoded = function",
                "goog.uri.utils.appendParams = function");
        assertResourceContains("goog/math/math.js", "goog.provide('goog.math');", "goog.math.clamp = function",
                "goog.math.modulo = function", "goog.math.lerp = function", "goog.math.angle = function",
                "goog.math.longestCommonSubsequence = function", "goog.math.average = function");
        assertResourceContains("goog/json/json.js", "goog.provide('goog.json');", "goog.json.isValid = function",
                "goog.json.parse =", "goog.json.serialize =", "goog.json.Serializer = function",
                "goog.json.Serializer.prototype.serialize = function");
    }

    @Test
    void structuredBrowserFacingModulesArePackagedWithConstructorsAndFactories() throws Exception {
        assertResourceContains("goog/date/date.js", "goog.provide('goog.date.Date');",
                "goog.date.isLeapYear = function", "goog.date.getWeekNumber = function",
                "goog.date.Interval = function", "goog.date.Date = function",
                "goog.date.DateTime = function");
        assertResourceContains("goog/crypt/crypt.js", "goog.provide('goog.crypt');",
                "goog.crypt.stringToByteArray = function", "goog.crypt.byteArrayToHex = function",
                "goog.crypt.hexToByteArray = function", "goog.crypt.stringToUtf8ByteArray = function",
                "goog.crypt.xorByteArray = function");
        assertResourceContains("goog/structs/map.js", "goog.provide('goog.structs.Map');",
                "goog.structs.Map = function", "goog.structs.Map.prototype.getCount = function",
                "goog.structs.Map.prototype.set = function", "goog.structs.Map.prototype.clone = function",
                "goog.structs.Map.prototype.getKeyIterator = function");
        assertResourceContains("goog/events/eventtarget.js", "goog.provide('goog.events.EventTarget');",
                "goog.events.EventTarget = function", "goog.events.EventTarget.prototype.addEventListener = function",
                "goog.events.EventTarget.prototype.dispatchEvent = function",
                "goog.events.EventTarget.prototype.listenOnce = function",
                "goog.events.EventTarget.prototype.removeAllListeners = function");
        assertResourceContains("goog/promise/promise.js", "goog.provide('goog.Promise');", "goog.Promise = function",
                "goog.Promise.resolve = function", "goog.Promise.reject = function", "goog.Promise.all = function",
                "goog.Promise.prototype.then = function", "goog.Promise.prototype.cancel = function");
        assertResourceContains("goog/html/safehtml.js", "goog.provide('goog.html.SafeHtml');",
                "goog.html.SafeHtml.unwrap = function", "goog.html.SafeHtml.htmlEscape = function",
                "goog.html.SafeHtml.create = function", "goog.html.SafeHtml.concat = function");
    }

    @Test
    void internationalizationModulesArePackagedWithFormattingApis() throws Exception {
        Map<String, Dependency> dependenciesByProvide = dependenciesByProvide();

        assertDependency(dependenciesByProvide, "goog.i18n.NumberFormat", "i18n/numberformat.js", "goog.asserts",
                "goog.i18n.NumberFormatSymbols", "goog.i18n.currency", "goog.math");
        assertDependency(dependenciesByProvide, "goog.i18n.MessageFormat", "i18n/messageformat.js", "goog.asserts",
                "goog.i18n.NumberFormat", "goog.i18n.ordinalRules", "goog.i18n.pluralRules");
        assertDependency(dependenciesByProvide, "goog.i18n.DateTimeFormat", "i18n/datetimeformat.js", "goog.date",
                "goog.i18n.DateTimeSymbols", "goog.i18n.TimeZone", "goog.string");
        assertDependency(dependenciesByProvide, "goog.i18n.BidiFormatter", "i18n/bidiformatter.js",
                "goog.html.SafeHtml", "goog.i18n.bidi", "goog.i18n.bidi.Dir", "goog.i18n.bidi.Format");

        assertResourceContains("goog/i18n/numberformat.js", "goog.provide('goog.i18n.NumberFormat');",
                "goog.provide('goog.i18n.NumberFormat.Format');", "goog.i18n.NumberFormat = function",
                "goog.i18n.NumberFormat.prototype.format = function",
                "goog.i18n.NumberFormat.prototype.parse = function",
                "goog.i18n.NumberFormat.prototype.setMinimumFractionDigits = function");
        assertResourceContains("goog/i18n/messageformat.js", "goog.provide('goog.i18n.MessageFormat');",
                "goog.i18n.MessageFormat = function", "goog.i18n.MessageFormat.prototype.format = function",
                "goog.i18n.MessageFormat.prototype.formatIgnoringPound =");
        assertResourceContains("goog/i18n/datetimeformat.js", "goog.provide('goog.i18n.DateTimeFormat');",
                "goog.provide('goog.i18n.DateTimeFormat.Format');", "goog.i18n.DateTimeFormat = function",
                "goog.i18n.DateTimeFormat.prototype.format = function");
        assertResourceContains("goog/i18n/bidiformatter.js", "goog.provide('goog.i18n.BidiFormatter');",
                "goog.i18n.BidiFormatter = function", "goog.i18n.BidiFormatter.prototype.estimateDirection =",
                "goog.i18n.BidiFormatter.prototype.setAlwaysSpan = function");
    }

    @Test
    void packagedClosureTestsAndDemosRemainAvailableAsResources() throws Exception {
        assertResourceContains("goog/base_test.html", "goog.require('goog.baseTest');");
        assertResourceContains("goog/base_test.js", "goog.provide('goog.baseTest');",
                "goog.require('goog.testing.jsunit');", "function testProvide", "function testAddDependency");
        assertResourceContains("goog/array/array_test.js", "goog.provide('goog.arrayTest');",
                "goog.require('goog.array');", "function testArrayIndexOf", "function testStableSort");
        assertResourceContains("goog/testing/jsunit.js", "goog.provide('goog.testing.jsunit');",
                "goog.require('goog.testing.TestCase');", "goog.require('goog.testing.TestRunner');");
        assertResourceContains("goog/demos/jsonprettyprinter.html", "goog.require('goog.format.JsonPrettyPrinter');",
                "JsonPrettyPrinter");
    }

    private static void assertDependency(Map<String, Dependency> dependenciesByProvide, String namespace, String path,
            String... requiredNamespaces) throws Exception {
        Dependency dependency = dependenciesByProvide.get(namespace);

        assertThat(dependency).as(namespace).isNotNull();
        assertThat(dependency.path()).isEqualTo(path);
        assertThat(dependency.requires()).contains(requiredNamespaces);
        assertThat(dependency.module()).isFalse();

        String resource = readResource("goog/" + path);
        assertThat(resource).contains("goog.provide('" + namespace + "');");
    }

    private static Map<String, Dependency> dependenciesByProvide() throws Exception {
        String depsJs = readResource("goog/deps.js");
        Matcher matcher = DEPENDENCY_PATTERN.matcher(depsJs);
        Map<String, Dependency> dependenciesByProvide = new HashMap<>();

        while (matcher.find()) {
            Dependency dependency = new Dependency(
                    matcher.group(1), parseSymbols(matcher.group(2)), parseSymbols(matcher.group(3)),
                    Boolean.parseBoolean(matcher.group(4)));
            for (String provide : dependency.provides()) {
                dependenciesByProvide.put(provide, dependency);
            }
        }

        assertThat(dependenciesByProvide)
                .containsKeys("goog.array", "goog.object", "goog.string", "goog.Promise", "goog.ui.Component");
        return dependenciesByProvide;
    }

    private static List<String> parseSymbols(String symbols) {
        Matcher matcher = QUOTED_SYMBOL_PATTERN.matcher(symbols);
        List<String> parsedSymbols = new ArrayList<>();

        while (matcher.find()) {
            parsedSymbols.add(matcher.group(1));
        }

        return parsedSymbols;
    }

    private static void assertResourceContains(String resourcePath, String... expectedSnippets) throws Exception {
        String resource = readResource(resourcePath);

        assertThat(resource).contains(expectedSnippets);
    }

    private static String readResource(String resourcePath) throws IOException {
        ClassLoader classLoader = Google_closure_libraryTest.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream != null) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        Path libraryJar = libraryJarPath();
        try (JarFile jarFile = new JarFile(libraryJar.toFile())) {
            assertThat(jarFile.getEntry(resourcePath)).as(resourcePath).isNotNull();
            try (InputStream inputStream = jarFile.getInputStream(jarFile.getEntry(resourcePath))) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static Path libraryJarPath() throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(Path.of("gradle.properties"))) {
            properties.load(inputStream);
        }

        String[] coordinates = properties.getProperty("library.coordinates").split(":");
        assertThat(coordinates).hasSize(3);
        Path cacheDirectory = Path.of(System.getProperty("user.home"), ".gradle", "caches", "modules-2", "files-2.1",
                coordinates[0], coordinates[1], coordinates[2]);
        String jarFileName = coordinates[1] + "-" + coordinates[2] + ".jar";

        try (Stream<Path> paths = Files.walk(cacheDirectory)) {
            Path jarPath = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(jarFileName))
                    .findFirst()
                    .orElse(null);
            assertThat(jarPath).as(jarFileName).isNotNull();
            return jarPath;
        }
    }

    private record Dependency(String path, List<String> provides, List<String> requires, boolean module) {
    }
}
