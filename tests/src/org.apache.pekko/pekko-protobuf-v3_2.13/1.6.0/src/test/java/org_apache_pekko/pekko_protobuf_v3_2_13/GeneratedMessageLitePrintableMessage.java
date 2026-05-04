/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite;
import org.apache.pekko.protobufv3.internal.Parser;

public final class GeneratedMessageLitePrintableMessage
        extends GeneratedMessageLite<GeneratedMessageLitePrintableMessage,
                GeneratedMessageLitePrintableMessage.Builder> {
    private static final String LABEL = "visible";
    private static final List<String> TAGS = List.of("red", "blue");
    private static final Map<String, Integer> COUNTS = orderedCounts();
    private static final GeneratedMessageLitePrintableMessage DEFAULT_INSTANCE =
            new GeneratedMessageLitePrintableMessage();
    private static volatile Parser<GeneratedMessageLitePrintableMessage> parser;

    static {
        registerDefaultInstance(GeneratedMessageLitePrintableMessage.class, DEFAULT_INSTANCE);
    }

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
    protected Object dynamicMethod(
            MethodToInvoke method,
            Object firstArgument,
            Object secondArgument) {
        switch (method) {
            case GET_MEMOIZED_IS_INITIALIZED:
                return (byte) 1;
            case SET_MEMOIZED_IS_INITIALIZED:
                return null;
            case BUILD_MESSAGE_INFO:
                return newMessageInfo(DEFAULT_INSTANCE, "\u0000\u0000", new Object[0]);
            case NEW_MUTABLE_INSTANCE:
                return new GeneratedMessageLitePrintableMessage();
            case NEW_BUILDER:
                return new Builder();
            case GET_DEFAULT_INSTANCE:
                return DEFAULT_INSTANCE;
            case GET_PARSER:
                Parser<GeneratedMessageLitePrintableMessage> localParser = parser;
                if (localParser == null) {
                    synchronized (GeneratedMessageLitePrintableMessage.class) {
                        localParser = parser;
                        if (localParser == null) {
                            localParser = new DefaultInstanceBasedParser<>(DEFAULT_INSTANCE);
                            parser = localParser;
                        }
                    }
                }
                return localParser;
            default:
                throw new UnsupportedOperationException(
                        "Dynamic operation is not needed for this coverage test"
                );
        }
    }

    private static Map<String, Integer> orderedCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("errors", 7);
        counts.put("warnings", 3);
        return Collections.unmodifiableMap(counts);
    }

    public static final class Builder
            extends GeneratedMessageLite.Builder<GeneratedMessageLitePrintableMessage, Builder> {
        private Builder() {
            super(DEFAULT_INSTANCE);
        }
    }
}
