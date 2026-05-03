/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.FieldAttributes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class FieldAttributesTest {
    @Test
    void readsValueFromWrappedPublicField() throws Exception {
        Field field = FieldCarrier.class.getField("title");
        FieldAttributes attributes = new FieldAttributes(field);
        FieldCarrier carrier = new FieldCarrier("integration-guide");

        Method getMethod = FieldAttributes.class.getDeclaredMethod("get", Object.class);
        getMethod.setAccessible(true);
        Object value = getMethod.invoke(attributes, carrier);

        assertThat(value).isEqualTo("integration-guide");
        assertThat(attributes.getName()).isEqualTo("title");
        assertThat(attributes.getDeclaredClass()).isEqualTo(String.class);
        assertThat(attributes.getDeclaringClass()).isEqualTo(FieldCarrier.class);
    }

    public static final class FieldCarrier {
        public String title;

        public FieldCarrier(String title) {
            this.title = title;
        }
    }
}
