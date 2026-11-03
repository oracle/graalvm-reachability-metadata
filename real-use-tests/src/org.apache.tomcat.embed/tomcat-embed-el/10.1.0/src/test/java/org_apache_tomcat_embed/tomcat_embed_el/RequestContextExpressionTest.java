/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.PropertyNotWritableException;
import jakarta.el.StandardELContext;
import java.beans.FeatureDescriptor;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shares a request-scoped application service through Jakarta Expression Language's context
 * storage subsystem. A checkout application can attach request data to the EL context and let
 * expressions use it without making the service a global bean.
 */
public class RequestContextExpressionTest {

    @Test
    @Timeout(60)
    public void evaluatesRequestServiceStoredInExpressionContext() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        RequestContext request = new RequestContext("ORD-11871", 3, 125);
        context.putContext(RequestContext.class, request);
        context.addELResolver(new RequestContextResolver());

        String summary = factory.createValueExpression(
                context,
                "#{request.orderNumber.concat(' / ').concat(request.itemCount).concat(' items')}",
                String.class).getValue(context);
        Number merchandiseTotal = factory.createValueExpression(
                context, "#{request.itemCount * request.unitPrice}", Number.class)
                .getValue(context);
        String route = factory.createValueExpression(
                context,
                "#{request.itemCount >= 3 && request.unitPrice >= 100 ? 'priority' : 'standard'}",
                String.class).getValue(context);

        assertEquals(request, context.getContext(RequestContext.class));
        assertEquals("ORD-11871 / 3 items", summary);
        assertEquals(375, merchandiseTotal.intValue());
        assertEquals("priority", route);
    }

    /** Request data attached to the expression context by the checkout application. */
    public static final class RequestContext {
        private final String orderNumber;
        private final int itemCount;
        private final int unitPrice;

        public RequestContext(String orderNumber, int itemCount, int unitPrice) {
            this.orderNumber = orderNumber;
            this.itemCount = itemCount;
            this.unitPrice = unitPrice;
        }

        public String getOrderNumber() {
            return orderNumber;
        }

        public int getItemCount() {
            return itemCount;
        }

        public int getUnitPrice() {
            return unitPrice;
        }
    }

    /** Resolves the request-scoped service stored in the EL context for application expressions. */
    public static final class RequestContextResolver extends ELResolver {

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (base == null && "request".equals(property)) {
                context.setPropertyResolved(true);
                return context.getContext(RequestContext.class);
            }
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            if (base == null && "request".equals(property)) {
                context.setPropertyResolved(true);
                return RequestContext.class;
            }
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
            if (base == null && "request".equals(property)) {
                context.setPropertyResolved(true);
                throw new PropertyNotWritableException("Request context is read-only");
            }
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            if (base == null && "request".equals(property)) {
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
