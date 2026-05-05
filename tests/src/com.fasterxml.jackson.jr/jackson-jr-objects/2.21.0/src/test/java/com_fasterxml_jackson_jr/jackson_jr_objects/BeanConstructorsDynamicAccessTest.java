/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.impl.BeanConstructors;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
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
        NestedBeanHolder.CONSTRUCTOR_CALLS.set(0);
        NestedDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
        NestedRecordHolder.CONSTRUCTOR_CALLS.set(0);
        RECORD_CTOR_CALLS.set(0);
    }

    @Test
    void createsBeansThroughPublicDefaultConstructorsWhenReadingObjects() throws Exception {
        PublicDefaultCtorBean bean = JSON.std.beanFrom(PublicDefaultCtorBean.class, """
                {"name":"Ada"}
                """);

        assertThat(bean.name).isEqualTo("Ada");
        assertThat(PublicDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsBeansThroughNonPublicDefaultConstructorsWhenAccessIsForced() throws Exception {
        PrivateDefaultCtorBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(PrivateDefaultCtorBean.class, """
                {"name":"Ada"}
                """);

        assertThat(bean.name).isEqualTo("Ada");
        assertThat(PrivateDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsBeansThroughConstructorsDiscoveredByBeanIntrospection() throws Exception {
        AccessibleBeanConstructors constructors = new AccessibleBeanConstructors(PublicDefaultCtorBean.class);
        BeanPropertyIntrospector.addNonRecordConstructors(PublicDefaultCtorBean.class, constructors);

        PublicDefaultCtorBean bean = (PublicDefaultCtorBean) constructors.createBean();

        assertThat(bean).isNotNull();
        assertThat(PublicDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsNestedBeansThroughDefaultConstructorsWhenReadingProperties() throws Exception {
        NestedBeanHolder bean = JSON.std.beanFrom(NestedBeanHolder.class, """
                {"child":{"name":"Grace"}}
                """);

        assertThat(bean.child.name).isEqualTo("Grace");
        assertThat(NestedBeanHolder.CONSTRUCTOR_CALLS).hasValue(1);
        assertThat(NestedDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsRecordsThroughCanonicalConstructorsWhenReadingObjects() throws Exception {
        RecordCtorBean bean = JSON.std.beanFrom(RecordCtorBean.class, """
                {"name":"Ada","age":37}
                """);

        assertThat(bean.name()).isEqualTo("Ada");
        assertThat(bean.age()).isEqualTo(37);
        assertThat(RECORD_CTOR_CALLS).hasValue(1);
    }

    @Test
    void createsRecordsThroughConstructorsDiscoveredByBeanIntrospection() throws Exception {
        Map<String, String> propertyNames = new LinkedHashMap<>();
        AccessibleBeanConstructors constructors = new AccessibleBeanConstructors(RecordCtorBean.class);
        constructors.addRecordConstructor(BeanPropertyIntrospector.derivePropertiesFromRecordConstructor(
                RecordCtorBean.class, propertyNames, Function.identity()));

        RecordCtorBean bean = (RecordCtorBean) constructors.createRecordBean("Ada", 37);

        assertThat(propertyNames.keySet()).containsExactlyInAnyOrder("name", "age");
        assertThat(bean.name()).isEqualTo("Ada");
        assertThat(bean.age()).isEqualTo(37);
        assertThat(RECORD_CTOR_CALLS).hasValue(1);
    }

    @Test
    void createsNestedRecordsThroughCanonicalConstructorsWhenReadingProperties() throws Exception {
        NestedRecordHolder bean = JSON.std.beanFrom(NestedRecordHolder.class, """
                {"child":{"name":"Grace","age":42}}
                """);

        assertThat(bean.child.name()).isEqualTo("Grace");
        assertThat(bean.child.age()).isEqualTo(42);
        assertThat(NestedRecordHolder.CONSTRUCTOR_CALLS).hasValue(1);
        assertThat(RECORD_CTOR_CALLS).hasValue(1);
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

    public static final class NestedBeanHolder {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public NestedDefaultCtorBean child;

        public NestedBeanHolder() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }

    public static final class NestedDefaultCtorBean {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public String name;

        public NestedDefaultCtorBean() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }

    public static final class NestedRecordHolder {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public RecordCtorBean child;

        public NestedRecordHolder() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }

    public record RecordCtorBean(String name, int age) {
        public RecordCtorBean {
            RECORD_CTOR_CALLS.incrementAndGet();
        }
    }
}
