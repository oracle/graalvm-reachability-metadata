/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.MethodAccessor;
import ognl.MethodFailedException;
import ognl.Ognl;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's method-accessor subsystem for a shipping quote page.
 *
 * A fulfillment application routes expression method calls through its application-level quote
 * policy so templates can calculate delivery prices and labels through the supported operations.
 */
public class ShippingQuoteMethodAccessorTest {
    @Test
    @Timeout(60)
    void evaluatesQuoteOperationsThroughRegisteredMethodAccessor() throws OgnlException {
        ShippingQuote quote = new ShippingQuote("Berlin", 3);
        AuditedQuoteMethodAccessor accessor = new AuditedQuoteMethodAccessor();
        MethodAccessor previousAccessor = OgnlRuntime.getMethodAccessor(ShippingQuote.class);
        OgnlRuntime.setMethodAccessor(ShippingQuote.class, accessor);

        try {
            Map context = Ognl.createDefaultContext(quote);
            Object deliveryCost = Ognl.getValue("shippingCost('EXPRESS')", context, quote);
            Object deliveryLabel = Ognl.getValue("deliveryLabel()", context, quote);

            assertThat(deliveryCost).isEqualTo(14.50);
            assertThat(deliveryLabel).isEqualTo("EXPRESS delivery to Berlin");
            assertThat(accessor.invocations())
                    .containsExactly("shippingCost", "deliveryLabel");
        } finally {
            OgnlRuntime.setMethodAccessor(ShippingQuote.class, previousAccessor);
        }
    }

    public static final class AuditedQuoteMethodAccessor implements MethodAccessor {
        private final List<String> invocations = new ArrayList<>();

        @Override
        public Object callStaticMethod(
                Map context, Class targetClass, String methodName, Object[] arguments)
                throws MethodFailedException {
            throw new MethodFailedException(targetClass, "Static quote operations are not supported");
        }

        @Override
        public Object callMethod(
                Map context, Object target, String methodName, Object[] arguments)
                throws MethodFailedException {
            ShippingQuote quote = (ShippingQuote) target;
            invocations.add(methodName);
            if ("shippingCost".equals(methodName) && arguments.length == 1) {
                return quote.shippingCost(String.valueOf(arguments[0]));
            }
            if ("deliveryLabel".equals(methodName) && arguments.length == 0) {
                return quote.deliveryLabel();
            }
            throw new MethodFailedException(target, "Unsupported quote operation: " + methodName);
        }

        public List<String> invocations() {
            return invocations;
        }
    }

    public static final class ShippingQuote {
        private final String destination;
        private final int parcels;

        private ShippingQuote(String destination, int parcels) {
            this.destination = destination;
            this.parcels = parcels;
        }

        public double shippingCost(String service) {
            if (!"EXPRESS".equals(service)) {
                throw new IllegalArgumentException("Unsupported shipping service: " + service);
            }
            return 8.50 + parcels * 2.0;
        }

        public String deliveryLabel() {
            return "EXPRESS delivery to " + destination;
        }
    }
}
