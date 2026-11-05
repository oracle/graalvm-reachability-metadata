/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_core;

import com.sun.xml.bind.v2.ClassFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassFactoryTest {
    @Test
    void createsDistinctInstancesUsingANonPublicConstructor() throws Exception {
        try {
            Widget first = ClassFactory.create0(Widget.class);
            Widget second = ClassFactory.create(Widget.class);

            assertThat(first.name).isEqualTo("new widget");
            assertThat(second.name).isEqualTo("new widget");
            assertThat(second).isNotSameAs(first);
        } finally {
            ClassFactory.cleanCache();
        }
    }

    @Test
    void invokesAPublicStaticFactoryMethod() throws Exception {
        // ClassFactory's factory-method API requires a Method argument.
        Object result = ClassFactory.create(WidgetFactory.class.getMethod("createWidget"));

        assertThat(result).isInstanceOf(Widget.class);
        assertThat(((Widget) result).name).isEqualTo("factory widget");
    }

    public static class Widget {
        private String name = "new widget";

        private Widget() {
        }
    }

    public static class WidgetFactory {
        public static Widget createWidget() {
            Widget widget = new Widget();
            widget.name = "factory widget";
            return widget;
        }
    }
}
