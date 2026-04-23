/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Structured language marker for language-specific libraries.
 */
public record LibraryLanguage(
        String name,
        String version
) {
    @JsonIgnore
    public boolean isScala() {
        return "scala".equals(name);
    }

    @JsonIgnore
    public boolean isScala2() {
        return isScala() && "2".equals(version);
    }

    @JsonIgnore
    public boolean isScala3() {
        return isScala() && "3".equals(version);
    }

    @JsonIgnore
    public boolean isKotlin() {
        return "kotlin".equals(name);
    }
}
