/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;
import org.apache.cxf.databinding.WrapperHelper;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.junit.jupiter.api.Test;

public class JAXBWrapperHelperTest {
    @Test
    void reflectionFallbackHandlesFieldsGettersAndElementFactories() throws Exception {
        JAXBDataBinding binding = new JAXBDataBinding(JAXBContext.newInstance(String.class));
        WrapperHelper helper = binding.createWrapperHelper(
                ReflectionWrapper.class,
                new QName("urn:cxf-jaxb-test", "reflectionWrapper"),
                Arrays.asList("fieldValue", "name", "payload"),
                Arrays.asList("string", "string", "string"),
                Arrays.<Class<?>>asList(String.class, String.class, String.class));

        ReflectionWrapper wrapper = (ReflectionWrapper) helper.createWrapperObject(
                Arrays.<Object>asList("field", "Grace", "wrapped"));

        assertThat(wrapper.fieldValue).isEqualTo("field");
        assertThat(wrapper.getName()).isEqualTo("Grace");
        assertThat(wrapper.getPayload().getValue()).isEqualTo("wrapped");
        assertThat(helper.getWrapperParts(wrapper)).containsExactly("field", "Grace", "wrapped");
    }

    public static class ReflectionWrapper {
        @XmlElement(name = "fieldValue")
        public String fieldValue;

        private String name;
        private JAXBElement<String> payload;

        public ReflectionWrapper() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public JAXBElement<String> getPayload() {
            return payload;
        }

        public void setPayload(JAXBElement<String> payload) {
            this.payload = payload;
        }
    }
}
