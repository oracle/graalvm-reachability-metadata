/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class PatchFixesHiderInnerUtilTest {

    private static final String PATCH_FIXES_HIDER_UTIL = "lombok.launch.PatchFixesHider$Util";
    private static final String PATCH_FIXES_SHADOW_LOADED =
            "lombok.eclipse.agent.PatchFixesShadowLoaded";

    @Test
    void utilityMethodsResolveAndInvokeShadowLoadedPatchFix() throws Exception {
        try {
            final Class<?> utilType = Class.forName(PATCH_FIXES_HIDER_UTIL);
            final Method shadowLoadClass = utilType.getMethod("shadowLoadClass", String.class);
            final Method findMethod = utilType.getMethod(
                    "findMethod", Class.class, String.class, Class[].class);
            final Method findMethodByTypeNames = utilType.getMethod(
                    "findMethod", Class.class, String.class, String[].class);
            final Method findMethodAnyArgs = utilType.getMethod(
                    "findMethodAnyArgs", Class.class, String.class);
            final Method getShadowLoader = utilType.getMethod("getShadowLoader");
            final Method invokeMethod = utilType.getMethod(
                    "invokeMethod", Method.class, Object[].class);

            final ClassLoader shadowLoader = (ClassLoader) getShadowLoader.invoke(null);
            shadowLoader.loadClass(PATCH_FIXES_SHADOW_LOADED);
            final Class<?> shadowLoadedType = (Class<?>) shadowLoadClass.invoke(
                    null, PATCH_FIXES_SHADOW_LOADED);
            final Method addLombokNotes = (Method) findMethod.invoke(
                    null,
                    shadowLoadedType,
                    "addLombokNotesToEclipseAboutDialog",
                    new Class<?>[] {String.class, String.class});
            final Method bytePostCompiler = (Method) findMethodByTypeNames.invoke(
                    null,
                    shadowLoadedType,
                    "runPostCompiler",
                    new String[] {byte[].class.getName(), String.class.getName()});
            final Method outputPostCompiler = (Method) findMethodAnyArgs.invoke(
                    null, shadowLoadedType, "runPostCompiler");

            final Object aboutText = invokeMethod.invoke(
                    null, addLombokNotes, new Object[] {"Eclipse IDE", "aboutText"});

            assertThat(shadowLoadedType.getName()).isEqualTo(PATCH_FIXES_SHADOW_LOADED);
            assertThat(bytePostCompiler.getParameterTypes())
                    .containsExactly(byte[].class, String.class);
            assertThat(outputPostCompiler.getName()).isEqualTo("runPostCompiler");
            assertThat((String) aboutText)
                    .startsWith("Eclipse IDE")
                    .contains("Lombok ")
                    .contains(" is installed. https://projectlombok.org/");
        } catch (InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (!(cause instanceof Error error
                    && NativeImageSupport.isUnsupportedFeatureError(error))) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
