/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hamcrest.hamcrest;

import org.hamcrest.MatcherAssert;
import org.hamcrest.object.HasEqualValues;
import org.junit.jupiter.api.Test;

public class HasEqualValuesTest {

    @Test
    void publicFieldsAreReadWhenComparingObjects() {
        PublicFieldsBean expected = new PublicFieldsBean("Ada", 7);
        PublicFieldsBean actual = new PublicFieldsBean("Ada", 7);

        MatcherAssert.assertThat(actual, new HasEqualValues<>(expected));
    }

    public static final class PublicFieldsBean {
        public final String name;
        public final int priority;

        public PublicFieldsBean(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }
}
