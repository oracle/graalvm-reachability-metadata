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
        PublicDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
        PrivateDefaultCtorBean.CONSTRUCTOR_CALLS.set(0);
        PublicRecordBean.CONSTRUCTOR_CALLS.set(0);
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

    @Test
    void createsRecordsThroughCanonicalConstructorsWhenReadingObjects() throws Exception {
        PublicRecordBean bean = JSON.std.beanFrom(PublicRecordBean.class, """
                {
                  "name": "Ada",
                  "quantity": 3,
                  "stocked": true
                }
                """);

        assertThat(bean.name()).isEqualTo("Ada");
        assertThat(bean.quantity()).isEqualTo(3);
        assertThat(bean.stocked()).isTrue();
        assertThat(PublicRecordBean.CONSTRUCTOR_CALLS).hasValue(1);
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

    public record PublicRecordBean(String name, int quantity, boolean stocked) {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public PublicRecordBean {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }
    }
}
