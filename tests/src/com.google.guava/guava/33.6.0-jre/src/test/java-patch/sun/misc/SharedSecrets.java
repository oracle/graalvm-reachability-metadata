/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package sun.misc;

public final class SharedSecrets {
    private static final JavaLangAccess JAVA_LANG_ACCESS = new StackTraceJavaLangAccess();

    private SharedSecrets() {}

    public static JavaLangAccess getJavaLangAccess() {
        return JAVA_LANG_ACCESS;
    }

    private static final class StackTraceJavaLangAccess implements JavaLangAccess {
        @Override
        public StackTraceElement getStackTraceElement(Throwable throwable, int index) {
            return throwable.getStackTrace()[index];
        }

        @Override
        public int getStackTraceDepth(Throwable throwable) {
            return throwable.getStackTrace().length;
        }
    }
}
