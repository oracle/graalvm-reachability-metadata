/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.awt.Point;
import java.util.Date;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyReaderDynamicAccessTest {
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);

    @Test
    void populatesFieldBackedJdkBeanProperties() throws Exception {
        Point point = JSON_WITH_FORCE_ACCESS.beanFrom(Point.class, "{\"x\":3,\"y\":4}");

        assertThat(point.x).isEqualTo(3);
        assertThat(point.y).isEqualTo(4);
    }

    @Test
    void populatesPublicSetterBackedJdkBeanProperties() throws Exception {
        Date date = JSON_WITH_FORCE_ACCESS.beanFrom(Date.class, "{\"time\":123456789}");

        assertThat(date.getTime()).isEqualTo(123456789L);
    }

    @Test
    void populatesPrivateSetterBackedProperties() throws Exception {
        SetterBackedReaderBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(SetterBackedReaderBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.getName()).isEqualTo("Ada");
        assertThat(bean.getSetterCalls()).isEqualTo(1);
    }

    public static final class SetterBackedReaderBean {
        private String name;
        private int setterCalls;

        public SetterBackedReaderBean() {
        }

        public String getName() {
            return name;
        }

        public int getSetterCalls() {
            return setterCalls;
        }

        private void setName(String name) {
            this.name = name;
            setterCalls++;
        }
    }
}
