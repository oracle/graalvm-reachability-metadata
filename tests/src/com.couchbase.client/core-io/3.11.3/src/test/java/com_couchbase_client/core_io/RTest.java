/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.xbill.DNS.dnssec.R;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RTest {
    @Test
    void formatsDiagnosticsFromApplicationMessageBundle() {
        R.setBundle(null);
        R.setUseNeutralMessages(false);
        try {
            assertEquals("DNS validation for example.test: secure",
                    R.get("test.validation", "example.test", "secure"));
            R.setUseNeutralMessages(true);
            assertEquals("test.validation:example.test:secure",
                    R.get("test.validation", "example.test", "secure"));
        } finally {
            R.setUseNeutralMessages(false);
            R.setBundle(null);
        }
    }
}
