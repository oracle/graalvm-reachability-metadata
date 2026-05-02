/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_common;

import org.apache.parquet.util.DynConstructors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynConstructorsInnerBuilderTest {
    @Test
    public void findsPublicConstructorByClassName() {
        DynConstructors.Ctor<PublicConstructible> constructor = new DynConstructors.Builder(PublicConstructible.class)
                .impl(PublicConstructible.class.getName(), String.class)
                .build();

        PublicConstructible instance = constructor.newInstance("configured");

        assertThat(constructor.getConstructedClass()).isEqualTo(PublicConstructible.class);
        assertThat(instance.value()).isEqualTo("configured");
    }

    @Test
    public void findsHiddenConstructorByClassName() {
        DynConstructors.Ctor<HiddenConstructible> constructor = new DynConstructors.Builder(HiddenConstructible.class)
                .hiddenImpl(HiddenConstructible.class.getName(), int.class)
                .build();

        HiddenConstructible instance = constructor.newInstance(17);

        assertThat(constructor.getConstructedClass()).isEqualTo(HiddenConstructible.class);
        assertThat(instance.value()).isEqualTo(17);
    }

    public static final class PublicConstructible {
        private final String value;

        public PublicConstructible(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static final class HiddenConstructible {
        private final int value;

        private HiddenConstructible(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }
}
