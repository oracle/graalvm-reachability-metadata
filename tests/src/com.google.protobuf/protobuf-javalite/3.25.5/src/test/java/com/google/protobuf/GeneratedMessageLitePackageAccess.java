/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf;

import java.lang.reflect.Method;

public final class GeneratedMessageLitePackageAccess {
    private GeneratedMessageLitePackageAccess() { }

    public static Object readValueWithGeneratedMessageLiteHelpers(StringValue message) {
        Method getValue = GeneratedMessageLite.getMethodOrDie(StringValue.class, "getValue");
        return GeneratedMessageLite.invokeOrDie(getValue, message);
    }
}
