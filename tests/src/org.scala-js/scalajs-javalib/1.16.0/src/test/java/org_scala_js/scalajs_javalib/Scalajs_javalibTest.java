/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_js.scalajs_javalib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Scalajs_javalibTest {

    private static final byte[] SJSIR_HEADER_PREFIX = new byte[] {
            (byte) 0xca, (byte) 0xfe, 'J', 'S', 0, 4, '1', '.', '1', '6'
    };

    @Test
    void publicJavaLibraryIrResourcesHaveScalaJsHeadersAndDeclaredNames() throws IOException {
        List<IrResource> resources = List.of(
                new IrResource("java/lang/String.sjsir", "java.lang.String"),
                new IrResource("java/io/InputStream.sjsir", "java.io.InputStream"),
                new IrResource("java/math/BigInteger.sjsir", "java.math.BigInteger"),
                new IrResource("java/net/URI.sjsir", "java.net.URI"),
                new IrResource("java/nio/ByteBuffer.sjsir", "java.nio.ByteBuffer"),
                new IrResource("java/nio/charset/StandardCharsets.sjsir", "java.nio.charset.StandardCharsets"),
                new IrResource("java/util/HashMap.sjsir", "java.util.HashMap"),
                new IrResource("java/util/regex/Pattern.sjsir", "java.util.regex.Pattern"),
                new IrResource("java/util/concurrent/atomic/AtomicInteger.sjsir",
                        "java.util.concurrent.atomic.AtomicInteger"),
                new IrResource("org/scalajs/javalibintf/TypedArrayBuffer.sjsir",
                        "org.scalajs.javalibintf.TypedArrayBuffer"));

        for (IrResource resource : resources) {
            byte[] bytes = readResource(resource.path());

            assertThat(bytes)
                    .as(resource.path())
                    .hasSizeGreaterThan(100)
                    .startsWith(SJSIR_HEADER_PREFIX);
            assertThat(asResourceText(bytes))
                    .as(resource.path())
                    .contains(resource.declaredName());
        }
    }

    @Test
    void javaLangResourcesContainCoreObjectAndStaticCompanionMembers() throws IOException {
        assertThat(resourceText("java/lang/String.sjsir"))
                .contains("java.lang.String")
                .contains("java.lang.Object")
                .contains("java.io.Serializable")
                .contains("java.lang.Comparable")
                .contains("java.lang.CharSequence")
                .contains("substring")
                .contains("compareTo")
                .contains("valueOf");

        assertThat(resourceText("java/lang/Integer$.sjsir"))
                .contains("java.lang.Integer$")
                .contains("parseInt")
                .contains("toString")
                .contains("valueOf");

        assertThat(resourceText("java/lang/Throwable.sjsir"))
                .contains("java.lang.Throwable")
                .contains("getMessage")
                .contains("getCause")
                .contains("printStackTrace");
    }

    @Test
    void javaCollectionsAndOptionalResourcesExposeExpectedPublicOperations() throws IOException {
        assertThat(resourceText("java/util/HashMap.sjsir"))
                .contains("java.util.HashMap")
                .contains("java.util.AbstractMap")
                .contains("java.util.Map")
                .contains("put")
                .contains("get")
                .contains("containsKey")
                .contains("entrySet");

        assertThat(resourceText("java/util/Collections.sjsir"))
                .contains("java.util.Collections")
                .contains("emptyList")
                .contains("singletonMap")
                .contains("unmodifiableList")
                .contains("synchronizedMap");

        assertThat(resourceText("java/util/Optional.sjsir"))
                .contains("java.util.Optional")
                .contains("empty")
                .contains("ofNullable")
                .contains("orElse")
                .contains("ifPresent");
    }

    @Test
    void mathNetworkingRegexAndBufferResourcesExposeRichApiSurface() throws IOException {
        assertThat(resourceText("java/math/BigInteger.sjsir"))
                .contains("java.math.BigInteger")
                .contains("java.lang.Number")
                .contains("probablePrime")
                .contains("modPow")
                .contains("gcd")
                .contains("toByteArray");

        assertThat(resourceText("java/net/URI.sjsir"))
                .contains("java.net.URI")
                .contains("create")
                .contains("parseServerAuthority")
                .contains("resolve")
                .contains("relativize");

        assertThat(resourceText("java/util/regex/Pattern.sjsir"))
                .contains("java.util.regex.Pattern")
                .contains("compile")
                .contains("matcher")
                .contains("split")
                .contains("quote");

        assertThat(resourceText("java/nio/ByteBuffer.sjsir"))
                .contains("java.nio.ByteBuffer")
                .contains("java.nio.Buffer")
                .contains("allocate")
                .contains("wrap")
                .contains("slice")
                .contains("asIntBuffer");
    }

    @Test
    void concurrentAndFunctionalResourcesArePackagedWithNestedTypes() throws IOException {
        assertThat(resourceText("java/util/concurrent/ConcurrentHashMap.sjsir"))
                .contains("java.util.concurrent.ConcurrentHashMap")
                .contains("putIfAbsent")
                .contains("replace")
                .contains("keySet")
                .contains("elements");

        assertThat(resourceText("java/util/concurrent/Flow$Publisher.sjsir"))
                .contains("java.util.concurrent.Flow$Publisher")
                .contains("subscribe");

        assertThat(resourceText("java/util/function/Function.sjsir"))
                .contains("java.util.function.Function")
                .contains("apply")
                .contains("compose")
                .contains("andThen");

        assertThat(resourceText("java/util/function/Predicate.sjsir"))
                .contains("java.util.function.Predicate")
                .contains("test")
                .contains("and")
                .contains("negate")
                .contains("or");
    }

    @Test
    void scalaJsJavaLibInterfaceResourcesAreAvailable() throws IOException {
        assertThat(resourceText("org/scalajs/javalibintf/StackTraceElement.sjsir"))
                .contains("org.scalajs.javalibintf.StackTraceElement");

        assertThat(resourceText("org/scalajs/javalibintf/TypedArrayBuffer.sjsir"))
                .contains("org.scalajs.javalibintf.TypedArrayBuffer")
                .contains("wrap");
    }

    private static String resourceText(String path) throws IOException {
        return asResourceText(readResource(path));
    }

    private static String asResourceText(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static byte[] readResource(String path) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = Scalajs_javalibTest.class.getClassLoader();
        }
        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            assertThat(inputStream).as(path).isNotNull();
            byte[] bytes = inputStream.readAllBytes();
            assertThat(Arrays.copyOf(bytes, SJSIR_HEADER_PREFIX.length))
                    .as(path)
                    .containsExactly(SJSIR_HEADER_PREFIX);
            return bytes;
        }
    }

    private record IrResource(String path, String declaredName) {
    }
}
