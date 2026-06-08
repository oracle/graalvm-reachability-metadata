/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_tx;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MethodMapTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodMapTransactionAttributeSourceTest {

    @Test
    void addTransactionalMethodWithClassAndPatternRegistersMatchingDeclaredMethods() throws Exception {
        MethodMapTransactionAttributeSource source = new MethodMapTransactionAttributeSource();
        TransactionAttribute attribute = new DefaultTransactionAttribute();

        source.addTransactionalMethod(OrderService.class, "create*", attribute);

        assertThat(source.getTransactionAttribute(method("createOrder"), OrderService.class)).isSameAs(attribute);
        assertThat(source.getTransactionAttribute(method("createInvoice"), OrderService.class)).isSameAs(attribute);
        assertThat(source.getTransactionAttribute(method("queryOrders"), OrderService.class)).isNull();
    }

    private static Method method(String name) throws NoSuchMethodException {
        return OrderService.class.getMethod(name);
    }

    static class OrderService {

        public void createOrder() {
        }

        public void createInvoice() {
        }

        public void queryOrders() {
        }
    }
}
