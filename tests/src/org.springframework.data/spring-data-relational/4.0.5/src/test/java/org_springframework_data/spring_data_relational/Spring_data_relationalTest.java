/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_relational;

import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Spring_data_relationalTest {
    @Test
    void derivesTableNamesFromDomainTypes() {
        RelationalMappingContext context = new RelationalMappingContext();
        NamingStrategy namingStrategy = context.getNamingStrategy();

        assertEquals("purchase_order", namingStrategy.getTableName(PurchaseOrder.class));
        assertEquals("customer_account", namingStrategy.getTableName(CustomerAccount.class));
        assertEquals("purchase_order", namingStrategy.getTableName(PurchaseOrder.class));
    }

    static class PurchaseOrder {
    }

    static class CustomerAccount {
    }
}
