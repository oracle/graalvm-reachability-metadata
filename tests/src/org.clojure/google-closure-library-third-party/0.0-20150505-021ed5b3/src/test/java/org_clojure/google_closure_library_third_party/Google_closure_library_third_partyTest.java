/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_clojure.google_closure_library_third_party;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class Google_closure_library_third_partyTest {
    private static final String VERSION = "0.0-20150505-021ed5b3";
    private static final String POM_PROPERTIES =
            "META-INF/maven/org.clojure/google-closure-library-third-party/pom.properties";
    private static final String POM_XML =
            "META-INF/maven/org.clojure/google-closure-library-third-party/pom.xml";
    private static final Path GRADLE_ARTIFACT_CACHE = Path.of(
            System.getProperty("user.home"),
            ".gradle",
            "caches",
            "modules-2",
            "files-2.1",
            "org.clojure",
            "google-closure-library-third-party");
    private static final Pattern PROVIDE_PATTERN = Pattern.compile("goog\\.provide\\('([^']+)'\\);");
    private static final Pattern REQUIRE_PATTERN = Pattern.compile("goog\\.require\\('([^']+)'\\);");

    private static final List<String> PUBLISHED_RESOURCES = List.of(
            "goog/caja/string/html/htmlsanitizer.js",
            "goog/caja/string/html/htmlparser.js",
            "goog/osapi/osapi.js",
            "goog/mochikit/async/deferred_test.html",
            "goog/mochikit/async/deferredlist.js",
            "goog/mochikit/async/deferred_async_test.html",
            "goog/mochikit/async/deferred.js",
            "goog/mochikit/async/deferredlist_test.html",
            "goog/loremipsum/text/loremipsum.js",
            "goog/loremipsum/text/loremipsum_test.html",
            "goog/jpeg_encoder/jpeg_encoder_basic.js",
            "goog/svgpan/svgpan.js",
            "goog/dojo/dom/query_test.js",
            "goog/dojo/dom/query.js",
            "goog/dojo/dom/query_test.html",
            "README.md",
            "AUTHORS",
            "LICENSE",
            POM_XML,
            POM_PROPERTIES);

    private static final Map<String, List<String>> EXPECTED_PROVIDES = new LinkedHashMap<>();
    private static final Map<String, List<String>> EXPECTED_REQUIRES = new LinkedHashMap<>();

    static {
        EXPECTED_PROVIDES.put("goog/caja/string/html/htmlsanitizer.js", List.of(
                "goog.string.html.HtmlSanitizer",
                "goog.string.html.HtmlSanitizer.AttributeType",
                "goog.string.html.HtmlSanitizer.Attributes",
                "goog.string.html.htmlSanitize"));
        EXPECTED_PROVIDES.put("goog/caja/string/html/htmlparser.js", List.of(
                "goog.string.html",
                "goog.string.html.HtmlParser",
                "goog.string.html.HtmlParser.EFlags",
                "goog.string.html.HtmlParser.Elements",
                "goog.string.html.HtmlParser.Entities",
                "goog.string.html.HtmlSaxHandler"));
        EXPECTED_PROVIDES.put("goog/osapi/osapi.js", List.of("goog.osapi"));
        EXPECTED_PROVIDES.put("goog/mochikit/async/deferredlist.js", List.of("goog.async.DeferredList"));
        EXPECTED_PROVIDES.put("goog/mochikit/async/deferred.js", List.of(
                "goog.async.Deferred",
                "goog.async.Deferred.AlreadyCalledError",
                "goog.async.Deferred.CanceledError"));
        EXPECTED_PROVIDES.put("goog/loremipsum/text/loremipsum.js", List.of("goog.text.LoremIpsum"));
        EXPECTED_PROVIDES.put("goog/jpeg_encoder/jpeg_encoder_basic.js", List.of("goog.crypt.JpegEncoder"));
        EXPECTED_PROVIDES.put("goog/svgpan/svgpan.js", List.of("svgpan.SvgPan"));
        EXPECTED_PROVIDES.put("goog/dojo/dom/query.js", List.of("goog.dom.query"));

        EXPECTED_REQUIRES.put("goog/caja/string/html/htmlsanitizer.js", List.of(
                "goog.string.StringBuffer",
                "goog.string.html.HtmlParser",
                "goog.string.html.HtmlParser.EFlags",
                "goog.string.html.HtmlParser.Elements",
                "goog.string.html.HtmlSaxHandler"));
        EXPECTED_REQUIRES.put("goog/caja/string/html/htmlparser.js", List.of());
        EXPECTED_REQUIRES.put("goog/osapi/osapi.js", List.of());
        EXPECTED_REQUIRES.put("goog/mochikit/async/deferredlist.js", List.of("goog.async.Deferred"));
        EXPECTED_REQUIRES.put("goog/mochikit/async/deferred.js", List.of(
                "goog.Promise",
                "goog.Thenable",
                "goog.array",
                "goog.asserts",
                "goog.debug.Error"));
        EXPECTED_REQUIRES.put("goog/loremipsum/text/loremipsum.js", List.of(
                "goog.array",
                "goog.math",
                "goog.string",
                "goog.structs.Map",
                "goog.structs.Set"));
        EXPECTED_REQUIRES.put("goog/jpeg_encoder/jpeg_encoder_basic.js", List.of("goog.crypt.base64"));
        EXPECTED_REQUIRES.put("goog/svgpan/svgpan.js", List.of(
                "goog.Disposable",
                "goog.events",
                "goog.events.EventType",
                "goog.events.MouseWheelHandler"));
        EXPECTED_REQUIRES.put("goog/dojo/dom/query.js", List.of(
                "goog.array",
                "goog.dom",
                "goog.functions",
                "goog.string",
                "goog.userAgent"));
    }

    @Test
    void publishedResourcesAreReadableFromTheClasspath() throws IOException {
        for (String path : PUBLISHED_RESOURCES) {
            URL resource = resourceUrl(path);
            assertThat(resource).as(path).isNotNull();
            assertThat(loadResource(path)).as(path).isNotBlank();
        }
    }

    @Test
    void javascriptModulesDeclareTheExpectedClosureNamespaces() throws IOException {
        for (Map.Entry<String, List<String>> entry : EXPECTED_PROVIDES.entrySet()) {
            assertThat(extractMatches(PROVIDE_PATTERN, loadResource(entry.getKey())))
                    .as(entry.getKey())
                    .containsExactlyElementsOf(entry.getValue());
        }
    }

    @Test
    void javascriptModulesDeclareTheExpectedClosureDependencies() throws IOException {
        assertThat(EXPECTED_REQUIRES.keySet()).containsExactlyElementsOf(EXPECTED_PROVIDES.keySet());
        for (Map.Entry<String, List<String>> entry : EXPECTED_REQUIRES.entrySet()) {
            assertThat(extractMatches(REQUIRE_PATTERN, loadResource(entry.getKey())))
                    .as(entry.getKey())
                    .containsExactlyElementsOf(entry.getValue());
        }
    }

    @Test
    void htmlFixturesReferenceTheModulesTheyExercise() throws IOException {
        String deferredTest = loadResource("goog/mochikit/async/deferred_test.html");
        assertThat(deferredTest)
                .contains("Closure Unit Tests - goog.async.Deferred")
                .contains("goog.require('goog.async.Deferred')")
                .contains("goog.require('goog.testing.MockClock')")
                .contains("goog.require('goog.testing.jsunit')")
                .contains("function testNormal()")
                .contains("function testThen()")
                .contains("function testThen_reject()")
                .contains("function testCancel()")
                .contains("function testBranch()")
                .contains("function testCancelThroughBranch()")
                .contains("function testUndefinedResultAndErrbackSequence()")
                .contains("function testDoubleCalling()");

        String deferredAsyncTest = loadResource("goog/mochikit/async/deferred_async_test.html");
        assertThat(deferredAsyncTest)
                .contains("goog.require('goog.async.Deferred')")
                .contains("goog.require('goog.testing.AsyncTestCase')")
                .contains("function testErrorStack()")
                .contains("function testErrorStack_forErrback()")
                .contains("function testErrorStack_nested()");

        String deferredListTest = loadResource("goog/mochikit/async/deferredlist_test.html");
        assertThat(deferredListTest)
                .contains("Closure Unit Tests - goog.async.DeferredList")
                .contains("goog.require('goog.async.DeferredList')")
                .contains("function testDeferredList()")
                .contains("function testGatherResults()")
                .contains("function testFireOnFirstCallback()")
                .contains("function testFireOnFirstErrback()");

        String loremIpsumTest = loadResource("goog/loremipsum/text/loremipsum_test.html");
        assertThat(loremIpsumTest)
                .contains("goog.require('goog.text.LoremIpsum')")
                .contains("goog.require('goog.testing.PseudoRandom')")
                .contains("function testLoremIpsum()");

        String queryTest = loadResource("goog/dojo/dom/query_test.html");
        assertThat(queryTest)
                .contains("goog.require('goog.dom.query')")
                .contains("<script src=\"query_test.js\"></script>")
                .contains("<div id=\"t\">")
                .contains("class=\"foo bar\"");
    }

    @Test
    void moduleSourcesContainRepresentativePublicApisAndBehaviors() throws IOException {
        assertThat(loadResource("goog/caja/string/html/htmlsanitizer.js"))
                .contains("goog.string.html.htmlSanitize = function")
                .contains("goog.string.html.HtmlSanitizer.AttributeType")
                .contains("goog.string.html.HtmlSanitizer.Attributes")
                .contains("goog.string.html.HtmlSanitizer.prototype.startTag")
                .contains("goog.string.html.HtmlSanitizer.prototype.endTag")
                .contains("goog.string.html.HtmlSanitizer.prototype.pcdata")
                .contains("goog.string.html.HtmlSanitizer.prototype.rcdata")
                .contains("goog.string.html.HtmlSanitizer.prototype.cdata");

        assertThat(loadResource("goog/caja/string/html/htmlparser.js"))
                .contains("goog.string.html.HtmlParser.prototype.parse")
                .contains("goog.string.html.HtmlSaxHandler.prototype.startTag")
                .contains("goog.string.html.HtmlSaxHandler.prototype.endTag")
                .contains("goog.string.html.HtmlParser.Entities")
                .contains("goog.string.html.HtmlParser.Elements");

        assertThat(loadResource("goog/mochikit/async/deferred.js"))
                .contains("goog.async.Deferred.prototype.callback")
                .contains("goog.async.Deferred.prototype.errback")
                .contains("goog.async.Deferred.prototype.addCallback")
                .contains("goog.async.Deferred.prototype.addErrback")
                .contains("goog.async.Deferred.prototype.addBoth")
                .contains("goog.async.Deferred.prototype.cancel")
                .contains("goog.async.Deferred.prototype.then")
                .contains("goog.async.Deferred.AlreadyCalledError")
                .contains("goog.async.Deferred.CanceledError");

        assertThat(loadResource("goog/mochikit/async/deferredlist.js"))
                .contains("goog.async.DeferredList = function")
                .contains("goog.async.DeferredList.prototype.errback")
                .contains("goog.async.DeferredList.gatherResults");

        assertThat(loadResource("goog/dojo/dom/query.js"))
                .contains("goog.dom.query = (function()")
                .contains("'nth-child': function")
                .contains("'contains': function")
                .contains("'not': function")
                .contains("getElementsByClassName")
                .contains("getElementsByTagName");

        assertThat(loadResource("goog/loremipsum/text/loremipsum.js"))
                .contains("goog.text.LoremIpsum = function")
                .contains("goog.text.LoremIpsum.prototype.generateSentence")
                .contains("goog.text.LoremIpsum.prototype.generateParagraph")
                .contains("goog.text.LoremIpsum.chooseClosest");

        assertThat(loadResource("goog/jpeg_encoder/jpeg_encoder_basic.js"))
                .contains("goog.crypt.JpegEncoder = function")
                .contains("this.encode = function(image,opt_quality)");

        assertThat(loadResource("goog/svgpan/svgpan.js"))
                .contains("svgpan.SvgPan = function")
                .contains("svgpan.SvgPan.prototype.setPanEnabled")
                .contains("svgpan.SvgPan.prototype.setZoomEnabled")
                .contains("svgpan.SvgPan.prototype.setDragEnabled")
                .contains("svgpan.SvgPan.prototype.setZoomScale")
                .contains("svgpan.SvgPan.prototype.handleMove")
                .contains("svgpan.SvgPan.prototype.endPanOrDrag")
                .contains("svgpan.SvgPan.prototype.getState");

        assertThat(loadResource("goog/osapi/osapi.js"))
                .contains("goog.osapi.handleGadgetRpcMethod = function(requests)")
                .contains("goog.osapi.init = function()")
                .contains("goog.exportSymbol('osapi', osapi)")
                .contains("gadgets.rpc.register('osapi._handleGadgetRpcMethod'");
    }

    @Test
    void htmlParserDefinesEntityDecodingAndElementContentRules() throws IOException {
        String source = loadResource("goog/caja/string/html/htmlparser.js");
        Map<String, String> entities = extractStringMapValues(source, "goog.string.html.HtmlParser.Entities");
        Map<String, Integer> eFlags = extractEnumValues(source, "goog.string.html.HtmlParser.EFlags");
        Map<String, Integer> elements = extractElementFlagValues(source, eFlags);

        assertThat(entities)
                .containsEntry("lt", "<")
                .containsEntry("gt", ">")
                .containsEntry("amp", "&")
                .containsEntry("nbsp", "\u00a0")
                .containsEntry("quot", "\"")
                .containsEntry("apos", "'");

        assertThat(eFlags).containsKeys("OPTIONAL_ENDTAG", "EMPTY", "CDATA", "RCDATA", "UNSAFE", "FOLDABLE");
        assertThat(elements.get("br")).isEqualTo(combinedFlags(eFlags, "EMPTY"));
        assertThat(elements.get("img")).isEqualTo(combinedFlags(eFlags, "EMPTY"));
        assertThat(elements.get("param")).isEqualTo(combinedFlags(eFlags, "EMPTY", "UNSAFE"));
        assertThat(elements.get("p")).isEqualTo(combinedFlags(eFlags, "OPTIONAL_ENDTAG"));
        assertThat(elements.get("li")).isEqualTo(combinedFlags(eFlags, "OPTIONAL_ENDTAG"));
        assertThat(elements.get("script")).isEqualTo(combinedFlags(eFlags, "UNSAFE", "CDATA"));
        assertThat(elements.get("style")).isEqualTo(combinedFlags(eFlags, "UNSAFE", "CDATA"));
        assertThat(elements.get("textarea")).isEqualTo(combinedFlags(eFlags, "RCDATA"));
        assertThat(elements.get("title")).isEqualTo(combinedFlags(eFlags, "RCDATA", "UNSAFE"));
        assertThat(elements.get("html")).isEqualTo(combinedFlags(eFlags, "OPTIONAL_ENDTAG", "UNSAFE", "FOLDABLE"));
        assertThat(elements.get("span")).isZero();
    }

    @Test
    void htmlSanitizerAttributePoliciesSeparateSafeTokensUrisAndExecutableContent() throws IOException {
        String source = loadResource("goog/caja/string/html/htmlsanitizer.js");
        Map<String, Integer> attributeTypes = extractEnumValues(
                source, "goog.string.html.HtmlSanitizer.AttributeType");
        Map<String, Integer> attributes = extractAttributePolicyValues(source, attributeTypes);

        assertThat(attributeTypes).containsKeys("URI", "GLOBAL_NAME", "LOCAL_NAME", "CLASSES", "ID", "STYLE", "SCRIPT");
        assertThat(attributePolicy(attributes, "a", "href")).isEqualTo(attributeTypes.get("URI"));
        assertThat(attributePolicy(attributes, "blockquote", "cite")).isEqualTo(attributeTypes.get("URI"));
        assertThat(attributePolicy(attributes, "img", "src")).isEqualTo(attributeTypes.get("URI"));
        assertThat(attributePolicy(attributes, "a", "name")).isEqualTo(attributeTypes.get("GLOBAL_NAME"));
        assertThat(attributePolicy(attributes, "button", "name")).isEqualTo(attributeTypes.get("LOCAL_NAME"));
        assertThat(attributePolicy(attributes, "span", "class")).isEqualTo(attributeTypes.get("CLASSES"));
        assertThat(attributePolicy(attributes, "span", "id")).isEqualTo(attributeTypes.get("ID"));
        assertThat(attributePolicy(attributes, "span", "style")).isEqualTo(attributeTypes.get("STYLE"));
        assertThat(attributePolicy(attributes, "span", "onclick")).isEqualTo(attributeTypes.get("SCRIPT"));
        assertThat(attributePolicy(attributes, "input", "checked")).isZero();
        assertThat(attributePolicy(attributes, "td", "rowspan")).isZero();
        assertThat(attributePolicy(attributes, "span", "data-custom")).isNull();
    }

    @Test
    void mavenMetadataIdentifiesThePublishedArtifact() throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = openResource(POM_PROPERTIES)) {
            properties.load(inputStream);
        }

        assertThat(properties)
                .containsEntry("groupId", "org.clojure")
                .containsEntry("artifactId", "google-closure-library-third-party")
                .containsEntry("version", VERSION);

        assertThat(loadResource(POM_XML))
                .contains("<groupId>org.clojure</groupId>")
                .contains("<artifactId>google-closure-library-third-party</artifactId>")
                .contains("<version>" + VERSION + "</version>");
    }

    private static Map<String, Integer> extractEnumValues(String source, String objectName) {
        Matcher matcher = Pattern.compile("\\s*([A-Z_]+): (\\d+),?")
                .matcher(extractObjectLiteralBody(source, objectName));
        Map<String, Integer> values = new LinkedHashMap<>();
        while (matcher.find()) {
            values.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }
        assertThat(values).as(objectName).isNotEmpty();
        return values;
    }

    private static Map<String, Integer> extractAttributePolicyValues(
            String source, Map<String, Integer> attributeTypes) {
        Matcher matcher = Pattern.compile(
                "\\s*'([^']+)': (?:goog\\.string\\.html\\.HtmlSanitizer\\.AttributeType\\.([A-Z_]+)|(\\d+)),?")
                .matcher(extractObjectLiteralBody(source, "goog.string.html.HtmlSanitizer.Attributes"));
        Map<String, Integer> values = new LinkedHashMap<>();
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2) == null
                    ? Integer.parseInt(matcher.group(3))
                    : attributeTypes.get(matcher.group(2)));
        }
        assertThat(values).as("goog.string.html.HtmlSanitizer.Attributes").isNotEmpty();
        return values;
    }

    private static Map<String, String> extractStringMapValues(String source, String objectName) {
        Matcher matcher = Pattern.compile("\\s*'([^']+)': '((?:\\\\'|[^'])*)',?")
                .matcher(extractObjectLiteralBody(source, objectName));
        Map<String, String> values = new LinkedHashMap<>();
        while (matcher.find()) {
            values.put(matcher.group(1), decodeJavaScriptString(matcher.group(2)));
        }
        assertThat(values).as(objectName).isNotEmpty();
        return values;
    }

    private static String decodeJavaScriptString(String value) {
        return value.replace("\\u00a0", "\u00a0")
                .replace("\\'", "'")
                .replace("\\\\", "\\");
    }

    private static Map<String, Integer> extractElementFlagValues(String source, Map<String, Integer> eFlags) {
        String normalizedBody = extractObjectLiteralBody(source, "goog.string.html.HtmlParser.Elements")
                .replaceAll("\\R\\s+", " ");
        Matcher matcher = Pattern.compile("'([^']+)':\\s*([^,]+),?").matcher(normalizedBody);
        Map<String, Integer> values = new LinkedHashMap<>();
        while (matcher.find()) {
            values.put(matcher.group(1), evaluateFlagExpression(matcher.group(2), eFlags));
        }
        assertThat(values).as("goog.string.html.HtmlParser.Elements").isNotEmpty();
        return values;
    }

    private static int evaluateFlagExpression(String expression, Map<String, Integer> eFlags) {
        if (expression.trim().equals("0")) {
            return 0;
        }
        Matcher matcher = Pattern.compile("EFlags\\.([A-Z_]+)").matcher(expression);
        int value = 0;
        while (matcher.find()) {
            String flagName = matcher.group(1);
            assertThat(eFlags).containsKey(flagName);
            value |= eFlags.get(flagName);
        }
        assertThat(value).as(expression).isNotZero();
        return value;
    }

    private static int combinedFlags(Map<String, Integer> eFlags, String... flagNames) {
        int value = 0;
        for (String flagName : flagNames) {
            assertThat(eFlags).containsKey(flagName);
            value |= eFlags.get(flagName);
        }
        return value;
    }

    private static String extractObjectLiteralBody(String source, String objectName) {
        Matcher matcher = Pattern.compile(Pattern.quote(objectName) + " = \\{\\R(?<body>.*?)\\R\\};", Pattern.DOTALL)
                .matcher(source);
        assertThat(matcher.find()).as(objectName).isTrue();
        return matcher.group("body");
    }

    private static Integer attributePolicy(Map<String, Integer> attributes, String tagName, String attributeName) {
        Integer specificPolicy = attributes.get(tagName + "::" + attributeName);
        if (specificPolicy != null) {
            return specificPolicy;
        }
        return attributes.get("*::" + attributeName);
    }

    private static List<String> extractMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return matches;
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream inputStream = openResource(path)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream openResource(String path) {
        InputStream inputStream = Google_closure_library_third_partyTest.class
                .getClassLoader()
                .getResourceAsStream(path);
        if (inputStream != null) {
            return inputStream;
        }
        byte[] bytes = loadResourceBytesFromGradleArtifactCache(path);
        assertThat(bytes).as(path).isNotNull();
        return new ByteArrayInputStream(bytes);
    }

    private static URL resourceUrl(String path) {
        URL resource = Google_closure_library_third_partyTest.class.getClassLoader().getResource(path);
        if (resource != null) {
            return resource;
        }
        Path jar = findArtifactJarContaining(path);
        if (jar == null) {
            return null;
        }
        try {
            return jar.toUri().toURL();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] loadResourceBytesFromGradleArtifactCache(String path) {
        Path jar = findArtifactJarContaining(path);
        if (jar == null) {
            return null;
        }
        return loadResourceBytesFromJar(jar, path);
    }

    private static Path findArtifactJarContaining(String path) {
        if (!Files.isDirectory(GRADLE_ARTIFACT_CACHE)) {
            return null;
        }
        try (Stream<Path> paths = Files.walk(GRADLE_ARTIFACT_CACHE)) {
            return paths.filter(Files::isRegularFile)
                    .filter(Google_closure_library_third_partyTest::isLibraryJar)
                    .filter(jar -> jarContains(jar, path))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isLibraryJar(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("google-closure-library-third-party-") && fileName.endsWith(".jar");
    }

    private static boolean jarContains(Path jar, String path) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            return jarFile.getJarEntry(path) != null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] loadResourceBytesFromJar(Path jar, String path) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry jarEntry = jarFile.getJarEntry(path);
            assertThat(jarEntry).as(path).isNotNull();
            try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                return inputStream.readAllBytes();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
