/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.example.library;

public final class ReflectionTarget {

    public static final int COUNTER = 7;

    private final String prefix;

    public ReflectionTarget() {
        this.prefix = "hello";
    }

    public String prefix() {
        return prefix;
    }

    public String greet(String suffix) {
        return prefix + "-" + suffix;
    }
}
