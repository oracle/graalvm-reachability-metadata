/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_bindings_soap;

import com.sun.xml.messaging.saaj.soap.ver1_1.SOAPFactory1_1Impl;
import com.sun.xml.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPMessage;
import org.apache.cxf.binding.soap.Soap11;
import org.apache.cxf.binding.soap.saaj.SAAJFactoryResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SAAJFactoryResolverTest {

    @Test
    void createsConfiguredSaajFactoriesAndUsesThemForSoap11() throws Exception {
        String previousMessageFactory = System.getProperty(SAAJFactoryResolver.MESSAGE_FACTORY_KEY);
        String previousSoapFactory = System.getProperty(SAAJFactoryResolver.SOAP_FACTORY_KEY);
        System.setProperty(
                SAAJFactoryResolver.MESSAGE_FACTORY_KEY, SOAPMessageFactory1_1Impl.class.getName());
        System.setProperty(SAAJFactoryResolver.SOAP_FACTORY_KEY, SOAPFactory1_1Impl.class.getName());

        try {
            MessageFactory messageFactory = SAAJFactoryResolver.createMessageFactory(Soap11.getInstance());
            SOAPFactory soapFactory = SAAJFactoryResolver.createSOAPFactory(Soap11.getInstance());

            assertThat(messageFactory).isExactlyInstanceOf(SOAPMessageFactory1_1Impl.class);
            assertThat(soapFactory).isExactlyInstanceOf(SOAPFactory1_1Impl.class);

            SOAPMessage message = messageFactory.createMessage();
            assertThat(message.getSOAPBody()).isNotNull();

            SOAPElement element = soapFactory.createElement("payload", "p", "urn:test:payload");
            assertThat(element.getElementQName().getLocalPart()).isEqualTo("payload");
            assertThat(element.getElementQName().getNamespaceURI()).isEqualTo("urn:test:payload");
        } finally {
            restoreProperty(SAAJFactoryResolver.MESSAGE_FACTORY_KEY, previousMessageFactory);
            restoreProperty(SAAJFactoryResolver.SOAP_FACTORY_KEY, previousSoapFactory);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
