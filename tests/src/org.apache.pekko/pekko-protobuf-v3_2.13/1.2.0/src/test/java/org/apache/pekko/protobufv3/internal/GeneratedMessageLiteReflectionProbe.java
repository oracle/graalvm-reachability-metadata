/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.pekko.protobufv3.internal;

import java.lang.reflect.Method;

public final class GeneratedMessageLiteReflectionProbe {
    private GeneratedMessageLiteReflectionProbe() {
    }

    public static String invokeGreeting(String name) {
        Method method = GeneratedMessageLite.getMethodOrDie(Target.class, "greeting", String.class);
        return (String) GeneratedMessageLite.invokeOrDie(method, new Target(), name);
    }

    public static final class Target {
        public String greeting(String name) {
            return "hello " + name;
        }
    }
}
