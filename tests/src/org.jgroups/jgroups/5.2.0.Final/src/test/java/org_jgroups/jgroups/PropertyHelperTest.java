/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.conf.PropertyHelper;
import org.jgroups.protocols.DISCARD;
import org.jgroups.util.StackType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyHelperTest {
    @Test
    void convertsAnnotatedFieldValueWithDefaultConverter() throws Exception {
        DISCARD protocol = new DISCARD();
        Field field = DISCARD.class.getDeclaredField("up");

        Object converted = PropertyHelper.getConvertedValue(protocol, field, "0.25", false, StackType.IPv4);

        assertThat(converted).isEqualTo(0.25);
    }
}
