/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SoapFactoryFinderTest {
    private static final String MESSAGE_FACTORY_PROPERTY = "javax.xml.soap.MessageFactory";

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalMessageFactoryProperty = System.getProperty(MESSAGE_FACTORY_PROPERTY);

    @AfterEach
    void restoreThreadState() {
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalMessageFactoryProperty == null) {
            System.clearProperty(MESSAGE_FACTORY_PROPERTY);
        } else {
            System.setProperty(MESSAGE_FACTORY_PROPERTY, originalMessageFactoryProperty);
        }
    }

    @Test
    void loadsMessageFactoryFromFactoryFinderClassLoaderWhenContextClassLoaderCannotResolveProvider() throws SOAPException {
        ClassLoader isolatedClassLoader = new ClassLoader(null) {
        };
        currentThread.setContextClassLoader(isolatedClassLoader);
        System.clearProperty(MESSAGE_FACTORY_PROPERTY);

        MessageFactory messageFactory = MessageFactory.newInstance();

        assertThat(messageFactory).isInstanceOf(RecordingMessageFactory.class);
        RecordingMessageFactory recordingMessageFactory = (RecordingMessageFactory) messageFactory;
        assertThat(recordingMessageFactory.getConstructorContextClassLoader()).isSameAs(isolatedClassLoader);
    }

    @Test
    void loadsMessageFactoryFromSystemServiceDescriptorWhenContextClassLoaderIsNull() throws SOAPException {
        currentThread.setContextClassLoader(null);
        System.clearProperty(MESSAGE_FACTORY_PROPERTY);

        MessageFactory messageFactory = MessageFactory.newInstance();

        assertThat(messageFactory).isInstanceOf(RecordingMessageFactory.class);
        RecordingMessageFactory recordingMessageFactory = (RecordingMessageFactory) messageFactory;
        assertThat(recordingMessageFactory.getConstructorContextClassLoader()).isNull();
    }

    @Test
    void loadsMessageFactoryFromSystemPropertyUsingContextClassLoader() throws SOAPException {
        ClassLoader testClassLoader = SoapFactoryFinderTest.class.getClassLoader();
        currentThread.setContextClassLoader(testClassLoader);
        System.setProperty(MESSAGE_FACTORY_PROPERTY, RecordingMessageFactory.class.getName());

        MessageFactory messageFactory = MessageFactory.newInstance();

        assertThat(messageFactory).isInstanceOf(RecordingMessageFactory.class);
        RecordingMessageFactory recordingMessageFactory = (RecordingMessageFactory) messageFactory;
        assertThat(recordingMessageFactory.getConstructorContextClassLoader()).isSameAs(testClassLoader);
    }

    @Test
    void loadsMessageFactoryFromSystemPropertyWhenContextClassLoaderIsNull() throws SOAPException {
        currentThread.setContextClassLoader(null);
        System.setProperty(MESSAGE_FACTORY_PROPERTY, RecordingMessageFactory.class.getName());

        MessageFactory messageFactory = MessageFactory.newInstance();

        assertThat(messageFactory).isInstanceOf(RecordingMessageFactory.class);
        RecordingMessageFactory recordingMessageFactory = (RecordingMessageFactory) messageFactory;
        assertThat(recordingMessageFactory.getConstructorContextClassLoader()).isNull();
    }

    public static final class RecordingMessageFactory extends MessageFactory {
        private final ClassLoader constructorContextClassLoader;

        public RecordingMessageFactory() {
            this.constructorContextClassLoader = Thread.currentThread().getContextClassLoader();
        }

        public ClassLoader getConstructorContextClassLoader() {
            return constructorContextClassLoader;
        }

        @Override
        public SOAPMessage createMessage() {
            return null;
        }

        @Override
        public SOAPMessage createMessage(MimeHeaders headers, InputStream inputStream) throws IOException {
            return null;
        }
    }
}
