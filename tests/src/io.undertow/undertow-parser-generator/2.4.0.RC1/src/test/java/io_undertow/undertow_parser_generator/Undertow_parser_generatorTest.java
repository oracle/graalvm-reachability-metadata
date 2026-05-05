/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_parser_generator;

import io.undertow.annotationprocessor.RequestParserGenerator;
import io.undertow.annotationprocessor.ResponseParserGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Undertow_parser_generatorTest {

    @Test
    void requestParserGeneratorCreatesTokenizerClassForConfiguredRequestTokens() throws IOException {
        byte[] classBytes = new RequestParserGenerator("com.example.RequestParser")
            .createTokenizer(
                new String[]{"GET", "POST"},
                new String[]{"HTTP/1.1", "HTTP/2"},
                new String[]{"Host", "Content-Type"}
            );

        ClassFileSnapshot snapshot = ClassFileSnapshot.parse(classBytes);

        assertThat(snapshot.className()).isEqualTo("com/example/RequestParser$$generated");
        assertThat(snapshot.superClassName()).isEqualTo("com/example/RequestParser");
        assertThat(snapshot.methodSignatures()).contains(
            new Member("<init>", "(Lorg/xnio/OptionMap;)V"),
            new Member("<clinit>", "()V"),
            new Member(
                "handleHttpVerb",
                "(Ljava/nio/ByteBuffer;Lio/undertow/server/protocol/http/ParseState;"
                    + "Lio/undertow/server/HttpServerExchange;)V"
            ),
            new Member(
                "handleHttpVersion",
                "(Ljava/nio/ByteBuffer;Lio/undertow/server/protocol/http/ParseState;"
                    + "Lio/undertow/server/HttpServerExchange;)V"
            ),
            new Member(
                "handleHeader",
                "(Ljava/nio/ByteBuffer;Lio/undertow/server/protocol/http/ParseState;"
                    + "Lio/undertow/server/HttpServerExchange;)V"
            )
        );
        assertThat(snapshot.utf8Constants()).contains(
            "GET",
            "POST",
            "HTTP/1.1",
            "HTTP/2",
            "Host",
            "Content-Type",
            "HTTP/"
        );
        assertThat(snapshot.fieldNames()).anyMatch(name -> name.startsWith("HTTP_STRING_"));
        assertThat(snapshot.fieldNames()).anyMatch(name -> name.startsWith("STATE_BYTES_"));
    }

    @Test
    void requestParserGeneratorAddsSharedPrefixStatesForAmbiguousTokens() throws IOException {
        ClassFileSnapshot distinctTokens = ClassFileSnapshot.parse(
            new RequestParserGenerator("com.example.RequestParserDistinct")
                .createTokenizer(
                    new String[]{"GET", "POST"},
                    new String[]{"HTTP/1.1", "HTTP/2"},
                    new String[]{"X-Trace-Id", "Y-Trace-Token"}
                )
        );
        ClassFileSnapshot sharedPrefixes = ClassFileSnapshot.parse(
            new RequestParserGenerator("com.example.RequestParserShared")
                .createTokenizer(
                    new String[]{"PATCH", "POST"},
                    new String[]{"HTTP/1.1", "HTTP/2"},
                    new String[]{"X-Trace-Id", "X-Trace-Token"}
                )
        );

        assertThat(distinctTokens.utf8Constants()).doesNotContain("P", "X-Trace-");
        assertThat(sharedPrefixes.utf8Constants()).contains(
            "P",
            "PATCH",
            "POST",
            "X-Trace-",
            "X-Trace-Id",
            "X-Trace-Token"
        );
        assertThat(sharedPrefixes.countFieldsWithPrefix("HTTP_STRING_"))
            .isLessThan(distinctTokens.countFieldsWithPrefix("HTTP_STRING_"));
        assertThat(sharedPrefixes.countFieldsWithPrefix("STATE_BYTES_"))
            .isLessThan(distinctTokens.countFieldsWithPrefix("STATE_BYTES_"));
    }

    @Test
    void responseParserGeneratorCreatesTokenizerClassForConfiguredResponseTokens() throws IOException {
        byte[] classBytes = new ResponseParserGenerator("com.example.ResponseParser")
            .createTokenizer(
                new String[0],
                new String[]{"HTTP/1.0", "HTTP/1.1"},
                new String[]{"Server", "X-Env-Id", "X-Env-Name"}
            );

        ClassFileSnapshot snapshot = ClassFileSnapshot.parse(classBytes);

        assertThat(snapshot.className()).isEqualTo("com/example/ResponseParser$$generated");
        assertThat(snapshot.superClassName()).isEqualTo("com/example/ResponseParser");
        assertThat(snapshot.methodSignatures()).contains(
            new Member("<init>", "()V"),
            new Member("<clinit>", "()V"),
            new Member(
                "handleHttpVersion",
                "(Ljava/nio/ByteBuffer;Lio/undertow/client/http/ResponseParseState;"
                    + "Lio/undertow/client/http/HttpResponseBuilder;)V"
            ),
            new Member(
                "handleHeader",
                "(Ljava/nio/ByteBuffer;Lio/undertow/client/http/ResponseParseState;"
                    + "Lio/undertow/client/http/HttpResponseBuilder;)V"
            )
        );
        assertThat(snapshot.methodNames()).doesNotContain("handleHttpVerb");
        assertThat(snapshot.utf8Constants()).contains(
            "HTTP/1.0",
            "HTTP/1.1",
            "HTTP/1.",
            "Server",
            "X-Env-",
            "X-Env-Id",
            "X-Env-Name"
        );
        assertThat(snapshot.fieldNames()).anyMatch(name -> name.startsWith("HTTP_STRING_"));
        assertThat(snapshot.fieldNames()).anyMatch(name -> name.startsWith("STATE_BYTES_"));
    }

    @Test
    void responseParserGeneratorIgnoresConfiguredRequestMethods() throws IOException {
        ResponseParserGenerator generator = new ResponseParserGenerator("com.example.ResponseParserIgnoredMethods");
        String[] protocols = {"HTTP/1.0", "HTTP/1.1"};
        String[] headers = {"Server", "X-Env-Id", "X-Env-Name"};

        ClassFileSnapshot withoutRequestMethods = ClassFileSnapshot.parse(
            generator.createTokenizer(new String[0], protocols, headers)
        );
        ClassFileSnapshot withRequestMethods = ClassFileSnapshot.parse(
            generator.createTokenizer(new String[]{"GET", "PATCH"}, protocols, headers)
        );

        assertThat(withRequestMethods).isEqualTo(withoutRequestMethods);
        assertThat(withRequestMethods.methodNames()).doesNotContain("handleHttpVerb");
        assertThat(withRequestMethods.utf8Constants()).doesNotContain("GET", "PATCH", "P");
    }

    @Test
    void requestParserGeneratorAddsValidationAndCompletionHooksToGeneratedParser() throws IOException {
        ClassFileSnapshot snapshot = ClassFileSnapshot.parse(
            new RequestParserGenerator("com.example.RequestParserHooks")
                .createTokenizer(
                    new String[]{"GET", "POST"},
                    new String[]{"HTTP/1.1", "HTTP/2"},
                    new String[]{"Host", "Content-Type"}
                )
        );

        assertThat(snapshot.utf8Constants()).contains(
            "io/undertow/server/Connectors",
            "verifyToken",
            "setRequestMethod",
            "(Lio/undertow/util/HttpString;)Lio/undertow/server/HttpServerExchange;",
            "setProtocol",
            "nextHeader",
            "leftOver",
            "parseComplete"
        );
    }

    @Test
    void responseParserGeneratorKeepsResponseHooksWithoutRequestValidation() throws IOException {
        ClassFileSnapshot snapshot = ClassFileSnapshot.parse(
            new ResponseParserGenerator("com.example.ResponseParserHooks")
                .createTokenizer(
                    new String[]{"GET", "POST"},
                    new String[]{"HTTP/1.0", "HTTP/1.1"},
                    new String[]{"Server", "X-Env-Id", "X-Env-Name"}
                )
        );

        assertThat(snapshot.utf8Constants()).contains(
            "io/undertow/client/http/HttpResponseBuilder",
            "setProtocol",
            "(Lio/undertow/util/HttpString;)V",
            "nextHeader",
            "leftOver",
            "parseComplete"
        );
        assertThat(snapshot.utf8Constants()).doesNotContain(
            "io/undertow/server/Connectors",
            "verifyToken",
            "setRequestMethod"
        );
    }

    private record Member(String name, String descriptor) {
    }

    private record ClassFileSnapshot(
        String className,
        String superClassName,
        List<Member> fieldSignatures,
        List<Member> methodSignatures,
        Set<String> utf8Constants
    ) {

        private static final int CONSTANT_UTF8 = 1;
        private static final int CONSTANT_INTEGER = 3;
        private static final int CONSTANT_FLOAT = 4;
        private static final int CONSTANT_LONG = 5;
        private static final int CONSTANT_DOUBLE = 6;
        private static final int CONSTANT_CLASS = 7;
        private static final int CONSTANT_STRING = 8;
        private static final int CONSTANT_FIELD_REF = 9;
        private static final int CONSTANT_METHOD_REF = 10;
        private static final int CONSTANT_INTERFACE_METHOD_REF = 11;
        private static final int CONSTANT_NAME_AND_TYPE = 12;
        private static final int CONSTANT_METHOD_HANDLE = 15;
        private static final int CONSTANT_METHOD_TYPE = 16;
        private static final int CONSTANT_DYNAMIC = 17;
        private static final int CONSTANT_INVOKE_DYNAMIC = 18;
        private static final int CONSTANT_MODULE = 19;
        private static final int CONSTANT_PACKAGE = 20;

        static ClassFileSnapshot parse(byte[] classBytes) throws IOException {
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(classBytes))) {
                int magic = input.readInt();
                assertThat(magic).isEqualTo(0xCAFEBABE);

                input.readUnsignedShort();
                input.readUnsignedShort();

                Object[] constantPool = new Object[input.readUnsignedShort()];
                Set<String> utf8Constants = new LinkedHashSet<>();
                int index = 1;
                while (index < constantPool.length) {
                    int tag = input.readUnsignedByte();
                    switch (tag) {
                        case CONSTANT_UTF8 -> {
                            String value = input.readUTF();
                            constantPool[index] = value;
                            utf8Constants.add(value);
                            index++;
                        }
                        case CONSTANT_INTEGER, CONSTANT_FLOAT -> {
                            input.skipNBytes(Integer.BYTES);
                            index++;
                        }
                        case CONSTANT_LONG, CONSTANT_DOUBLE -> {
                            input.skipNBytes(Long.BYTES);
                            index += 2;
                        }
                        case CONSTANT_CLASS, CONSTANT_STRING, CONSTANT_METHOD_TYPE,
                             CONSTANT_MODULE, CONSTANT_PACKAGE -> {
                            constantPool[index] = input.readUnsignedShort();
                            index++;
                        }
                        case CONSTANT_FIELD_REF, CONSTANT_METHOD_REF,
                             CONSTANT_INTERFACE_METHOD_REF, CONSTANT_NAME_AND_TYPE,
                             CONSTANT_DYNAMIC, CONSTANT_INVOKE_DYNAMIC -> {
                            input.skipNBytes(2L * Short.BYTES);
                            index++;
                        }
                        case CONSTANT_METHOD_HANDLE -> {
                            input.skipNBytes(1L + Short.BYTES);
                            index++;
                        }
                        default -> throw new IOException("Unsupported constant pool tag: " + tag);
                    }
                }

                input.readUnsignedShort();
                String className = resolveClassName(constantPool, input.readUnsignedShort());
                String superClassName = resolveClassName(constantPool, input.readUnsignedShort());

                skipInterfaces(input);
                List<Member> fields = readMembers(input, constantPool);
                List<Member> methods = readMembers(input, constantPool);
                skipAttributes(input);

                return new ClassFileSnapshot(className, superClassName, fields, methods, utf8Constants);
            }
        }

        List<String> fieldNames() {
            return fieldSignatures.stream().map(Member::name).toList();
        }

        List<String> methodNames() {
            return methodSignatures.stream().map(Member::name).toList();
        }

        int countFieldsWithPrefix(String prefix) {
            return (int) fieldSignatures.stream()
                .map(Member::name)
                .filter(name -> name.startsWith(prefix))
                .count();
        }

        private static void skipInterfaces(DataInputStream input) throws IOException {
            int interfaceCount = input.readUnsignedShort();
            input.skipNBytes((long) interfaceCount * Short.BYTES);
        }

        private static List<Member> readMembers(DataInputStream input, Object[] constantPool) throws IOException {
            int memberCount = input.readUnsignedShort();
            List<Member> members = new ArrayList<>(memberCount);
            for (int index = 0; index < memberCount; index++) {
                input.readUnsignedShort();
                String name = resolveUtf8(constantPool, input.readUnsignedShort());
                String descriptor = resolveUtf8(constantPool, input.readUnsignedShort());
                members.add(new Member(name, descriptor));
                skipAttributes(input);
            }
            return members;
        }

        private static void skipAttributes(DataInputStream input) throws IOException {
            int attributeCount = input.readUnsignedShort();
            for (int index = 0; index < attributeCount; index++) {
                input.readUnsignedShort();
                input.skipNBytes(input.readInt());
            }
        }

        private static String resolveClassName(Object[] constantPool, int classIndex) {
            return classIndex == 0 ? null : resolveUtf8(constantPool, (Integer) constantPool[classIndex]);
        }

        private static String resolveUtf8(Object[] constantPool, int index) {
            return (String) constantPool[index];
        }
    }
}
