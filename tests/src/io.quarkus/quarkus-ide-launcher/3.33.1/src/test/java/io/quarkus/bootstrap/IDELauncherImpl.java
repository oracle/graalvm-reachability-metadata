/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.quarkus.bootstrap;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class IDELauncherImpl {
    private static Path appClasses;
    private static Map<String, Object> context;
    private static int launchCount;
    private static RecordingCloseable closeable = new RecordingCloseable();

    private IDELauncherImpl() {
    }

    public static Closeable launch(Path appClasses, Map<String, Object> context) {
        IDELauncherImpl.appClasses = appClasses;
        IDELauncherImpl.context = new HashMap<>(context);
        IDELauncherImpl.launchCount++;
        IDELauncherImpl.closeable = new RecordingCloseable();
        return IDELauncherImpl.closeable;
    }

    public static void reset() {
        appClasses = null;
        context = null;
        launchCount = 0;
        closeable = new RecordingCloseable();
    }

    public static Path appClasses() {
        return appClasses;
    }

    public static Map<String, Object> context() {
        return context;
    }

    public static int launchCount() {
        return launchCount;
    }

    public static Closeable closeable() {
        return closeable;
    }

    public static boolean closeableClosed() {
        return closeable.closed;
    }

    private static final class RecordingCloseable implements Closeable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
