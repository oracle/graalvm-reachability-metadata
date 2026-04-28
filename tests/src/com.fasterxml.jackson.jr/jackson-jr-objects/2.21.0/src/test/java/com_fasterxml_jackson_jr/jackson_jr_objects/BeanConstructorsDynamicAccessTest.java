/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanConstructorsDynamicAccessTest {
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);

    @Test
    void createsBeansThroughNonPublicNoArgsConstructors() throws Exception {
        HiddenNoArgsBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(HiddenNoArgsBean.class, "{}");

        assertThat(bean.getMarker()).isEqualTo("created");
    }

    @Test
    void createsRecordsThroughTheirCanonicalConstructors() throws Exception {
        HiddenRecordBean record = JSON_WITH_FORCE_ACCESS.beanFrom(HiddenRecordBean.class,
                "{\"name\":\"Ada\",\"count\":7}");

        assertThat(record.name()).isEqualTo("Ada");
        assertThat(record.count()).isEqualTo(7);
    }

    public static final class HiddenNoArgsBean {
        private final String marker;

        private HiddenNoArgsBean() {
            marker = "created";
        }

        public String getMarker() {
            return marker;
        }
    }

    record HiddenRecordBean(String name, int count) {
    }
}
