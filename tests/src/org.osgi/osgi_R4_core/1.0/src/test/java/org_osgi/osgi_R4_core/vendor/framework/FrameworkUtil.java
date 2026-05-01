/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core.vendor.framework;

import java.util.Dictionary;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

public final class FrameworkUtil {
    public static int createFilterCalls;
    public static String lastFilter;

    private FrameworkUtil() {}

    public static void reset() {
        createFilterCalls = 0;
        lastFilter = null;
    }

    public static Filter createFilter(String filter) throws InvalidSyntaxException {
        createFilterCalls++;
        lastFilter = filter;
        return new PropertyEqualsFilter(filter);
    }

    private static final class PropertyEqualsFilter implements Filter {
        private final String filter;
        private final String key;
        private final String expectedValue;

        private PropertyEqualsFilter(String filter) throws InvalidSyntaxException {
            if (filter == null) {
                throw new NullPointerException("filter");
            }
            if (!filter.startsWith("(") || !filter.endsWith(")")) {
                throw new InvalidSyntaxException("Filter must be wrapped in parentheses", filter);
            }
            int equalsIndex = filter.indexOf('=');
            if (equalsIndex <= 1 || equalsIndex >= filter.length() - 2) {
                throw new InvalidSyntaxException("Only equality filters are supported", filter);
            }
            this.filter = filter;
            this.key = filter.substring(1, equalsIndex);
            this.expectedValue = filter.substring(equalsIndex + 1, filter.length() - 1);
        }

        @Override
        public boolean match(ServiceReference reference) {
            return false;
        }

        @Override
        public boolean match(Dictionary dictionary) {
            return matchesDictionary(dictionary);
        }

        @Override
        public boolean matchCase(Dictionary dictionary) {
            return matchesDictionary(dictionary);
        }

        @Override
        public String toString() {
            return filter;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Filter && filter.equals(obj.toString());
        }

        @Override
        public int hashCode() {
            return filter.hashCode();
        }

        private boolean matchesDictionary(Dictionary dictionary) {
            Object actualValue = dictionary == null ? null : dictionary.get(key);
            return expectedValue.equals(String.valueOf(actualValue));
        }
    }
}
