/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import org.cyberneko.html.filters.Writer;
import org.junit.jupiter.api.Test;

public class WriterTest {
    private static final String WRITER_CLASS_CACHE_FIELD = "class$org$cyberneko$html$filters$Writer";

    @Test
    void usageRenderingResolvesWriterClassNameWhenCompilerCacheIsEmpty() throws Throwable {
        Class<?> originalCachedClass = setWriterClassCache(null);
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        PrintStream replacementErr = new PrintStream(capturedErr, true, StandardCharsets.UTF_8);

        try {
            System.setErr(replacementErr);

            printUsageMethod().invoke();
        } finally {
            System.setErr(originalErr);
            replacementErr.close();
            setWriterClassCache(originalCachedClass);
        }

        String usage = capturedErr.toString(StandardCharsets.UTF_8);
        assertTrue(usage.contains("usage: java org.cyberneko.html.filters.Writer (options) file ..."));
        assertTrue(usage.contains("-h        Display help screen"));
    }

    private static MethodHandle printUsageMethod() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Writer.class, MethodHandles.lookup());
        return lookup.findStatic(Writer.class, "printUsage", MethodType.methodType(Void.TYPE));
    }

    private static Class<?> setWriterClassCache(Class<?> cachedClass) throws ReflectiveOperationException {
        Field field = Writer.class.getDeclaredField(WRITER_CLASS_CACHE_FIELD);
        field.setAccessible(true);
        Class<?> originalClass = (Class<?>) field.get(null);
        field.set(null, cachedClass);
        return originalClass;
    }
}
