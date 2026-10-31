/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.filter.RegexFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RegexFilterTest {
    @Test
    void appliesNamedPatternFlagsWhenMatchingFormattedMessages() throws Exception {
        RegexFilter filter = RegexFilter.createFilter("accepted.*", new String[]{"CASE_INSENSITIVE", "DOTALL"},
                false, Filter.Result.ACCEPT, Filter.Result.DENY);
        assertEquals(Filter.Result.ACCEPT, filter.filter(TestSupport.event("ACCEPTED\nrequest")));
        assertEquals(Filter.Result.DENY, filter.filter(TestSupport.event("rejected")));
    }
}
