/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

public final class FilterValues {
    private FilterValues() {
    }

    public static final class Label {
        private final String value;

        public Label(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Label && value.equals(((Label) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
