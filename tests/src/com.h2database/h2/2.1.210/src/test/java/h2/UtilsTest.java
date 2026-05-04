/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.Utils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths in {@link Utils} through its public API.
 */
public class UtilsTest {
    @Test
    void invokesStaticMethodByName() throws Exception {
        Object result = Utils.callStaticMethod(
                ReflectionTarget.class.getName() + ".describe", "h2", 210);

        assertThat(result).isEqualTo("h2:210");
    }

    @Test
    void createsInstanceAndInvokesInstanceMethodByName() throws Exception {
        Object target = Utils.newInstance(
                ReflectionTarget.class.getName(), "database", 2);

        assertThat(target).isInstanceOf(ReflectionTarget.class);
        assertThat(Utils.callMethod(target, "format", "engine")).isEqualTo("database-2-engine");
    }

    @Test
    void loadsCompressedWebConsoleResource() throws Exception {
        byte[] resource = Utils.getResource("/org/h2/server/web/res/_text_en.prop");

        assertThat(resource).isNotNull();
        assertThat(new String(resource, StandardCharsets.UTF_8))
                .contains("a.title=H2 Console")
                .contains("admin.executing=Executing");
    }

    @Test
    void scalesValueForAvailableMemory() {
        int scaled = Utils.scaleForAvailableMemory(1);

        assertThat(scaled).isGreaterThanOrEqualTo(0);
    }

    /**
     * Public target for {@link Utils}' reflection-based invocation helpers.
     */
    public static class ReflectionTarget {
        private final String name;
        private final Integer number;

        public ReflectionTarget(String name, Integer number) {
            this.name = name;
            this.number = number;
        }

        public static String describe(String name, Integer number) {
            return name + ':' + number;
        }

        public String format(String suffix) {
            return name + '-' + number + '-' + suffix;
        }
    }
}
