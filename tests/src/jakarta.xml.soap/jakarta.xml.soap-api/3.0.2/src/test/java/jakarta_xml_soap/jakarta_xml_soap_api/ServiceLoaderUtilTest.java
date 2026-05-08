/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_soap.jakarta_xml_soap_api;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceLoaderUtilTest {
    private static final String MESSAGE_FACTORY_PROPERTY = MessageFactory.class.getName();
    private static final String INSTANTIABLE_NON_FACTORY_PROVIDER = Object.class.getName();

    @Test
    void systemPropertyProviderIsLoadedWithContextClassLoader() {
        SOAPException exception = assertThrows(
                SOAPException.class,
                () -> withContextClassLoader(
                        new DelegatingClassLoader(),
                        () -> withMessageFactoryProvider(MessageFactory::newInstance)));

        assertProviderWasInstantiatedBeforeTypeCheckFailed(exception);
    }

    @Test
    void systemPropertyProviderIsLoadedWhenContextClassLoaderIsNull() {
        SOAPException exception = assertThrows(
                SOAPException.class,
                () -> withContextClassLoader(
                        null,
                        () -> withMessageFactoryProvider(MessageFactory::newInstance)));

        assertProviderWasInstantiatedBeforeTypeCheckFailed(exception);
    }

    private static void assertProviderWasInstantiatedBeforeTypeCheckFailed(SOAPException exception) {
        String message = exception.getMessage();
        assertTrue(message.contains(INSTANTIABLE_NON_FACTORY_PROVIDER));
        assertTrue(message.contains(MessageFactory.class.getName()));
    }

    private static void withMessageFactoryProvider(ThrowingRunnable action) throws Exception {
        String previousProvider = System.getProperty(MESSAGE_FACTORY_PROPERTY);
        System.setProperty(MESSAGE_FACTORY_PROPERTY, INSTANTIABLE_NON_FACTORY_PROVIDER);
        try {
            action.run();
        } finally {
            restoreProperty(previousProvider);
        }
    }

    private static void withContextClassLoader(ClassLoader classLoader, ThrowingRunnable action) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            action.run();
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    private static void restoreProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(MESSAGE_FACTORY_PROPERTY);
        } else {
            System.setProperty(MESSAGE_FACTORY_PROPERTY, previousProvider);
        }
    }

    private static final class DelegatingClassLoader extends ClassLoader {
        private DelegatingClassLoader() {
            super(ClassLoader.getSystemClassLoader());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
