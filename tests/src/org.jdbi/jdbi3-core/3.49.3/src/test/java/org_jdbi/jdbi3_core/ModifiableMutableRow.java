/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

public final class ModifiableMutableRow implements JdbiImmutablesTest.MutableRow {
    private int id;
    private String label;

    public ModifiableMutableRow() {}

    @Override
    public int id() {
        return id;
    }

    public boolean idIsSet() {
        return true;
    }

    public ModifiableMutableRow setId(int value) {
        id = value;
        return this;
    }

    @Override
    public String label() {
        return label;
    }

    public boolean labelIsSet() {
        return label != null;
    }

    public ModifiableMutableRow setLabel(String value) {
        label = value;
        return this;
    }
}
