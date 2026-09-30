/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.glassfish.pfl.basic.reflection.FieldValueHelper;
import org.junit.jupiter.api.Test;

public class FieldValueHelperTest {
    public static class Values {
        public String open = "open";
        private String hidden = "hidden";
    }

    @Test
    public void readsAccessibleAndPrivateFields() throws Exception {
        Values values = new Values();
        Field open = Values.class.getField("open");
        open.setAccessible(true);
        Field hidden = Values.class.getDeclaredField("hidden");

        assertThat(FieldValueHelper.getFieldValue(values, open)).isEqualTo("open");
        assertThat(FieldValueHelper.getFieldValue(values, hidden)).isEqualTo("hidden");
    }
}
