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

public class ShimHandlerTest {
    @Test
    void wrappedAutoBeanShimDispatchesObjectAccessorsMutatorsAndCategoryCalls() {
        SampleCategory.reset();
        SampleFactory factory = AutoBeanFactorySource.create(SampleFactory.class);
        AutoBean<SampleBean> bean = factory.sample(new SampleBeanImpl());
        SampleBean sample = bean.as();

        assertThat(sample.equals(sample)).isTrue();

        sample.setName("Ada");

        assertThat(sample.getName()).isEqualTo("intercepted:Ada");
        assertThat(sample.describe("hello")).isEqualTo("intercepted:hello Ada");
        assertThat(SampleCategory.getInterceptedValues())
                .contains("Ada", "hello Ada");
    }

    @AutoBeanFactory.Category(SampleCategory.class)
    public interface SampleFactory extends AutoBeanFactory {
        AutoBean<SampleBean> sample(SampleBean sampleBean);
    }

    public interface SampleBean {
        String getName();

        void setName(String name);

        String describe(String prefix);
    }

    public static final class SampleBeanImpl implements SampleBean {
        private String name;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String describe(String prefix) {
            return prefix + " " + name;
        }
    }

    public static final class SampleCategory {
        private static final StringBuilder interceptedValues = new StringBuilder();

        private SampleCategory() {
        }

        // Checkstyle: stop method name check
        public static Object __intercept(AutoBean<?> bean, Object value) {
            if (value instanceof String) {
                String stringValue = (String) value;
                interceptedValues.append(stringValue).append('\n');
                return "intercepted:" + stringValue;
            }
            return value;
        }
        // Checkstyle: resume method name check

        static String getInterceptedValues() {
            return interceptedValues.toString();
        }

        static void reset() {
            interceptedValues.setLength(0);
        }
    }
}
