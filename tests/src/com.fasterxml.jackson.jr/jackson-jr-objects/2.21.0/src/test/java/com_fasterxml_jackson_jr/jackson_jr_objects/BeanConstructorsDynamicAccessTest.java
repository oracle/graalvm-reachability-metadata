/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanConstructorsDynamicAccessTest {
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);

    @BeforeEach
    void resetConstructorCounters() {
        BeanConstructorsDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
        BeanConstructorsRecordBean.CONSTRUCTOR_CALLS.set(0);
    }

    @Test
    void createsBeansThroughDefaultConstructorsWhenReadingObjects() throws Exception {
        BeanConstructorsDefaultCtorBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(BeanConstructorsDefaultCtorBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.name).isEqualTo("Ada");
        assertThat(BeanConstructorsDefaultCtorBean.CONSTRUCTOR_CALLS).hasValue(1);
    }

    @Test
    void createsLibraryBeansThroughDefaultConstructorsWhenReadingObjects() throws Exception {
        JSON json = JSON.std.beanFrom(JSON.class, "{}");

        assertThat(json).isNotNull();
    }

    @Test
    void createsRecordsThroughCanonicalConstructorsWhenReadingObjects() throws Exception {
        BeanConstructorsRecordBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(BeanConstructorsRecordBean.class, """
                {
                  "name": "Ada",
                  "quantity": 3,
                  "stocked": true
                }
                """);

        assertThat(bean.name()).isEqualTo("Ada");
        assertThat(bean.quantity()).isEqualTo(3);
        assertThat(bean.stocked()).isTrue();
        assertThat(BeanConstructorsRecordBean.CONSTRUCTOR_CALLS).hasValue(1);
    }
}

final class BeanConstructorsDefaultCtorBean {
    static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

    public String name;

    BeanConstructorsDefaultCtorBean() {
        CONSTRUCTOR_CALLS.incrementAndGet();
    }
}

record BeanConstructorsRecordBean(String name, int quantity, boolean stocked) {
    static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

    BeanConstructorsRecordBean {
        CONSTRUCTOR_CALLS.incrementAndGet();
    }
}
