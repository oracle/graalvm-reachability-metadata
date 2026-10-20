/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryAnonymous3Test {
    @Test
    void discoversAFactoryWithTheSystemResourceLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        LogFactory.releaseAll();
        try {
            thread.setContextClassLoader(null);
            assertThat(LogFactory.getFactory()).isNotNull();
        } finally {
            LogFactory.releaseAll();
            thread.setContextClassLoader(originalLoader);
        }
    }
}
