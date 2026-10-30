/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassMapTest {
    @Test
    void discoversInheritedAndOverriddenMethodsForRepeatedCalls() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("report", new Report());
        assertEquals("Monthly:ready|Monthly", VelocityTestSupport.render(context,
                "$report.title():$report.status()|$report.title()"));
    }

    public static class Document {
        public String title() {
            return "Untitled";
        }

        public String status() {
            return "ready";
        }
    }

    public static class Report extends Document {
        @Override
        public String title() {
            return "Monthly";
        }
    }
}
