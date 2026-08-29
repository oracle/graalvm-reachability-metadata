/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

public final class ImmutableRow implements JdbiImmutablesTest.Row {
    private final int id;
    private final String label;

    private ImmutableRow(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public String label() {
        return label;
    }

    public static final class Builder {
        private int id;
        private String label;

        public Builder id(int value) {
            id = value;
            return this;
        }

        public Builder label(String value) {
            label = value;
            return this;
        }

        public ImmutableRow build() {
            return new ImmutableRow(id, label);
        }
    }
}
