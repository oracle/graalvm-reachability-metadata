/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.lang.reflect.Field;

import org.jgroups.annotations.Property;
import org.jgroups.conf.PropertyHelper;
import org.jgroups.util.StackType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyHelperTest {
    @Test
    void convertsAnnotatedFieldValueWithDefaultConverter() throws Exception {
        Field retryCountField = FieldBackedConfiguration.class.getDeclaredField("retryCount");
        FieldBackedConfiguration configuration = new FieldBackedConfiguration();

        Object converted = PropertyHelper.getConvertedValue(configuration, retryCountField, "42", true, StackType.IPv4);

        assertThat(converted).isEqualTo(42);
    }

    private static final class FieldBackedConfiguration {
        @Property
        private int retryCount;
    }
}
