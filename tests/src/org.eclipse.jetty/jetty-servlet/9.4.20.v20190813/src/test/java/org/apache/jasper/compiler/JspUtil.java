/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.jasper.compiler;

public final class JspUtil {
    private JspUtil() {
    }

    public static String makeJavaIdentifier(String jsp) {
        return "javaIdentifier:" + jsp;
    }

    public static String makeJavaPackage(String jsp) {
        return "javaPackage:" + jsp;
    }
}
