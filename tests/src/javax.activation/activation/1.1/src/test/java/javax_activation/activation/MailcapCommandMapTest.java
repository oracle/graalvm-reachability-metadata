/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import javax.activation.DataContentHandler;
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MailcapCommandMapTest {
    @Test
    void createDataContentHandlerFallsBackToClassForName() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader blockingClassLoader = new ClassLoader(originalClassLoader) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (MailcapCommandMapDataContentHandler.class.getName().equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };

        Thread.currentThread().setContextClassLoader(blockingClassLoader);
        try {
            MailcapCommandMap commandMap = new MailcapCommandMap();
            commandMap.addMailcap(
                "application/x-mailcap-test;; x-java-content-handler=" + MailcapCommandMapDataContentHandler.class.getName()
            );

            DataContentHandler handler = commandMap.createDataContentHandler("application/x-mailcap-test");

            assertThat(handler).isInstanceOf(MailcapCommandMapDataContentHandler.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

}
