/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.named_interfaces_fixture.orders;

import org.springframework.modulith.NamedInterface;

@NamedInterface("orders")
public interface OrderOperations {

    OrderSummary findById(String identifier);

    record OrderSummary(String identifier) {}
}
