/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.imap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import com.sun.mail.iap.ProtocolException;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.protocol.IMAPResponse;
import com.sun.mail.imap.protocol.ListInfo;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class IMAPStoreTest {
    private static final String MISSING_FOLDER_CLASS = "org_eclipse_angus.imap.DoesNotExist";

    private static final String CHILD_LOADED_STORE_CLASS =
        "org_eclipse_angus.imap.GeneratedChildLoadedIMAPStore";

    private static final String FALLBACK_VISIBLE_FOLDER_CLASS =
        FallbackVisibleFolder.class.getName();

    @Test
    void customFolderClassIsLoadedAndUsedForFolderCreation() throws Exception {
        Session session = sessionWithFolderClass(CustomFolder.class.getName());
        TestIMAPStore store = new TestIMAPStore(session);

        Folder namedFolder = store.createFolder("INBOX", '/', Boolean.FALSE);
        Folder listedFolder = store.createFolder(listInfo(
            "* LIST (\\HasNoChildren) \"/\" \"Archive\""));

        assertThat(namedFolder)
            .isInstanceOf(CustomFolder.class)
            .extracting(Folder::getFullName)
            .isEqualTo("INBOX");
        assertThat(listedFolder)
            .isInstanceOf(CustomFolder.class)
            .extracting(Folder::getFullName)
            .isEqualTo("Archive");
    }

    @Test
    void fallbackClassLoaderIsUsedWhenStoreClassLoaderCannotLoadFolderClass() throws Exception {
        try {
            Session session = sessionWithFolderClass(FALLBACK_VISIBLE_FOLDER_CLASS);
            FolderFactory store = childLoadedStore(session);

            Folder folder = store.createFolder("Fallback", '/', null);

            assertThat(folder)
                .isInstanceOf(FallbackVisibleFolder.class)
                .extracting(Folder::getFullName)
                .isEqualTo("Fallback");
        } catch (Error error) {
            if (NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw error;
        }
    }

    @Test
    void missingCustomFolderClassFallsBackToDefaultFolderImplementation() {
        TestIMAPStore store = new TestIMAPStore(sessionWithFolderClass(MISSING_FOLDER_CLASS));

        Folder folder = store.createFolder("INBOX", '/', null);

        assertThat(folder)
            .isNotInstanceOf(CustomFolder.class)
            .isInstanceOf(IMAPFolder.class)
            .extracting(Folder::getFullName)
            .isEqualTo("INBOX");
    }

    private static Session sessionWithFolderClass(String folderClassName) {
        Properties properties = new Properties();
        properties.setProperty("mail.imap.folder.class", folderClassName);
        return Session.getInstance(properties);
    }

    private static ListInfo listInfo(String response) throws IOException, ProtocolException {
        return new ListInfo(new IMAPResponse(response));
    }

    private static FolderFactory childLoadedStore(Session session) throws Exception {
        ClassLoader parentClassLoader = IMAPStoreTest.class.getClassLoader();
        ClassLoader classLoader = new FolderClassHidingClassLoader(parentClassLoader);
        Class<?> storeClass = classLoader.loadClass(CHILD_LOADED_STORE_CLASS);
        try {
            return (FolderFactory) storeClass.getConstructor(Session.class).newInstance(session);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        }
    }

    public interface FolderFactory {
        IMAPFolder createFolder(String fullName, char separator, Boolean isNamespace);
    }

    public static class TestIMAPStore extends IMAPStore {
        TestIMAPStore(Session session) {
            super(session, new URLName("imap://localhost"), "imap", false);
        }

        IMAPFolder createFolder(String fullName, char separator, Boolean isNamespace) {
            return newIMAPFolder(fullName, separator, isNamespace);
        }

        IMAPFolder createFolder(ListInfo listInfo) {
            return newIMAPFolder(listInfo);
        }
    }

    public static class CustomFolder extends IMAPFolder {
        public CustomFolder(String fullName, char separator, IMAPStore store, Boolean isNamespace) {
            super(fullName, separator, store, isNamespace);
        }

        public CustomFolder(ListInfo listInfo, IMAPStore store) {
            super(listInfo, store);
        }
    }

    public static class FallbackVisibleFolder extends IMAPFolder {
        public FallbackVisibleFolder(String fullName, char separator, IMAPStore store,
                Boolean isNamespace) {
            super(fullName, separator, store, isNamespace);
        }

        public FallbackVisibleFolder(ListInfo listInfo, IMAPStore store) {
            super(listInfo, store);
        }
    }

    private static class FolderClassHidingClassLoader extends ClassLoader {
        FolderClassHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (FALLBACK_VISIBLE_FOLDER_CLASS.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            if (!CHILD_LOADED_STORE_CLASS.equals(name)) {
                return super.loadClass(name, resolve);
            }

            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                loadedClass = findClass(name);
            }
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!CHILD_LOADED_STORE_CLASS.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            try {
                byte[] classBytes = childLoadedStoreBytes(name);
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }

    private static byte[] childLoadedStoreBytes(String className) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(byteArrayOutputStream)) {
            writeClassHeader(output, className);
            writeConstructor(output);
            writeCreateFolderMethod(output);
            output.writeShort(0);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static void writeClassHeader(DataOutputStream output, String className)
            throws IOException {
        output.writeInt(0xCAFEBABE);
        output.writeShort(0);
        output.writeShort(52);
        output.writeShort(27);
        output.writeByte(7);
        output.writeShort(2);
        output.writeByte(1);
        output.writeUTF(className.replace('.', '/'));
        output.writeByte(7);
        output.writeShort(4);
        output.writeByte(1);
        output.writeUTF("com/sun/mail/imap/IMAPStore");
        output.writeByte(7);
        output.writeShort(6);
        output.writeByte(1);
        output.writeUTF("org_eclipse_angus/imap/IMAPStoreTest$FolderFactory");
        output.writeByte(1);
        output.writeUTF("<init>");
        output.writeByte(1);
        output.writeUTF("(Ljakarta/mail/Session;)V");
        output.writeByte(1);
        output.writeUTF("Code");
        output.writeByte(7);
        output.writeShort(11);
        output.writeByte(1);
        output.writeUTF("jakarta/mail/URLName");
        output.writeByte(8);
        output.writeShort(13);
        output.writeByte(1);
        output.writeUTF("imap://localhost");
        output.writeByte(10);
        output.writeShort(10);
        output.writeShort(15);
        output.writeByte(12);
        output.writeShort(7);
        output.writeShort(16);
        output.writeByte(1);
        output.writeUTF("(Ljava/lang/String;)V");
        output.writeByte(8);
        output.writeShort(18);
        output.writeByte(1);
        output.writeUTF("imap");
        output.writeByte(10);
        output.writeShort(3);
        output.writeShort(20);
        output.writeByte(12);
        output.writeShort(7);
        output.writeShort(21);
        output.writeByte(1);
        output.writeUTF("(Ljakarta/mail/Session;Ljakarta/mail/URLName;"
            + "Ljava/lang/String;Z)V");
        output.writeByte(1);
        output.writeUTF("createFolder");
        output.writeByte(1);
        output.writeUTF("(Ljava/lang/String;CLjava/lang/Boolean;)"
            + "Lcom/sun/mail/imap/IMAPFolder;");
        output.writeByte(10);
        output.writeShort(3);
        output.writeShort(25);
        output.writeByte(12);
        output.writeShort(26);
        output.writeShort(23);
        output.writeByte(1);
        output.writeUTF("newIMAPFolder");
        output.writeShort(0x0021);
        output.writeShort(1);
        output.writeShort(3);
        output.writeShort(1);
        output.writeShort(5);
        output.writeShort(0);
        output.writeShort(2);
    }

    private static void writeConstructor(DataOutputStream output) throws IOException {
        output.writeShort(0x0001);
        output.writeShort(7);
        output.writeShort(8);
        output.writeShort(1);
        output.writeShort(9);
        output.writeInt(30);
        output.writeShort(5);
        output.writeShort(2);
        output.writeInt(18);
        output.write(new byte[] {
            0x2A, 0x2B, (byte) 0xBB, 0, 10, 0x59, 0x12, 12,
            (byte) 0xB7, 0, 14, 0x12, 17, 0x03, (byte) 0xB7, 0, 19,
            (byte) 0xB1
        });
        output.writeShort(0);
        output.writeShort(0);
    }

    private static void writeCreateFolderMethod(DataOutputStream output) throws IOException {
        output.writeShort(0x0001);
        output.writeShort(22);
        output.writeShort(23);
        output.writeShort(1);
        output.writeShort(9);
        output.writeInt(20);
        output.writeShort(4);
        output.writeShort(4);
        output.writeInt(8);
        output.write(new byte[] {0x2A, 0x2B, 0x1C, 0x2D, (byte) 0xB6, 0, 24, (byte) 0xB0});
        output.writeShort(0);
        output.writeShort(0);
    }
}
