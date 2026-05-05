/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public class NativeLoaderStateResetFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        registerFields(access, "org.bouncycastle.crypto.fips.FipsStatus",
                "readyStatus", "loader", "statusException");
        registerFields(access, "org.bouncycastle.crypto.fips.NativeLoader",
                "nativeLibsAvailableForSystem", "nativeInstalled", "nativeEnabled",
                "nativeStatusMessage", "selectedVariant");
        registerFields(access, "org.bouncycastle.crypto.fips.FipsNativeServices", "nativeFeatures");
        registerMethod(access, "org.bouncycastle.jcajce.provider.ClassUtil",
                "throwBadTagException", String.class);
    }

    private static void registerFields(BeforeAnalysisAccess access, String className, String... fieldNames) {
        Class<?> clazz = access.findClassByName(className);
        if (clazz == null) {
            throw new IllegalStateException(className + " is unavailable");
        }
        RuntimeReflection.register(clazz);
        RuntimeReflection.registerClassLookup(className);
        for (String fieldName : fieldNames) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                RuntimeReflection.register(field);
            } catch (NoSuchFieldException exception) {
                throw new IllegalStateException(className + "#" + fieldName + " is unavailable", exception);
            }
        }
    }

    private static void registerMethod(
            BeforeAnalysisAccess access,
            String className,
            String methodName,
            Class<?>... parameterTypes) {
        Class<?> clazz = access.findClassByName(className);
        if (clazz == null) {
            throw new IllegalStateException(className + " is unavailable");
        }
        RuntimeReflection.register(clazz);
        RuntimeReflection.registerClassLookup(className);
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            RuntimeReflection.register(method);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(className + "#" + methodName + " is unavailable", exception);
        }
    }
}
