/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_core;

import com.sun.xml.bind.v2.model.nav.Navigator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("rawtypes")
public class ReflectionNavigatorAnonymous3Test {
    @Test
    void enumeratesPublicAndPrivateFieldsWithoutInheritedFields() {
        Navigator<Type, Class, Field, Method> navigator = NavigatorSupport.navigator();
        Collection<? extends Field> fields = navigator.getDeclaredFields(Entry.class);

        assertThat(fields).extracting(navigator::getFieldName)
                .containsExactlyInAnyOrder("title", "sequence");
        for (Field field : fields) {
            assertThat(navigator.getDeclaringClassForField(field)).isEqualTo(Entry.class);
            assertThat(navigator.getFieldType(field))
                    .isEqualTo("title".equals(navigator.getFieldName(field)) ? String.class : long.class);
        }
    }

    public static class BaseEntry {
        public String inherited;
    }

    public static class Entry extends BaseEntry {
        public String title;
        private long sequence;
    }
}
