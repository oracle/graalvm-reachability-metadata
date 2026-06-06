/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.fabric8.kubernetes.api.builder.BaseFluent;
import io.fabric8.kubernetes.api.builder.VisitableBuilder;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class BaseFluentTest {
    @Test
    void createsVisitableBuilderUsingBuilderNamingConvention() {
        ConfiguredResource resource = new ConfiguredResource("example");

        VisitableBuilder<ConfiguredResource, ?> builder = BaseFluent.builderOf(resource);

        assertThat(builder).isInstanceOf(ConfiguredResourceBuilder.class);
        assertThat(builder.build()).isEqualTo(resource);
        assertThat(builder.getVisitableMap()).isPresent();
    }

    @Test
    void rejectsItemsWhoseBuilderDoesNotImplementVisitableBuilder() {
        assertThatIllegalStateException()
                .isThrownBy(() -> BaseFluent.builderOf("example"))
                .withMessageContaining("Failed to create builder for: class java.lang.String")
                .withCauseInstanceOf(ClassCastException.class);
    }

    public static final class ConfiguredResource {
        private final String name;

        public ConfiguredResource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConfiguredResource that)) {
                return false;
            }
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    public static final class ConfiguredResourceBuilder extends BaseFluent<ConfiguredResourceBuilder>
            implements VisitableBuilder<ConfiguredResource, ConfiguredResourceBuilder> {
        private final ConfiguredResource resource;

        public ConfiguredResourceBuilder(ConfiguredResource resource) {
            this.resource = resource;
        }

        @Override
        public ConfiguredResource build() {
            return resource;
        }
    }
}
