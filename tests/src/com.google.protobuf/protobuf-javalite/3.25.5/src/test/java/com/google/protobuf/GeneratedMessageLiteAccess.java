/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf;

import java.lang.reflect.Method;

public final class GeneratedMessageLiteAccess {
    private GeneratedMessageLiteAccess() {
    }

    public static Object invokeNoArgMethodOrDie(Class<?> clazz, String methodName, Object target) {
        Method method = GeneratedMessageLite.getMethodOrDie(clazz, methodName);
        return GeneratedMessageLite.invokeOrDie(method, target);
    }
}
