/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package android.util;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class Log {
    public static final int DEBUG = 3;
    public static final int WARN = 5;

    private Log() {
    }

    public static int println(int priority, String tag, String message) {
        return message.length();
    }

    public static String getStackTraceString(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
