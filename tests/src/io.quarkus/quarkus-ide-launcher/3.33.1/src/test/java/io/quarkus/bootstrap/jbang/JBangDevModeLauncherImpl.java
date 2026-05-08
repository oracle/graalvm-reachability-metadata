/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.quarkus.bootstrap.jbang;

public final class JBangDevModeLauncherImpl {
    private static String[] args;
    private static String quarkusDevProperty;
    private static ClassLoader contextClassLoader;
    private static int invocationCount;

    private JBangDevModeLauncherImpl() {
    }

    public static void main(String... args) {
        JBangDevModeLauncherImpl.args = args.clone();
        JBangDevModeLauncherImpl.quarkusDevProperty = System.getProperty("quarkus.dev");
        JBangDevModeLauncherImpl.contextClassLoader = Thread.currentThread().getContextClassLoader();
        JBangDevModeLauncherImpl.invocationCount++;
    }

    public static void reset() {
        args = null;
        quarkusDevProperty = null;
        contextClassLoader = null;
        invocationCount = 0;
    }

    public static String[] args() {
        return args == null ? null : args.clone();
    }

    public static String quarkusDevProperty() {
        return quarkusDevProperty;
    }

    public static ClassLoader contextClassLoader() {
        return contextClassLoader;
    }

    public static int invocationCount() {
        return invocationCount;
    }
}
