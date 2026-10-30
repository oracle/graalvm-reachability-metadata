/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_testsupport.org_apache_aries_testsupport_unit;

import org.apache.aries.unittest.mocks.MethodCall;
import org.apache.aries.unittest.mocks.Skeleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SkeletonAnonymous1Test {
    @Test
    void delegatesInterfaceCallsAndArgumentsToTheTemplate() {
        FormatterTemplate template = new FormatterTemplate();
        Formatter formatter = Skeleton.newMock(template, Formatter.class);

        assertEquals("report: 7", formatter.format("report", 7));
        assertEquals("summary: 2", formatter.format("summary", 2));
        assertEquals(2, template.invocations);
        Skeleton.getSkeleton(formatter).assertCalled(new MethodCall(Formatter.class, "format", "report", 7));
    }

    public interface Formatter {
        String format(String label, int count);
    }

    public static class FormatterTemplate {
        private int invocations;

        public String format(String label, int count) {
            invocations++;
            return label + ": " + count;
        }
    }
}
