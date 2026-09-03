/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.immutables.JdbiImmutables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PojoBuilderUtilsTest {
    @Test
    void resolvesExactAndCompatibleBuilderSetters() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:builder_setters;DB_CLOSE_DELAY=-1");
        jdbi.getConfig(JdbiImmutables.class)
                .registerImmutable(Summary.class, ImmutableSummary.class, ImmutableSummary::builder);

        Summary summary = jdbi.withHandle(handle -> handle.createQuery("select 71 count, 'compatible' name")
                .mapTo(Summary.class)
                .one());

        assertThat(summary.count()).isEqualTo(71);
        assertThat(summary.name()).isEqualTo("compatible");
    }

    public interface Summary {
        int count();

        String name();
    }

    public static final class ImmutableSummary implements Summary {
        private final int count;
        private final String name;

        private ImmutableSummary(int count, String name) {
            this.count = count;
            this.name = name;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public String name() {
            return name;
        }

        public static final class Builder {
            private int count;
            private String name;

            public Builder count(int value) {
                count = value;
                return this;
            }

            public Builder name(CharSequence value) {
                name = value.toString();
                return this;
            }

            public ImmutableSummary build() {
                return new ImmutableSummary(count, name);
            }
        }
    }
}
