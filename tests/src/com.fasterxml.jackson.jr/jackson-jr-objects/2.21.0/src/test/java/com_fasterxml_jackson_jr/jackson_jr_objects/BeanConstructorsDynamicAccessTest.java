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

    @BeforeEach
    void resetConstructorCounters() {
        PublicDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
        PrivateDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
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
}
