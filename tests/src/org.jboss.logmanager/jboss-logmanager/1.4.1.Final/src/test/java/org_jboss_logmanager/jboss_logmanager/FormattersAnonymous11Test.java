/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.util.logging.Level;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.formatters.Formatters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormattersAnonymous11Test {

    @Test
    void extendedExceptionFormattingFallsBackWhenThreadContextClassLoaderIsAbsent() {
        final String bootstrapClassName = String.class.getName();
        final String missingClassName = "org.jboss.logmanager.coverage.MissingFrameClass";
        final IllegalStateException failure = new IllegalStateException("boom");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(bootstrapClassName, "valueOf", "String.java", 1),
                new StackTraceElement(missingClassName, "invoke", "MissingFrameClass.java", 2)
        });

        final String formatted = formatWithTccl(null, failure);

        assertThat(formatted)
                .contains("\tat " + bootstrapClassName + ".valueOf")
                .contains("\tat " + missingClassName + ".invoke");
    }

    private static String formatWithTccl(final ClassLoader contextClassLoader, final Throwable thrown) {
        final ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            final ExtLogRecord record = new ExtLogRecord(
                    Level.SEVERE,
                    "coverage",
                    FormattersAnonymous11Test.class.getName()
            );
            record.setThrown(thrown);
            final StringBuilder builder = new StringBuilder();
            Formatters.exceptionFormatStep(false, 0, 0, true).render(builder, record);
            return builder.toString();
        } finally {
            Thread.currentThread().setContextClassLoader(originalTccl);
        }
    }
}
