/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.architecture_fixture.orders;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class OrderEventListener {

    @EventListener(OrderCompleted.class)
    public void onOrderCompleted() {}

    public record OrderCompleted(String orderIdentifier) {}
}
