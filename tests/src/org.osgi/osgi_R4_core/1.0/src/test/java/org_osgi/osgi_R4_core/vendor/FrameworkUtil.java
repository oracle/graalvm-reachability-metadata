/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core.vendor;

import java.util.Dictionary;
import java.util.Objects;

import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

public final class FrameworkUtil {
    private FrameworkUtil() {
    }

    public static Filter createFilter(String filter) throws InvalidSyntaxException {
        return new EqualityFilter(filter);
    }

    private static final class EqualityFilter implements Filter {
        private final String filter;
        private final String key;
        private final String value;

        private EqualityFilter(String filter) throws InvalidSyntaxException {
            if (filter == null) {
                throw new NullPointerException("filter");
            }
            if (!filter.startsWith("(") || !filter.endsWith(")") || !filter.contains("=")) {
                throw new InvalidSyntaxException("Expected a simple equality filter", filter);
            }
            String expression = filter.substring(1, filter.length() - 1);
            int separatorIndex = expression.indexOf('=');
            key = expression.substring(0, separatorIndex);
            value = expression.substring(separatorIndex + 1);
            if (key.isEmpty() || value.isEmpty()) {
                throw new InvalidSyntaxException("Filter key and value must be non-empty", filter);
            }
            this.filter = filter;
        }

        @Override
        public boolean match(ServiceReference reference) {
            return value.equals(reference.getProperty(key));
        }

        @Override
        public boolean match(Dictionary dictionary) {
            return matchCase(dictionary);
        }

        @Override
        public boolean matchCase(Dictionary dictionary) {
            return value.equals(dictionary.get(key));
        }

        @Override
        public String toString() {
            return filter;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof EqualityFilter)) {
                return false;
            }
            EqualityFilter that = (EqualityFilter) object;
            return filter.equals(that.filter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filter);
        }
    }
}
