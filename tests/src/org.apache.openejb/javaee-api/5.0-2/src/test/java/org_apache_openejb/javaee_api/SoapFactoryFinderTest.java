/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.io.InputStream;

import javax.xml.namespace.QName;
import javax.xml.soap.Detail;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SoapFactoryFinderTest {
    private static final String MESSAGE_FACTORY_PROPERTY = MessageFactory.class.getName();
    private static final String SOAP_FACTORY_PROPERTY = SOAPFactory.class.getName();
    private static final String SOAP_CONNECTION_FACTORY_PROPERTY = SOAPConnectionFactory.class.getName();

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalMessageFactoryProperty = System.getProperty(MESSAGE_FACTORY_PROPERTY);
    private final String originalSoapFactoryProperty = System.getProperty(SOAP_FACTORY_PROPERTY);
    private final String originalSoapConnectionFactoryProperty = System.getProperty(SOAP_CONNECTION_FACTORY_PROPERTY);

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
        restoreSystemProperty(MESSAGE_FACTORY_PROPERTY, originalMessageFactoryProperty);
        restoreSystemProperty(SOAP_FACTORY_PROPERTY, originalSoapFactoryProperty);
        restoreSystemProperty(SOAP_CONNECTION_FACTORY_PROPERTY, originalSoapConnectionFactoryProperty);
    }

    @Test
    void loadsMessageFactoryFromSystemResourcesWhenContextClassLoaderIsNull() throws Exception {
        currentThread.setContextClassLoader(null);
        clearFactorySystemProperties();

        MessageFactory messageFactory = MessageFactory.newInstance();

        assertThat(messageFactory).isInstanceOf(ServiceResourceMessageFactory.class);
    }

    @Test
    void loadsSoapFactoryFromContextClassLoaderResources() throws Exception {
        currentThread.setContextClassLoader(getClass().getClassLoader());
        clearFactorySystemProperties();

        SOAPFactory soapFactory = SOAPFactory.newInstance();

        assertThat(soapFactory).isInstanceOf(ContextClassLoaderSoapFactory.class);
    }

    @Test
    void fallsBackToFactoryFinderClassLoaderWhenContextClassLoaderCannotResolveProvider() throws Exception {
        currentThread.setContextClassLoader(new ClassLoader(null) {
        });
        clearFactorySystemProperties();

        SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();

        assertThat(soapConnectionFactory).isInstanceOf(FallbackClassLoaderSoapConnectionFactory.class);
    }

    private static void clearFactorySystemProperties() {
        System.clearProperty(MESSAGE_FACTORY_PROPERTY);
        System.clearProperty(SOAP_FACTORY_PROPERTY);
        System.clearProperty(SOAP_CONNECTION_FACTORY_PROPERTY);
    }

    private static void restoreSystemProperty(String propertyName, String value) {
        if (value == null) {
            System.clearProperty(propertyName);
            return;
        }
        System.setProperty(propertyName, value);
    }

    public static final class ServiceResourceMessageFactory extends MessageFactory {
        @Override
        public SOAPMessage createMessage() {
            return null;
        }

        @Override
        public SOAPMessage createMessage(MimeHeaders headers, InputStream inputStream) {
            return null;
        }
    }

    public static final class ContextClassLoaderSoapFactory extends SOAPFactory {
        @Override
        public SOAPElement createElement(Name name) {
            return null;
        }

        @Override
        public SOAPElement createElement(String localName) {
            return null;
        }

        @Override
        public SOAPElement createElement(String localName, String prefix, String uri) {
            return null;
        }

        @Override
        public Detail createDetail() {
            return null;
        }

        @Override
        public Name createName(String localName, String prefix, String uri) {
            return null;
        }

        @Override
        public Name createName(String localName) {
            return null;
        }

        @Override
        public SOAPFault createFault() {
            return null;
        }

        @Override
        public SOAPFault createFault(String reasonText, QName faultCode) {
            return null;
        }
    }

    public static final class FallbackClassLoaderSoapConnectionFactory extends SOAPConnectionFactory {
        @Override
        public SOAPConnection createConnection() {
            return new NoOpSoapConnection();
        }
    }

    public static final class NoOpSoapConnection extends SOAPConnection {
        @Override
        public SOAPMessage call(SOAPMessage request, Object endpoint) throws SOAPException {
            return null;
        }

        @Override
        public void close() throws SOAPException {
        }
    }
}
