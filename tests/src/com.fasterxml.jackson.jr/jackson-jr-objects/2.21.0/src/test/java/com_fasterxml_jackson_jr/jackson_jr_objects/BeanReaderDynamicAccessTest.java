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

public class BeanReaderDynamicAccessTest {
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);

    @Test
    void createsBeansThroughPublicDefaultConstructors() throws Exception {
        PublicDefaultCtorBean bean = JSON.std.beanFrom(PublicDefaultCtorBean.class, "{\"name\":\"Ada\"}");

        assertThat(bean.isConstructed()).isTrue();
        assertThat(bean.name).isEqualTo("Ada");
    }

    @Test
    void createsBeansThroughNonPublicDefaultConstructorsWhenAccessIsForced() throws Exception {
        PrivateDefaultCtorBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(PrivateDefaultCtorBean.class,
                "{\"name\":\"Ada\"}");

        assertThat(bean.isConstructed()).isTrue();
        assertThat(bean.name).isEqualTo("Ada");
    }

    public static final class PublicDefaultCtorBean {
        private boolean constructed;
        public String name;

        public PublicDefaultCtorBean() {
            constructed = true;
        }

        public boolean isConstructed() {
            return constructed;
        }
    }

    public static final class PrivateDefaultCtorBean {
        private boolean constructed;
        public String name;

        private PrivateDefaultCtorBean() {
            constructed = true;
        }

        public boolean isConstructed() {
            return constructed;
        }
    }
}
