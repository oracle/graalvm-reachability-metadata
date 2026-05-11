/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.pulsar.client.internal;

/**
 * Test bridge for the package-private Pulsar reflection helper.
 */
public final class ReflectionUtilsAccess {
    private ReflectionUtilsAccess() {
    }

    public static String loadClassName(String className) {
        return ReflectionUtils.newClassInstance(className).getName();
    }

    public static String constructorDeclaringClassName(String className, Class<?>... argTypes) {
        return ReflectionUtils.getConstructor(className, argTypes).getDeclaringClass().getName();
    }

    public static String staticMethodReturnTypeName(String className, String method, Class<?>... argTypes) {
        return ReflectionUtils.getStaticMethod(className, method, argTypes).getReturnType().getName();
    }
}
