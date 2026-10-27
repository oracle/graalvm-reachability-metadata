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

public class BuilderPojoPropertiesTest {
    @Test
    void mapsRowsThroughAnImmutableBuilder() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:builder_properties;DB_CLOSE_DELAY=-1");
        jdbi.getConfig(JdbiImmutables.class)
                .registerImmutable(View.class, ImmutableView.class, ImmutableView::builder);

        View view = jdbi.withHandle(handle -> handle.createQuery("select 61 id, 'built' label")
                .mapTo(View.class)
                .one());

        assertThat(view.id()).isEqualTo(61);
        assertThat(view.label()).isEqualTo("built");
    }

    public interface View {
        int id();

        String label();
    }

    public static final class ImmutableView implements View {
        private final int id;
        private final String label;

        private ImmutableView(int id, String label) {
            this.id = id;
            this.label = label;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public String label() {
            return label;
        }

        public static final class Builder {
            private int id;
            private String label;

            public Builder id(int value) {
                id = value;
                return this;
            }

            public Builder label(String value) {
                label = value;
                return this;
            }

            public ImmutableView build() {
                return new ImmutableView(id, label);
            }
        }
    }
}
