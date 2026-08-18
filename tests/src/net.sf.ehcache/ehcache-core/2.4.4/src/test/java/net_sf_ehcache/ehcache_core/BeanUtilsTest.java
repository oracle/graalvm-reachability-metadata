/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.ehcache.hibernate.management.impl.BeanUtils;

import org.junit.jupiter.api.Test;

public class BeanUtilsTest {
    @Test
    void readsPropertyThroughPublicGetter() throws NoSuchFieldException {
        GetterBean bean = new GetterBean(17L);

        assertThat(BeanUtils.getBeanProperty(bean, "sampleCount")).isEqualTo(17L);
        assertThat(BeanUtils.getLongBeanProperty(bean, "sampleCount")).isEqualTo(17L);
    }

    @Test
    void readsPropertyThroughDeclaredFieldWhenGetterIsAbsent() throws NoSuchFieldException {
        FieldBean bean = new FieldBean(23L);

        assertThat(BeanUtils.getBeanProperty(bean, "sampleCount")).isEqualTo(23L);
        assertThat(BeanUtils.getLongBeanProperty(bean, "sampleCount")).isEqualTo(23L);
    }

    public static final class GetterBean {
        private final long sampleCount;

        public GetterBean(long sampleCount) {
            this.sampleCount = sampleCount;
        }

        public long getSampleCount() {
            return sampleCount;
        }
    }

    public static final class FieldBean {
        private final long sampleCount;

        public FieldBean(long sampleCount) {
            this.sampleCount = sampleCount;
        }
    }
}
