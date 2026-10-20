/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.apache.cxf.databinding.DataReader;
import org.apache.cxf.databinding.DataWriter;
import org.apache.cxf.databinding.WrapperHelper;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.junit.jupiter.api.Test;

public class JAXBDataBindingTest {
    @Test
    void writesAndReadsRootElementThroughDataBinding() throws Exception {
        JAXBDataBinding binding = new JAXBDataBinding(Customer.class);
        DataWriter<XMLStreamWriter> dataWriter = binding.createWriter(XMLStreamWriter.class);
        StringWriter output = new StringWriter();
        XMLStreamWriter streamWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(output);

        dataWriter.write(new Customer("Ada"), streamWriter);
        streamWriter.close();

        DataReader<XMLStreamReader> dataReader = binding.createReader(XMLStreamReader.class);
        XMLStreamReader streamReader = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(output.toString()));
        Customer restored = (Customer) dataReader.read(streamReader);

        assertThat(restored.getName()).isEqualTo("Ada");
    }

    @Test
    void createsWrapperHelperForBooleanAndReturnProperties() throws Exception {
        JAXBDataBinding binding = new JAXBDataBinding(BooleanWrapper.class);
        WrapperHelper helper = binding.createWrapperHelper(
                BooleanWrapper.class,
                new QName("urn:cxf-jaxb-test", "booleanWrapper"),
                Arrays.asList("enabled", "return"),
                Arrays.asList("boolean", "string"),
                Arrays.<Class<?>>asList(Boolean.TYPE, String.class));

        BooleanWrapper wrapper = (BooleanWrapper) helper.createWrapperObject(Arrays.<Object>asList(true, "ok"));

        assertThat(wrapper.isEnabled()).isTrue();
        assertThat(wrapper.get_return()).isEqualTo("ok");
        assertThat(helper.getWrapperParts(wrapper)).containsExactly(true, "ok");
    }

    @XmlRootElement(name = "customer")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Customer {
        @XmlElement
        private String name;

        public Customer() {
        }

        public Customer(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class BooleanWrapper {
        private boolean enabled;
        private String result;

        public BooleanWrapper() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String get_return() {
            return result;
        }

        public void set_return(String result) {
            this.result = result;
        }
    }
}
