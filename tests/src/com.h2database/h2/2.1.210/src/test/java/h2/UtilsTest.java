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
    void loadsBundledResourcesFromH2Archive() throws Exception {
        byte[] messages = Utils.getResource("/org/h2/res/_messages_en.prop");

        assertThat(messages).isNotEmpty();
        assertThat(new String(messages, StandardCharsets.UTF_8)).contains("Invalid parameter count");
    }

    @Test
    void invokesStaticAndInstanceMethodsThroughUtilsReflectionHelpers() throws Exception {
        Object staticResult = Utils.callStaticMethod(UtilsAccessFixture.class.getName() + ".combine", "count", 3);
        UtilsAccessFixture fixture = new UtilsAccessFixture("value");

        Object instanceResult = Utils.callMethod(fixture, "describe", "prefix");

        assertThat(staticResult).isEqualTo("count:3");
        assertThat(instanceResult).isEqualTo("prefix=value");
    }

    @Test
    void createsInstancesThroughUtilsReflectionHelper() throws Exception {
        Object instance = Utils.newInstance(UtilsAccessFixture.class.getName(), "created");

        assertThat(instance).isInstanceOf(UtilsAccessFixture.class);
        assertThat(((UtilsAccessFixture) instance).getLabel()).isEqualTo("created");
    }

    @Test
    void scalesValuesForAvailableMemory() {
        int scaledValue = Utils.scaleForAvailableMemory(1_024);

        assertThat(scaledValue).isPositive();
    }

    public static final class UtilsAccessFixture {
        private final String label;

        public UtilsAccessFixture(String label) {
            this.label = label;
        }

        public static String combine(String name, Integer value) {
            return name + ':' + value;
        }

        public String describe(String prefix) {
            return prefix + '=' + label;
        }

        public String getLabel() {
            return label;
        }
    }
}
