/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.nio.charset.StandardCharsets;

import org.h2.util.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
    @Test
    void loadsResourceFromBundledDataZip() throws Exception {
        String missingResource = "/org/h2/server/web/res/missing-" + System.nanoTime() + ".txt";

        assertThat(Utils.getResource(missingResource)).isNull();
        byte[] resource = Utils.getResource("/org/h2/server/web/res/_text_en.prop");

        assertThat(resource).isNotNull();
        assertThat(new String(resource, StandardCharsets.UTF_8)).contains("H2 Console");
    }

    @Test
    void invokesStaticMethodByName() throws Exception {
        Object result = Utils.callStaticMethod(StaticMethodTarget.class.getName() + ".join", "prefix", 7);

        assertThat(result).isEqualTo("prefix-7");
    }

    @Test
    void invokesInstanceMethodByName() throws Exception {
        InstanceMethodTarget target = new InstanceMethodTarget("left");

        Object result = Utils.callMethod(target, "combine", "right");

        assertThat(result).isEqualTo("left:right");
    }

    @Test
    void createsInstanceByClassName() throws Exception {
        Object result = Utils.newInstance(ConstructorTarget.class.getName(), "created", 3);

        assertThat(result).isInstanceOf(ConstructorTarget.class);
        assertThat(result.toString()).isEqualTo("created#3");
    }

    @Test
    void scalesValueForAvailableMemory() {
        int scaled = Utils.scaleForAvailableMemory(16);

        assertThat(scaled).isGreaterThanOrEqualTo(0);
    }

    public static class StaticMethodTarget {
        public static String join(String text, int value) {
            return text + '-' + value;
        }
    }

    public static class InstanceMethodTarget {
        private final String left;

        public InstanceMethodTarget(String left) {
            this.left = left;
        }

        public String combine(String right) {
            return left + ':' + right;
        }
    }

    public static class ConstructorTarget {
        private final String text;
        private final int value;

        public ConstructorTarget(String text, int value) {
            this.text = text;
            this.value = value;
        }

        @Override
        public String toString() {
            return text + '#' + value;
        }
    }
}
