/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.impl.BeanConstructors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanConstructorsDynamicAccessTest {
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);
    private static final AtomicInteger RECORD_CTOR_CALLS = new AtomicInteger();

    @BeforeEach
    void resetConstructorCounters() {
        PublicDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
        PrivateDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
        RECORD_CTOR_CALLS.set(0);
    }

    @Test
    void createsBeansDirectlyThroughPublicDefaultConstructors() throws Exception {
        Constructor<PublicDefaultCtorBean> constructor = PublicDefaultCtorBean.class.getDeclaredConstructor();
        AccessibleBeanConstructors constructors = new AccessibleBeanConstructors(PublicDefaultCtorBean.class);
        constructors.addNoArgsConstructor(constructor);

        PublicDefaultCtorBean bean = (PublicDefaultCtorBean) constructors.createBean();

        assertThat(bean).isNotNull();
        assertThat(PublicDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsBeansDirectlyThroughNonPublicDefaultConstructorsWhenAccessIsForced() throws Exception {
        Constructor<PrivateDefaultCtorBean> constructor = PrivateDefaultCtorBean.class.getDeclaredConstructor();
        AccessibleBeanConstructors constructors = new AccessibleBeanConstructors(PrivateDefaultCtorBean.class);
        constructors.addNoArgsConstructor(constructor);
        constructors.forceAccess();

        PrivateDefaultCtorBean bean = (PrivateDefaultCtorBean) constructors.createBean();

        assertThat(bean).isNotNull();
        assertThat(PrivateDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsBeansThroughPublicDefaultConstructorsWhenReadingObjects() throws Exception {
        PublicDefaultCtorBean bean = JSON.std.beanFrom(PublicDefaultCtorBean.class, "{\"name\":\"Ada\"}");

        assertThat(bean.name).isEqualTo("Ada");
        assertThat(PublicDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsRecordsDirectlyThroughCanonicalConstructors() throws Exception {
        Constructor<RecordCtorBean> constructor = RecordCtorBean.class.getDeclaredConstructor(String.class, int.class);
        AccessibleBeanConstructors constructors = new AccessibleBeanConstructors(RecordCtorBean.class);
        constructors.addRecordConstructor(constructor);

        RecordCtorBean bean = (RecordCtorBean) constructors.createRecordBean("Ada", 37);

        assertThat(bean.name()).isEqualTo("Ada");
        assertThat(bean.age()).isEqualTo(37);
        assertThat(RECORD_CTOR_CALLS).hasValue(1);
    }

    @Test
    void createsRecordsThroughCanonicalConstructorsWhenReadingObjects() throws Exception {
        RecordCtorBean bean = JSON.std.beanFrom(RecordCtorBean.class, "{\"name\":\"Ada\",\"age\":37}");

        assertThat(bean.name()).isEqualTo("Ada");
        assertThat(bean.age()).isEqualTo(37);
        assertThat(RECORD_CTOR_CALLS).hasValue(1);
    }

    @Test
    void createsBeansThroughNonPublicDefaultConstructorsWhenAccessIsForced() throws Exception {
        PrivateDefaultCtorBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(PrivateDefaultCtorBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.name).isEqualTo("Ada");
        assertThat(PrivateDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    static final class AccessibleBeanConstructors extends BeanConstructors {
        AccessibleBeanConstructors(Class<?> valueType) {
            super(valueType);
        }

        Object createBean() throws Exception {
            return create();
        }

        Object createRecordBean(Object... components) throws Exception {
            return createRecord(components);
        }
    }

    public static final class PublicDefaultCtorBean {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public String name;

        public PublicDefaultCtorBean() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }

    public static final class PrivateDefaultCtorBean {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public String name;

        private PrivateDefaultCtorBean() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }

    public record RecordCtorBean(String name, int age) {
        public RecordCtorBean {
            RECORD_CTOR_CALLS.incrementAndGet();
        }
    }
}
