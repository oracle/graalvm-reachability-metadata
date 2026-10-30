/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.osgi.framework.internal.core.FrameworkProperties;

final class FrameworkProperty implements AutoCloseable {
    private final String key;
    private final String previous;

    FrameworkProperty(String key, String value) {
        this.key = key;
        previous = FrameworkProperties.getProperty(key);
        if (value == null) {
            FrameworkProperties.clearProperty(key);
        } else {
            FrameworkProperties.setProperty(key, value);
        }
    }

    @Override
    public void close() {
        if (previous == null) {
            FrameworkProperties.clearProperty(key);
        } else {
            FrameworkProperties.setProperty(key, previous);
        }
    }
}
