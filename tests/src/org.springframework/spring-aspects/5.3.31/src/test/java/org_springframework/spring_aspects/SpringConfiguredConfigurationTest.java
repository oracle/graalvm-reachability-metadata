/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aspects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect;
import org.springframework.context.annotation.aspectj.SpringConfiguredConfiguration;

public class SpringConfiguredConfigurationTest {

    @Test
    void createsTheSingletonBeanConfigurerAspect() {
        SpringConfiguredConfiguration configuration = new SpringConfiguredConfiguration();

        AnnotationBeanConfigurerAspect aspect = configuration.beanConfigurerAspect();

        assertThat(aspect).isSameAs(AnnotationBeanConfigurerAspect.aspectOf());
        assertThat(AnnotationBeanConfigurerAspect.hasAspect()).isTrue();
    }
}
