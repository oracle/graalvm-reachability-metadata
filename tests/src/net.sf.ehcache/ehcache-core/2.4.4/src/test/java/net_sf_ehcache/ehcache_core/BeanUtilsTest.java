/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import net.sf.ehcache.Element;
import net.sf.ehcache.hibernate.management.impl.BeanUtils;
import org.junit.jupiter.api.Test;

public class BeanUtilsTest {
    @Test
    void getsPublicBeanPropertyThroughGetter() {
        Element element = new Element("bean-utils-key", "bean-utils-value", 42L);

        Object version = BeanUtils.getBeanProperty(element, "version");

        assertThat(version).isEqualTo(42L);
    }

    @Test
    void getsDeclaredBeanPropertyThroughField() {
        Element element = new Element("bean-utils-key", "bean-utils-value", 42L);

        Object cacheDefaultLifespan = BeanUtils.getBeanProperty(element, "cacheDefaultLifespan");

        assertThat(cacheDefaultLifespan).isEqualTo(Boolean.TRUE);
    }

    @Test
    void getsLongBeanPropertyThroughGetter() throws Exception {
        Element element = new Element("bean-utils-key", "bean-utils-value", 42L);

        long version = BeanUtils.getLongBeanProperty(element, "version");

        assertThat(version).isEqualTo(42L);
    }

    @Test
    void reportsMissingBeanProperty() {
        Element element = new Element("bean-utils-key", "bean-utils-value", 42L);

        assertThatExceptionOfType(NoSuchFieldException.class)
                .isThrownBy(() -> BeanUtils.getLongBeanProperty(element, "missingProperty"))
                .withMessage("missingProperty");
    }
}
