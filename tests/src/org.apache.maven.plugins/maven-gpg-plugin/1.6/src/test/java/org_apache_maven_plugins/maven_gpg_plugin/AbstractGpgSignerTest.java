/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_gpg_plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Console;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.gpg.AbstractGpgSigner;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

public class AbstractGpgSignerTest {
    @Test
    void getPassphraseAttemptsConsolePasswordReaderBeforeInputStreamFallback() throws Exception {
        Unsafe unsafe = unsafe();
        Object console = unsafe.allocateInstance(Console.class);
        Object originalConsole = replaceSystemConsole(unsafe, console);
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;
        String originalConsolePassphrase = System.getProperty("maven.gpg.test.console.passphrase");

        try {
            System.setProperty("maven.gpg.test.console.passphrase", "console-passphrase");
            System.setIn(new ZeroAvailableInputStream("fallback-passphrase\n"));
            System.setOut(new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8));

            TestGpgSigner signer = new TestGpgSigner();
            MavenProject project = new MavenProject();

            String passphrase = signer.getPassphrase(project);

            assertThat(Set.of("console-passphrase", "fallback-passphrase")).contains(passphrase);
            assertThat(project.getProperties().getProperty("gpg.passphrase")).isEqualTo(passphrase);
        } finally {
            restoreProperty("maven.gpg.test.console.passphrase", originalConsolePassphrase);
            System.setIn(originalIn);
            System.setOut(originalOut);
            replaceSystemConsole(unsafe, originalConsole);
        }
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static Object replaceSystemConsole(Unsafe unsafe, Object console) throws NoSuchFieldException {
        Field field = System.class.getDeclaredField("cons");
        Object staticBase = unsafe.staticFieldBase(field);
        long staticOffset = unsafe.staticFieldOffset(field);
        Object previousConsole = unsafe.getObjectVolatile(staticBase, staticOffset);
        unsafe.putObjectVolatile(staticBase, staticOffset, console);
        return previousConsole;
    }

    private static void restoreProperty(String name, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, originalValue);
        }
    }

    private static final class TestGpgSigner extends AbstractGpgSigner {
        @Override
        protected void generateSignatureForFile(File file, File signature) throws MojoExecutionException {
            throw new UnsupportedOperationException("signature generation is not used by this test");
        }
    }

    private static final class ZeroAvailableInputStream extends InputStream {
        private final byte[] bytes;
        private int offset;

        ZeroAvailableInputStream(String input) {
            this.bytes = input.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int read() {
            if (offset == bytes.length) {
                return -1;
            }
            int next = bytes[offset] & 0xff;
            offset++;
            return next;
        }

        @Override
        public int available() {
            return 0;
        }
    }
}
