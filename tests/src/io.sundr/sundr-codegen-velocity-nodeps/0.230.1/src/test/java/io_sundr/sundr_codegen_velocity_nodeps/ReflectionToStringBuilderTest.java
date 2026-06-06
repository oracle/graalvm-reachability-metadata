/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.builder.ReflectionToStringBuilder;
import io.sundr.deps.org.apache.commons.lang.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

public class ReflectionToStringBuilderTest {

    @Test
    public void toStringReadsDeclaredInstanceFields() {
        ComponentDescriptor descriptor = new ComponentDescriptor("generator", 3, "ready");

        String description = ReflectionToStringBuilder.toString(descriptor, ToStringStyle.SHORT_PREFIX_STYLE);

        assertThat(description)
                .contains("name=generator")
                .contains("priority=3")
                .contains("owner=")
                .doesNotContain("lifecycle")
                .doesNotContain("DESCRIPTOR_TYPE");
    }

    @Test
    public void toStringCanIncludeTransientAndStaticFields() {
        ComponentDescriptor descriptor = new ComponentDescriptor("renderer", 5, "warming");

        String description = ReflectionToStringBuilder.toString(
                descriptor, ToStringStyle.SHORT_PREFIX_STYLE, true, true);

        assertThat(description)
                .contains("name=renderer")
                .contains("priority=5")
                .contains("lifecycle=warming")
                .contains("DESCRIPTOR_TYPE=component");
    }

    private static final class ComponentDescriptor {
        private static final String DESCRIPTOR_TYPE = "component";

        private final String name;
        private final int priority;
        private final String owner;
        private transient String lifecycle;

        private ComponentDescriptor(String name, int priority, String lifecycle) {
            this.name = name;
            this.priority = priority;
            this.owner = null;
            this.lifecycle = lifecycle;
        }
    }
}
