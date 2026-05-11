/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package java.io;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class Console implements Flushable {
    Console() {
    }

    public PrintWriter writer() {
        throw new UnsupportedOperationException("writer is not used by this test");
    }

    public Reader reader() {
        throw new UnsupportedOperationException("reader is not used by this test");
    }

    public Console format(String format, Object... args) {
        throw new UnsupportedOperationException("format is not used by this test");
    }

    public Console format(Locale locale, String format, Object... args) {
        throw new UnsupportedOperationException("format is not used by this test");
    }

    public Console printf(String format, Object... args) {
        return format(format, args);
    }

    public Console printf(Locale locale, String format, Object... args) {
        return format(locale, format, args);
    }

    public String readLine(String format, Object... args) {
        throw new UnsupportedOperationException("readLine is not used by this test");
    }

    public String readLine(Locale locale, String format, Object... args) {
        throw new UnsupportedOperationException("readLine is not used by this test");
    }

    public String readLine() {
        throw new UnsupportedOperationException("readLine is not used by this test");
    }

    public char[] readPassword(String format, Object... args) {
        return System.getProperty("maven.gpg.test.console.passphrase", "console-passphrase").toCharArray();
    }

    public char[] readPassword(Locale locale, String format, Object... args) {
        return readPassword(format, args);
    }

    public char[] readPassword() {
        return readPassword("");
    }

    public void flush() {
    }

    public Charset charset() {
        return StandardCharsets.UTF_8;
    }

    public boolean isTerminal() {
        return true;
    }
}
