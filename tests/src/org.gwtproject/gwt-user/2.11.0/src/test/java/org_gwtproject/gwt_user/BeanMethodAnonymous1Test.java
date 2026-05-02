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

public class BeanMethodAnonymous1Test {
    @Test
    void simplePeerToStringReturnsAutoBeanPayload() {
        SampleFactory factory = AutoBeanFactorySource.create(SampleFactory.class);
        AutoBean<SampleBean> bean = factory.sample();
        SampleBean sample = bean.as();

        sample.setName("Ada");

        assertThat(sample.toString()).contains("\"name\":\"Ada\"");
    }

    public interface SampleFactory extends AutoBeanFactory {
        AutoBean<SampleBean> sample();
    }

    public interface SampleBean {
        String getName();

        void setName(String name);
    }
}
