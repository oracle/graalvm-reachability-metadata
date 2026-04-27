/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.log.format;

public final class ExtendedPatternFormatterAccess {
    private ExtendedPatternFormatterAccess() {
    }

    public static Class<?> resolveClass(String packageName, String simpleName) {
        return ExtendedPatternFormatter.class$(packageName + simpleName);
    }
}
