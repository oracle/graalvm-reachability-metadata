/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's inherited context subsystem for request-specific customer views.
public class InheritedRequestContextTemplateTest {
    @Test
    @Timeout(60)
    public void rendersRequestValuesWithInheritedAccountDefaults() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        VelocityContext accountContext = new VelocityContext();
        accountContext.put("accountName", "Northwind");
        accountContext.put("supportEmail", "support@northwind.example");

        VelocityContext requestContext = new VelocityContext(accountContext);
        requestContext.put("customerName", "Maya");
        requestContext.put("supportEmail", "priority@northwind.example");

        StringWriter page = new StringWriter();
        boolean rendered = engine.evaluate(
                requestContext,
                page,
                "customer-account",
                "Account: $accountName\n"
                        + "Customer: $customerName\n"
                        + "Support: $supportEmail");

        assertTrue(rendered);
        assertEquals("Account: Northwind\nCustomer: Maya\n"
                + "Support: priority@northwind.example", page.toString());
    }
}
