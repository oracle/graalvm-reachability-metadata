/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

public class ConstructorProjection {
    private final String name;

    public ConstructorProjection(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
