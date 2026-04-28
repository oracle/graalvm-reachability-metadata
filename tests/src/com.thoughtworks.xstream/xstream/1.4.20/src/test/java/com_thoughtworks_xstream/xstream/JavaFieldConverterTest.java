/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.lang.reflect.Field;

import com.thoughtworks.xstream.XStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaFieldConverterTest {
    @Test
    void unmarshalsDeclaredFieldDescription() {
        XStream xstream = configuredXStream();
        Object restored = xstream.fromXML("""
                <field>
                  <name>NO_REFERENCES</name>
                  <clazz>com.thoughtworks.xstream.XStream</clazz>
                </field>
                """);

        assertThat(restored).isInstanceOf(Field.class);
        Field field = (Field)restored;
        assertThat(field.getDeclaringClass()).isEqualTo(XStream.class);
        assertThat(field.getName()).isEqualTo("NO_REFERENCES");
    }

    private static XStream configuredXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{
            Field.class,
            XStream.class
        });
        return xstream;
    }
}
