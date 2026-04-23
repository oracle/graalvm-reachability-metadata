/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLog_1Test {

    @Test
    void simpleLogInitializesWithoutATestContextClassLoader() throws Throwable {
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();
        IllegalStateException failure = new IllegalStateException("boom");
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        PrintStream originalError = System.err;

        try (InputStream resource = ClassLoader.getSystemResourceAsStream("simplelog.properties")) {
            assertThat(resource).isNotNull();
        }

        try {
            Thread.currentThread().setContextClassLoader(null);

            Class<?> simpleLogType = Class.forName("org.apache.commons.logging.impl.SimpleLog");
            Object simpleLog = simpleLogType.getConstructor(String.class).newInstance("example.error.logger");
            int allLevels = simpleLogType.getField("LOG_LEVEL_ALL").getInt(null);

            simpleLogType.getMethod("setLevel", int.class).invoke(simpleLog, allLevels);

            System.setErr(new PrintStream(capturedError, true, StandardCharsets.UTF_8));
            invoke(simpleLog, "error", "error message", failure);
        } finally {
            System.setErr(originalError);
            Thread.currentThread().setContextClassLoader(originalLoader);
        }

        String output = capturedError.toString(StandardCharsets.UTF_8);

        assertThat(output)
                .contains("[ERROR]")
                .contains("error message")
                .contains("IllegalStateException: boom");
    }

    private static void invoke(Object target, String methodName, Object message, Throwable failure) throws Throwable {
        try {
            target.getClass()
                    .getMethod(methodName, Object.class, Throwable.class)
                    .invoke(target, message, failure);
        } catch (InvocationTargetException invocationTargetException) {
            throw invocationTargetException.getTargetException();
        }
    }
}
