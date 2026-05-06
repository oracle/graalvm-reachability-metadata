/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jetbrains.kotlin.name;

/** Minimal fixture for the Kotlin compiler Name type used by K2ReplEvaluator's eval-name constant. */
public final class Name {
    private final String value;

    private Name(String value) {
        this.value = value;
    }

    public static Name identifier(String value) {
        return new Name(value);
    }

    public String asString() {
        return value;
    }
}
