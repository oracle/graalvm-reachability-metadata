/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j.support;

import org.apache.commons.logging.impl.SimpleLog;

public final class SimpleLogTestSupport {

    // Initialize SimpleLog once with a null context class loader so it falls back to system resources.
    static {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousLoader = currentThread.getContextClassLoader();

        currentThread.setContextClassLoader(null);
        try {
            new SimpleLog(SimpleLogTestSupport.class.getName());
        }
        finally {
            currentThread.setContextClassLoader(previousLoader);
        }
    }

    private SimpleLogTestSupport() {
    }

    public static SimpleLog newSimpleLog(String name) {
        return new SimpleLog(name);
    }
}
