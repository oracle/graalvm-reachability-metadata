/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.kafka.shaded.com.google.protobuf;

import java.lang.reflect.Method;

public final class GeneratedMessageLiteCoverageAccess {
    private GeneratedMessageLiteCoverageAccess() {
    }

    public static Object invokeIsInitialized(GeneratedMessageLite<?, ?> message) {
        Method method = GeneratedMessageLite.getMethodOrDie(GeneratedMessageLite.class, "isInitialized");
        return GeneratedMessageLite.invokeOrDie(method, message);
    }
}
