/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.annotations.Property;
import org.jgroups.conf.PropertyHelper;
import org.jgroups.util.StackType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyHelperTest {
    @BeforeAll
    static void configureLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    @Test
    void convertsAnnotatedFieldValueWithoutPropertyMap() throws Exception {
        Field thresholdField = ConfigurableComponent.class.getDeclaredField("threshold");

        Object convertedValue = PropertyHelper.getConvertedValue(
                new ConfigurableComponent(),
                thresholdField,
                "123",
                false,
                StackType.IPv4);

        assertThat(convertedValue).isEqualTo(123);
    }

    public static class ConfigurableComponent {
        @Property
        private int threshold;
    }
}
