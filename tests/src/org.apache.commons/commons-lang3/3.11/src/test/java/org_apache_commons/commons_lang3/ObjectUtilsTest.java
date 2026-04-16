/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Test;

public class ObjectUtilsTest {

    @Test
    void clonesPrimitiveArraysByCreatingATypedCopy() {
        final int[] original = new int[] {1, 2, 3};

        final int[] cloned = ObjectUtils.clone(original);

        assertThat(cloned).containsExactly(1, 2, 3);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void clonesCloneableObjectsThroughTheirPublicCloneMethod() {
        final PublicCloneableValue original = new PublicCloneableValue("commons-lang");

        final PublicCloneableValue cloned = ObjectUtils.clone(original);

        assertThat(cloned).isNotNull();
        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getValue()).isEqualTo("commons-lang");
    }

    public static final class PublicCloneableValue implements Cloneable {
        private final String value;

        public PublicCloneableValue(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public PublicCloneableValue clone() {
            try {
                return (PublicCloneableValue) super.clone();
            } catch (final CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }
}
