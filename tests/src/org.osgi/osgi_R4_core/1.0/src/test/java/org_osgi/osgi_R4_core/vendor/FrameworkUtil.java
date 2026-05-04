/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core.vendor;

import java.util.Dictionary;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

public final class FrameworkUtil {
    private FrameworkUtil() {
    }

    public static Filter createFilter(String filter) throws InvalidSyntaxException {
        if (filter == null) {
            throw new NullPointerException("filter");
        }
        if (!filter.startsWith("(") || !filter.endsWith(")") || !filter.contains("=")) {
            throw new InvalidSyntaxException("Expected a simple equality filter", filter);
        }
        return new SimpleFilter(filter);
    }

    private static final class SimpleFilter implements Filter {
        private final String filter;
        private final String key;
        private final String value;

        private SimpleFilter(String filter) {
            this.filter = filter;
            String expression = filter.substring(1, filter.length() - 1);
            int separatorIndex = expression.indexOf('=');
            this.key = expression.substring(0, separatorIndex);
            this.value = expression.substring(separatorIndex + 1);
        }

        public boolean match(ServiceReference reference) {
            return value.equals(reference.getProperty(key));
        }

        public boolean match(Dictionary dictionary) {
            return value.equals(dictionary.get(key));
        }

        public boolean matchCase(Dictionary dictionary) {
            return match(dictionary);
        }

        public String toString() {
            return filter;
        }

        public boolean equals(Object other) {
            return other instanceof Filter && filter.equals(other.toString());
        }

        public int hashCode() {
            return filter.hashCode();
        }
    }
}
