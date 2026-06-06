/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.builder.BaseFluent;
import io.sundr.builder.VisitableBuilder;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
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
    void retriesBuilderCreationAfterReflectiveConstructionFailure() {
        FallbackResource resource = new FallbackResource("fallback");
        FallbackResourceBuilder.failNextConstructorCall();

        VisitableBuilder<FallbackResource, ?> builder = BaseFluent.builderOf(resource);

        assertThat(builder).isInstanceOf(FallbackResourceBuilder.class);
        assertThat(builder.build()).isSameAs(resource);
        assertThat(builder.build().getName()).isEqualTo("fallback");
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

    public static final class FallbackResource {
        private final String name;

        public FallbackResource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class FallbackResourceBuilder extends BaseFluent<FallbackResourceBuilder>
            implements VisitableBuilder<FallbackResource, FallbackResourceBuilder> {
        private static final AtomicBoolean FAIL_NEXT_CONSTRUCTOR_CALL = new AtomicBoolean();

        private final FallbackResource resource;

        public FallbackResourceBuilder(FallbackResource resource) {
            if (FAIL_NEXT_CONSTRUCTOR_CALL.getAndSet(false)) {
                throw new IllegalStateException("retry builder creation");
            }
            this.resource = resource;
        }

        static void failNextConstructorCall() {
            FAIL_NEXT_CONSTRUCTOR_CALL.set(true);
        }

        @Override
        public FallbackResource build() {
            return resource;
        }
    }
}
