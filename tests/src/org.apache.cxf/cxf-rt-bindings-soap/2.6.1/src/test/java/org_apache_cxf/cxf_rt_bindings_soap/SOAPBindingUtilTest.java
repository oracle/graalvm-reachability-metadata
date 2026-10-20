/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_bindings_soap;

import com.ibm.wsdl.extensions.soap.SOAPBindingImpl;
import org.apache.cxf.binding.soap.SOAPBindingUtil;
import org.apache.cxf.binding.soap.wsdl.extensions.SoapBinding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SOAPBindingUtilTest {

    @Test
    void delegatesSoapBindingOperationsThroughContextLoaderProxy() {
        SOAPBindingImpl extension = new SOAPBindingImpl();
        extension.setStyle("document");
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(SoapBinding.class.getClassLoader());
            SoapBinding binding = SOAPBindingUtil.getSoapBinding(extension);

            assertThat(binding.getStyle()).isEqualTo("document");
            binding.setTransportURI("urn:test:transport");
            assertThat(extension.getTransportURI()).isEqualTo("urn:test:transport");
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    void fallsBackToSoapBindingLoaderWhenContextLoaderCannotSeeInterface() {
        SOAPBindingImpl extension = new SOAPBindingImpl();
        extension.setTransportURI("urn:fallback:transport");
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(null);
            SoapBinding binding = SOAPBindingUtil.getSoapBinding(extension);

            assertThat(binding.getTransportURI()).isEqualTo("urn:fallback:transport");
            binding.setStyle("rpc");
            assertThat(extension.getStyle()).isEqualTo("rpc");
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }
}
