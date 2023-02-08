/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.Utils;
import org.junit.jupiter.api.Test;

public class TestUtils {
    @Test
    public void testClassLoads() {
        Utils.closeQuietly((AutoCloseable) null);
    }
}
