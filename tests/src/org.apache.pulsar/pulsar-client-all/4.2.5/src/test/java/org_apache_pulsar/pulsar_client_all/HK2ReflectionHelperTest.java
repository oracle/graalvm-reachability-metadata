/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import org.apache.pulsar.shade.org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.junit.jupiter.api.Test;

public class HK2ReflectionHelperTest {
    @Test
    void createsInvokesAndUpdatesMembers() throws Throwable {
        Constructor<MutableValue> constructor = MutableValue.class.getConstructor(String.class);
        MutableValue value = (MutableValue) ReflectionHelper.makeMe(
                constructor, new Object[] {"created"}, false);
        Method decorate = MutableValue.class.getMethod("decorate", String.class);
        Field text = MutableValue.class.getField("text");

        assertThat(ReflectionHelper.invoke(value, decorate, new Object[] {"value"}, false))
                .isEqualTo("value:created");
        ReflectionHelper.setField(text, value, "updated");
        assertThat(value.text).isEqualTo("updated");
    }

    @Test
    void resolvesRawClassForGenericArrayType() throws Exception {
        Type genericArray = GenericArrays.class.getField("values").getGenericType();

        assertThat(ReflectionHelper.getRawClass(genericArray)).isEqualTo(List[].class);
    }

    public static class MutableValue {
        public String text;

        public MutableValue(String text) {
            this.text = text;
        }

        public String decorate(String prefix) {
            return prefix + ":" + text;
        }
    }

    public static class GenericArrays {
        public List<String>[] values;
    }
}
