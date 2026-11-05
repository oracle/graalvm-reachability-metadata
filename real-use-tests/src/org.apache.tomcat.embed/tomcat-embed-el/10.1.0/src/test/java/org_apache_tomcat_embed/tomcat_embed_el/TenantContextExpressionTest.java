/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELContext;
import jakarta.el.ELProcessor;
import jakarta.el.ELResolver;
import jakarta.el.PropertyNotWritableException;
import java.beans.FeatureDescriptor;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Resolves a request-scoped tenant context with Jakarta Expression Language's custom resolver API.
 * An embedded web application can keep tenant policy in its request context while using EL to
 * render the policy summary and select the fulfillment route.
 */
public class TenantContextExpressionTest {

    @Test
    @Timeout(60)
    public void evaluatesTenantPolicyThroughApplicationResolver() {
        TenantContext tenant = new TenantContext("EU", "priority", 50);
        ELProcessor expressions = new ELProcessor();
        expressions.getELManager().addELResolver(new TenantContextResolver(tenant));

        String policySummary = expressions.eval(
                "tenant.region.concat(' / ').concat(tenant.plan)");
        Number minimumOrderTotal = expressions.eval("tenant.minimumOrderTotal");
        String fulfillmentRoute = expressions.eval(
                "tenant.region == 'EU' && tenant.plan == 'priority'"
                        + " && tenant.minimumOrderTotal <= 75"
                        + " ? 'express-eu' : 'standard'");

        assertEquals("EU / priority", policySummary);
        assertEquals(50, minimumOrderTotal.intValue());
        assertEquals("express-eu", fulfillmentRoute);
    }

    /** Tenant policy made available to the application's expression resolver. */
    public static final class TenantContext {
        private final String region;
        private final String plan;
        private final int minimumOrderTotal;

        public TenantContext(String region, String plan, int minimumOrderTotal) {
            this.region = region;
            this.plan = plan;
            this.minimumOrderTotal = minimumOrderTotal;
        }

        public String region() {
            return region;
        }

        public String plan() {
            return plan;
        }

        public int minimumOrderTotal() {
            return minimumOrderTotal;
        }
    }

    /** Exposes request-scoped tenant properties as a virtual EL root and property set. */
    public static final class TenantContextResolver extends ELResolver {
        private final TenantContext tenant;

        public TenantContextResolver(TenantContext tenant) {
            this.tenant = tenant;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (base == null && "tenant".equals(property)) {
                context.setPropertyResolved(true);
                return tenant;
            }
            if (base == tenant) {
                context.setPropertyResolved(true);
                return switch (String.valueOf(property)) {
                    case "region" -> tenant.region();
                    case "plan" -> tenant.plan();
                    case "minimumOrderTotal" -> tenant.minimumOrderTotal();
                    default -> null;
                };
            }
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            if (base == null && "tenant".equals(property)) {
                context.setPropertyResolved(true);
                return TenantContext.class;
            }
            if (base == tenant) {
                context.setPropertyResolved(true);
                return switch (String.valueOf(property)) {
                    case "region", "plan" -> String.class;
                    case "minimumOrderTotal" -> Integer.TYPE;
                    default -> null;
                };
            }
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
            if ((base == null && "tenant".equals(property)) || base == tenant) {
                context.setPropertyResolved(true);
                throw new PropertyNotWritableException("Tenant policy is read-only");
            }
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            if ((base == null && "tenant".equals(property)) || base == tenant) {
                context.setPropertyResolved(true);
                return true;
            }
            return false;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return null;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return String.class;
        }
    }
}
