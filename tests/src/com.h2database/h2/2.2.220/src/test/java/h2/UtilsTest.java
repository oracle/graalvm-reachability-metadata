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

public class UtilsTest {
    @Test
    void createsInstanceUsingBestMatchingPublicConstructor() throws Exception {
        Object instance = Utils.newInstance(UtilityFixture.class.getName(), "created", 4);

        assertThat(instance).isInstanceOf(UtilityFixture.class);
        assertThat(instance.toString()).isEqualTo("created:4");
    }

    @Test
    void invokesStaticAndInstanceMethodsByName() throws Exception {
        Object joined = Utils.callStaticMethod(UtilityFixture.class.getName() + ".join", "left", "right");
        UtilityFixture fixture = new UtilityFixture("instance", 2);

        Object description = Utils.callMethod(fixture, "describe", "prefix");

        assertThat(joined).isEqualTo("left:right");
        assertThat(description).isEqualTo("prefix:instance:2");
    }

    @Test
    void loadsResourceFromBundledResourceArchive() throws Exception {
        byte[] resource = Utils.getResource("/org/h2/res/help.csv");

        assertThat(new String(resource, StandardCharsets.UTF_8))
                .contains("\"SECTION\",\"TOPIC\",\"SYNTAX\",\"TEXT\",\"EXAMPLE\"");
    }

    @Test
    void scalesValueForAvailableMemory() {
        assertThat(Utils.scaleForAvailableMemory(1)).isGreaterThanOrEqualTo(0);
    }

    public static class UtilityFixture {
        private final String name;
        private final int count;

        public UtilityFixture() {
            this("default", 0);
        }

        public UtilityFixture(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public static String join(String left, String right) {
            return left + ':' + right;
        }

        public String describe(String prefix) {
            return prefix + ':' + name + ':' + count;
        }

        @Override
        public String toString() {
            return name + ':' + count;
        }
    }
}
