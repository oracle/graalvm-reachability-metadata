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
        PublicConstructorBean bean = JSON.std.beanFrom(PublicConstructorBean.class, "{\"name\":\"Ada\"}");

        assertThat(bean.constructed).isTrue();
        assertThat(bean.name).isEqualTo("Ada");
    }

    @Test
    void createsBeansThroughNonPublicDefaultConstructorsWhenAccessIsForced() throws Exception {
        PrivateConstructorBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(PrivateConstructorBean.class, "{\"name\":\"Ada\"}");

        assertThat(bean.constructed).isTrue();
        assertThat(bean.name).isEqualTo("Ada");
    }

    public static final class PublicConstructorBean {
        public boolean constructed;
        public String name;

        public PublicConstructorBean() {
            this.constructed = true;
        }
    }

    static final class PrivateConstructorBean {
        public boolean constructed;
        public String name;

        private PrivateConstructorBean() {
            this.constructed = true;
        }
    }
}
