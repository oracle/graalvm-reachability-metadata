/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.util.ExceptionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionUtilsTest {
    @Test
    void resolvesLegacyCompilerClassLiteralHelper() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ExceptionUtils.class, MethodHandles.lookup());
        MethodHandle resolver = lookup.findStatic(
                ExceptionUtils.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) resolver.invoke(VelocityException.class.getName());

        assertThat(resolvedClass).isSameAs(VelocityException.class);
    }

    @Test
    void createsAndLinksExceptionsUsingPublicExceptionUtilsApi() {
        Throwable rootCause = new IllegalArgumentException("root cause");

        RuntimeException runtimeException = ExceptionUtils.createRuntimeException("runtime wrapper", rootCause);

        assertThat(runtimeException)
                .hasMessage("runtime wrapper")
                .hasCause(rootCause);

        Throwable initialized = new Exception("initialized later");
        Throwable initializedCause = new IllegalStateException("initialized cause");

        ExceptionUtils.setCause(initialized, initializedCause);

        assertThat(initialized).hasCause(initializedCause);

        Throwable legacyCause = new IllegalArgumentException("legacy cause");
        Throwable legacyException = ExceptionUtils.createWithCause(
                MessageOnlyException.class,
                "legacy wrapper",
                legacyCause);

        assertThat(legacyException)
                .isInstanceOf(MessageOnlyException.class)
                .hasMessageContaining("legacy wrapper caused by")
                .hasMessageContaining("legacy cause");
        assertThat(legacyException.getCause()).isNull();
    }

    public static class MessageOnlyException extends Exception {
        public MessageOnlyException(String message) {
            super(message);
        }
    }
}
