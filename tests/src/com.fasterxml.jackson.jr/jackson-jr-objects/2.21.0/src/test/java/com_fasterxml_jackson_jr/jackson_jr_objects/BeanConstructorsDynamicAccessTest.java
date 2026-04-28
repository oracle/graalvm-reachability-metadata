/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.jr.ob.impl.BeanConstructors;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanConstructorsDynamicAccessTest {
    @Test
    void invokesNoArgsConstructorThroughBeanConstructorsCreate() throws Exception {
        InspectableBeanConstructors constructors = new InspectableBeanConstructors(HiddenNoArgsBean.class);
        BeanPropertyIntrospector.addNonRecordConstructors(HiddenNoArgsBean.class, constructors);
        constructors.forceAccess();

        HiddenNoArgsBean bean = (HiddenNoArgsBean) constructors.createBean();

        assertThat(bean.marker()).isEqualTo("created");
    }

    @Test
    void invokesCanonicalRecordConstructorThroughBeanConstructorsCreateRecord() throws Exception {
        InspectableBeanConstructors constructors = new InspectableBeanConstructors(HiddenRecordBean.class);
        constructors.addRecordConstructor(BeanPropertyIntrospector.derivePropertiesFromRecordConstructor(
                HiddenRecordBean.class, new LinkedHashMap<String, String>(), name -> name));
        constructors.forceAccess();

        HiddenRecordBean record = (HiddenRecordBean) constructors.createRecordBean("Ada", 7);

        assertThat(record.name()).isEqualTo("Ada");
        assertThat(record.count()).isEqualTo(7);
    }

    public static final class HiddenNoArgsBean {
        private final String marker;

        private HiddenNoArgsBean() {
            marker = "created";
        }

        public String marker() {
            return marker;
        }
    }

    record HiddenRecordBean(String name, int count) {
    }

    public static final class InspectableBeanConstructors extends BeanConstructors {
        public InspectableBeanConstructors(Class<?> valueType) {
            super(valueType);
        }

        public Object createBean() throws Exception {
            return create();
        }

        public Object createRecordBean(Object... components) throws Exception {
            return createRecord(components);
        }
    }
}
