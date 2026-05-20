/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.pop3;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Properties;

import com.sun.mail.pop3.POP3Message;
import com.sun.mail.pop3.POP3Store;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class POP3StoreTest {
    @Test
    void loadsConfiguredPop3MessageClassConstructor() {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.message.class", POP3Message.class.getName());
        Session session = Session.getInstance(properties);

        POP3Store store = new POP3Store(session, pop3Url());

        assertThat(store.isConnected()).isFalse();
    }

    @Test
    void fallsBackToStoreClassLoaderWhenSubclassLoaderCannotLoadMessageClass() throws ReflectiveOperationException {
        try {
            Properties properties = new Properties();
            properties.setProperty("mail.pop3.message.class", POP3StoreFallbackMessage.class.getName());
            Session session = Session.getInstance(properties);
            StoreSubclassLoader loader = new StoreSubclassLoader(POP3Store.class.getClassLoader());

            POP3Store store = loader.newStore(session, pop3Url());

            assertThat(store.isConnected()).isFalse();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static URLName pop3Url() {
        return new URLName("pop3", "localhost", -1, null, "user", "password");
    }

    private static final class StoreSubclassLoader extends ClassLoader {
        private static final String STORE_CLASS = "org_eclipse_angus.pop3.POP3StoreFallbackTriggerDynamic";
        private static final String MESSAGE_CLASS = "org_eclipse_angus.pop3.POP3StoreFallbackMessage";

        StoreSubclassLoader(ClassLoader parent) {
            super(parent);
        }

        POP3Store newStore(Session session, URLName url) throws ReflectiveOperationException {
            Class<? extends POP3Store> storeClass = loadClass(STORE_CLASS).asSubclass(POP3Store.class);
            return storeClass.getConstructor(Session.class, URLName.class).newInstance(session, url);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (MESSAGE_CLASS.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            if (!STORE_CLASS.equals(name)) {
                return super.loadClass(name, resolve);
            }

            Class<?> storeClass = findLoadedClass(name);
            if (storeClass == null) {
                storeClass = defineStoreClass();
            }
            if (resolve) {
                resolveClass(storeClass);
            }
            return storeClass;
        }

        private Class<?> defineStoreClass() throws ClassNotFoundException {
            try {
                byte[] classBytes = createStoreSubclassBytes();
                return defineClass(STORE_CLASS, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(STORE_CLASS, exception);
            }
        }

        private static byte[] createStoreSubclassBytes() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(0xCAFEBABE);
            output.writeShort(0);
            output.writeShort(52);
            output.writeShort(12);
            writeUtf8(output, STORE_CLASS.replace('.', '/'));
            output.writeByte(7);
            output.writeShort(1);
            writeUtf8(output, "com/sun/mail/pop3/POP3Store");
            output.writeByte(7);
            output.writeShort(3);
            writeUtf8(output, "<init>");
            writeUtf8(output, "(Ljakarta/mail/Session;Ljakarta/mail/URLName;)V");
            output.writeByte(12);
            output.writeShort(5);
            output.writeShort(6);
            output.writeByte(10);
            output.writeShort(4);
            output.writeShort(7);
            writeUtf8(output, "Code");
            writeUtf8(output, "SourceFile");
            writeUtf8(output, "POP3StoreFallbackTriggerDynamic.java");
            output.writeShort(0x0021);
            output.writeShort(2);
            output.writeShort(4);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(1);
            output.writeShort(0x0001);
            output.writeShort(5);
            output.writeShort(6);
            output.writeShort(1);
            output.writeShort(9);
            output.writeInt(19);
            output.writeShort(3);
            output.writeShort(3);
            output.writeInt(7);
            output.writeByte(0x2A);
            output.writeByte(0x2B);
            output.writeByte(0x2C);
            output.writeByte(0xB7);
            output.writeShort(8);
            output.writeByte(0xB1);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(1);
            output.writeShort(10);
            output.writeInt(2);
            output.writeShort(11);
            output.flush();
            return bytes.toByteArray();
        }

        private static void writeUtf8(DataOutputStream output, String value) throws IOException {
            output.writeByte(1);
            output.writeUTF(value);
        }
    }
}
