/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import org.junit.jupiter.api.Test;

import org.springframework.data.couchbase.core.convert.OtherConverters;

import static org.assertj.core.api.Assertions.assertThat;

public class OtherConvertersInnerStringToClassTest {

    @Test
    void convertsStoredClassNameToClass() {
        assertThat(OtherConverters.StringToClass.INSTANCE.convert(String.class.getName())).isEqualTo(String.class);
    }
}
