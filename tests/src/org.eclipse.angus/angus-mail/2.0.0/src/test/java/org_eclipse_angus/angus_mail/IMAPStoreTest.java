/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.protocol.IMAPResponse;
import com.sun.mail.imap.protocol.ListInfo;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class IMAPStoreTest {
    @Test
    public void configuredFolderClassCreatesFoldersFromNamesAndListResponses() throws Exception {
        RecordingIMAPFolder.reset();
        ExposedIMAPStore store = new ExposedIMAPStore(newSessionWithFolderClass());

        IMAPFolder namedFolder = store.createFolder("Projects", '/');
        IMAPFolder listedFolder = store.createFolder(newListInfo("Archive"));

        assertThat(namedFolder).isInstanceOf(RecordingIMAPFolder.class);
        assertThat(listedFolder).isInstanceOf(RecordingIMAPFolder.class);
        assertThat(RecordingIMAPFolder.nameConstructorCalls).hasValue(1);
        assertThat(RecordingIMAPFolder.listInfoConstructorCalls).hasValue(1);
    }

    @Test
    public void configuredFolderClassFallsBackToImapStoreClassLoader() throws Exception {
        try {
            ClassLoader loader = new IsolatingStoreClassLoader();
            Class<?> storeClass = loader.loadClass(IsolatedIMAPStore.class.getName());

            Object store = storeClass
                .getConstructor(Session.class, URLName.class)
                .newInstance(newSessionWithFolderClass(), new URLName("imap://localhost"));

            assertThat(store).isInstanceOf(IMAPStore.class);
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

    private static Session newSessionWithFolderClass() {
        Properties properties = new Properties();
        properties.setProperty("mail.imap.folder.class", RecordingIMAPFolder.class.getName());
        return Session.getInstance(properties);
    }

    private static ListInfo newListInfo(String name) throws Exception {
        return new ListInfo(new IMAPResponse("* LIST (\\Marked) \"/\" " + name));
    }

    public static final class ExposedIMAPStore extends IMAPStore {
        private ExposedIMAPStore(Session session) {
            super(session, new URLName("imap://localhost"));
        }

        private IMAPFolder createFolder(String fullName, char separator) {
            return newIMAPFolder(fullName, separator);
        }

        private IMAPFolder createFolder(ListInfo listInfo) {
            return newIMAPFolder(listInfo);
        }
    }

    public static final class IsolatedIMAPStore extends IMAPStore {
        public IsolatedIMAPStore(Session session, URLName urlName) {
            super(session, urlName);
        }
    }

    public static final class RecordingIMAPFolder extends IMAPFolder {
        private static final AtomicInteger nameConstructorCalls = new AtomicInteger();
        private static final AtomicInteger listInfoConstructorCalls = new AtomicInteger();

        public RecordingIMAPFolder(String fullName, char separator, IMAPStore store, Boolean isNamespace) {
            super(fullName, separator, store, isNamespace);
            nameConstructorCalls.incrementAndGet();
        }

        public RecordingIMAPFolder(ListInfo listInfo, IMAPStore store) {
            super(listInfo, store);
            listInfoConstructorCalls.incrementAndGet();
        }

        private static void reset() {
            nameConstructorCalls.set(0);
            listInfoConstructorCalls.set(0);
        }
    }

    private static final class IsolatingStoreClassLoader extends ClassLoader {
        private IsolatingStoreClassLoader() {
            super(IMAPStore.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (RecordingIMAPFolder.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            if (IsolatedIMAPStore.class.getName().equals(name)) {
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
            if (!IsolatedIMAPStore.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = readClassBytes(name);
            return defineClass(name, bytes, 0, bytes.length);
        }

        private static byte[] readClassBytes(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream input = IMAPStoreTest.class.getClassLoader().getResourceAsStream(resourceName)) {
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
