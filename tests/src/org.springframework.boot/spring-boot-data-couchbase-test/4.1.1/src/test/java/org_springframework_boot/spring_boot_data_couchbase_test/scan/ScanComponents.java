/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_couchbase_test.scan;

import org.springframework.stereotype.Component;

public final class ScanComponents {
    private ScanComponents() {
    }

    @Component
    public static class Included {
        public String name() {
            return "catalog";
        }
    }

    @Component
    public static class Excluded extends Included {
    }

    @Component
    public static class Ordinary {
    }
}
