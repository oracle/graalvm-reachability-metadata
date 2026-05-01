/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_protobuf_3_7;

import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.thirdparty.protobuf.CodedOutputStream;
import org.apache.hadoop.thirdparty.protobuf.GeneratedMessageLite;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteTest {
    private static final String LABEL = "visible";
    private static final List<String> TAGS = List.of("red", "blue");
    private static final Map<String, Integer> COUNTS = orderedCounts();

    @Test
    void generatedMethodHelpersResolveAndInvokeGeneratedAccessors() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                GeneratedMessageLite.class,
                MethodHandles.lookup()
        );
        MethodHandle getMethodOrDie = lookup.findStatic(
                GeneratedMessageLite.class,
                "getMethodOrDie",
                methodType(Method.class, Class.class, String.class, Class[].class)
        ).asFixedArity();
        MethodHandle invokeOrDie = lookup.findStatic(
                GeneratedMessageLite.class,
                "invokeOrDie",
                methodType(Object.class, Method.class, Object.class, Object[].class)
        ).asFixedArity();

        Method accessor = (Method) getMethodOrDie.invokeWithArguments(
                PrintableLiteMessage.class,
                "getLabel",
                new Class<?>[0]
        );
        Object value = invokeOrDie.invokeWithArguments(
                accessor,
                new PrintableLiteMessage(),
                new Object[0]
        );

        assertThat(value).isEqualTo(LABEL);
    }

    @Test
    void toStringReflectivelyPrintsGeneratedLiteAccessors() {
        String printed = new PrintableLiteMessage().toString();

        assertThat(printed)
                .contains("label: \"visible\"")
                .contains("tags: \"red\"")
                .contains("tags: \"blue\"")
                .contains("key: \"errors\"")
                .contains("value: 7")
                .contains("key: \"warnings\"")
                .contains("value: 3");
    }

    private static Map<String, Integer> orderedCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("errors", 7);
        counts.put("warnings", 3);
        return Collections.unmodifiableMap(counts);
    }

    public static final class PrintableLiteMessage
            extends GeneratedMessageLite<PrintableLiteMessage, PrintableLiteMessage.Builder> {
        private static final PrintableLiteMessage DEFAULT_INSTANCE = new PrintableLiteMessage();

        public String getLabel() {
            return LABEL;
        }

        public boolean hasLabel() {
            return true;
        }

        @SuppressWarnings("unused")
        public void setLabel(String label) {
            throw new UnsupportedOperationException(
                    "Setter exists only to mirror generated message accessors"
            );
        }

        public List<String> getTagsList() {
            return TAGS;
        }

        public Map<String, Integer> getCountsMap() {
            return COUNTS;
        }

        @Override
        public void writeTo(CodedOutputStream output) throws IOException {
            output.writeString(1, getLabel());
        }

        @Override
        public int getSerializedSize() {
            return CodedOutputStream.computeStringSize(1, getLabel());
        }

        @Override
        protected Object dynamicMethod(MethodToInvoke method, Object firstArgument, Object secondArgument) {
            switch (method) {
                case GET_DEFAULT_INSTANCE:
                    return DEFAULT_INSTANCE;
                case GET_MEMOIZED_IS_INITIALIZED:
                    return (byte) 1;
                case NEW_MUTABLE_INSTANCE:
                    return new PrintableLiteMessage();
                case NEW_BUILDER:
                    return new Builder();
                case MAKE_IMMUTABLE:
                    return null;
                case IS_INITIALIZED:
                    return DEFAULT_INSTANCE;
                case VISIT:
                    return null;
                default:
                    throw new UnsupportedOperationException(
                            "Dynamic operation is not needed for this coverage test"
                    );
            }
        }

        public static final class Builder extends GeneratedMessageLite.Builder<PrintableLiteMessage, Builder> {
            private Builder() {
                super(DEFAULT_INSTANCE);
            }
        }
    }
}
