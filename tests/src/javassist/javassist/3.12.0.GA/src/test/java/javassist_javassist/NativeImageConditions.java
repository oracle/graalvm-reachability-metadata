/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

final class NativeImageConditions {
    private NativeImageConditions() {
    }

    static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
