/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.autobean.shared.AutoBean;
import com.google.web.bindery.autobean.shared.AutoBeanFactory;
import com.google.web.bindery.autobean.vm.AutoBeanFactorySource;

import org.junit.jupiter.api.Test;

public class BeanMethodTest {
    @Test
    void categoryMethodIsResolvedForAutoBeanDomainMethod() {
        SampleFactory factory = AutoBeanFactorySource.create(SampleFactory.class);
        AutoBean<SampleBean> bean = factory.sample();
        SampleBean sample = bean.as();

        sample.setName("Ada");

        assertThat(sample.formatGreeting("hello")).isEqualTo("hello Ada");
        assertThat(SampleCategory.getLastBeanType()).isEqualTo(SampleBean.class);
    }

    @AutoBeanFactory.Category(SampleCategory.class)
    public interface SampleFactory extends AutoBeanFactory {
        AutoBean<SampleBean> sample();
    }

    public interface SampleBean {
        String getName();

        void setName(String name);

        String formatGreeting(String prefix);
    }

    public static final class SampleCategory {
        private static Class<?> lastBeanType;

        private SampleCategory() {
        }

        public static String formatGreeting(AutoBean<SampleBean> bean, String prefix) {
            lastBeanType = bean.getType();
            return prefix + " " + bean.as().getName();
        }

        static Class<?> getLastBeanType() {
            return lastBeanType;
        }
    }
}
