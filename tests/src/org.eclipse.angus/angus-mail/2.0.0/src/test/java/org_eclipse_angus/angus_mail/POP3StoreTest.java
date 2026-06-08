/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.angus.mail.pop3.POP3Message;
import org.eclipse.angus.mail.pop3.POP3Store;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class POP3StoreTest {
    @Test
    public void configuredMessageClassIsResolvedWhenPop3StoreIsCreated() {
        POP3Store store = new POP3Store(newSessionWithMessageClass(RecordingPOP3Message.class.getName()),
                new URLName("pop3://localhost"));

        assertThat(store).isNotNull();
    }

    @Test
    public void configuredMessageClassFallsBackToSystemClassLoader() throws Exception {
        try {
            ClassLoader loader = new IsolatingStoreClassLoader();
            Class<?> storeClass = loader.loadClass(IsolatedPOP3Store.class.getName());

            Object store = storeClass
                    .getConstructor(Session.class, URLName.class)
                    .newInstance(newSessionWithMessageClass(RecordingPOP3Message.class.getName()),
                            new URLName("pop3://localhost"));

            assertThat(store).isInstanceOf(POP3Store.class);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Session newSessionWithMessageClass(String messageClassName) {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.message.class", messageClassName);
        return Session.getInstance(properties);
    }

    public static final class RecordingPOP3Message extends POP3Message {
        public RecordingPOP3Message(Folder folder, int messageNumber) throws MessagingException {
            super(folder, messageNumber);
        }
    }

    public static final class IsolatedPOP3Store extends POP3Store {
        public IsolatedPOP3Store(Session session, URLName urlName) {
            super(session, urlName);
        }
    }

    private static final class IsolatingStoreClassLoader extends ClassLoader {
        private IsolatingStoreClassLoader() {
            super(POP3Store.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (RecordingPOP3Message.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            if (IsolatedPOP3Store.class.getName().equals(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = findClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!IsolatedPOP3Store.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = readClassBytes(name);
            return defineClass(name, bytes, 0, bytes.length);
        }

        private static byte[] readClassBytes(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream input = POP3StoreTest.class.getClassLoader().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                input.transferTo(output);
                return output.toByteArray();
            } catch (IOException exception) {
                ClassNotFoundException classNotFoundException = new ClassNotFoundException(name);
                classNotFoundException.initCause(exception);
                throw classNotFoundException;
            }
        }
    }
}
