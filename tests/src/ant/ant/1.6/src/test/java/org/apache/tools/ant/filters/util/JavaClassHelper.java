/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tools.ant.filters.util;

import java.nio.charset.StandardCharsets;

public final class JavaClassHelper {
    private JavaClassHelper() {
    }

    public static StringBuffer getConstants(byte[] bytes) {
        return new StringBuffer("payload=")
                .append(new String(bytes, StandardCharsets.UTF_8))
                .append(System.lineSeparator());
    }
}
