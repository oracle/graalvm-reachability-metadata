/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.utilint.JarMain;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class JarMainTest {

    private static final String STRING_ARRAY_CLASS_CACHE_FIELD = "array$Ljava$lang$String";

    @Test
    void delegatesToNamedUtilityMainMethod() {
        ByteArrayOutputStream output = invokeJarMain();

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Cache Size");
    }

    @Test
    void resolvesStringArrayParameterWhenSyntheticCacheIsEmpty() throws Exception {
        Field stringArrayClassCache = JarMain.class.getDeclaredField(STRING_ARRAY_CLASS_CACHE_FIELD);
        stringArrayClassCache.setAccessible(true);
        Object previousValue = stringArrayClassCache.get(null);
        try {
            stringArrayClassCache.set(null, null);

            ByteArrayOutputStream output = invokeJarMain();

            assertThat(output.toString(StandardCharsets.UTF_8)).contains("Cache Size");
        } finally {
            stringArrayClassCache.set(null, previousValue);
        }
    }

    private static ByteArrayOutputStream invokeJarMain() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8);
        try {
            System.setOut(capture);

            JarMain.main(new String[] {"DbCacheSize", "-records", "1", "-key", "1"});
            return output;
        } finally {
            System.setOut(originalOut);
            capture.close();
        }
    }
}
