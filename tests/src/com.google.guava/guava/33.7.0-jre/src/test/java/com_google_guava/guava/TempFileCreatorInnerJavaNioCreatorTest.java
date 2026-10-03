/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

public class TempFileCreatorInnerJavaNioCreatorTest {
    @Test
    void userPermissionCreationLooksUpCurrentProcessUser() throws Exception {
        Class<?> tempFileCreator = tempFileCreatorClass();
        MethodHandle testHook = MethodHandles.privateLookupIn(tempFileCreator, MethodHandles.lookup())
                .findStatic(
                        tempFileCreator,
                        "testMakingUserPermissionsFromScratch",
                        MethodType.methodType(void.class));

        assertThatCode(() -> invokeTestHook(testHook)).doesNotThrowAnyException();
    }

    private static void invokeTestHook(MethodHandle testHook) throws Exception {
        try {
            testHook.invoke();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new AssertionError(exception);
        }
    }

    private static Class<?> tempFileCreatorClass() throws ClassNotFoundException {
        return Class.forName("com.google.common.io.TempFileCreator");
    }
}
