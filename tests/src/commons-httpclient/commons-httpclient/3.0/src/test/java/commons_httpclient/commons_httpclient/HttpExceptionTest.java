/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.HttpException;
import org.junit.jupiter.api.Test;

public class HttpExceptionTest {
    private static final String THROWABLE_CLASS_CACHE_FIELD_NAME = "class$java$lang$Throwable";

    @Test
    void storesCauseAndPrintsNestedStackTraceToStreamAndWriter() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid header");
        HttpException exception = new HttpException("top-level failure", cause);

        assertThat(exception.getCause()).isSameAs(cause);

        String streamStackTrace = printToStream(exception);
        String writerStackTrace = printToWriter(exception);

        assertThat(streamStackTrace)
                .contains(HttpException.class.getName() + ": top-level failure")
                .contains(IllegalArgumentException.class.getName() + ": invalid header")
                .contains("Caused by:");
        assertThat(writerStackTrace)
                .contains(HttpException.class.getName() + ": top-level failure")
                .contains(IllegalArgumentException.class.getName() + ": invalid header")
                .contains("Caused by:");
    }

    @Test
    void constructorInitializesLegacyThrowableClassCacheWhenEmpty() throws Exception {
        VarHandle throwableClassCache = throwableClassCache();
        Object previousValue = throwableClassCache.get();
        IOException cause = new IOException("fresh cache cause");

        try {
            throwableClassCache.set(null);

            HttpException exception = new HttpException("fresh cache failure", cause);

            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(throwableClassCache.get()).isSameAs(Throwable.class);
        } finally {
            throwableClassCache.set(previousValue);
        }
    }

    private static String printToStream(HttpException exception) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            exception.printStackTrace(stream);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String printToWriter(HttpException exception) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(output, true, StandardCharsets.UTF_8)) {
            exception.printStackTrace(writer);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static VarHandle throwableClassCache() throws IllegalAccessException, NoSuchFieldException {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(HttpException.class, MethodHandles.lookup());
        return privateLookup.findStaticVarHandle(HttpException.class, THROWABLE_CLASS_CACHE_FIELD_NAME, Class.class);
    }
}
