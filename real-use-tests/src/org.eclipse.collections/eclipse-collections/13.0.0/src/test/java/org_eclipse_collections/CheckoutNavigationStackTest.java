/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;

import org.eclipse.collections.api.factory.Stacks;
import org.eclipse.collections.api.partition.stack.PartitionImmutableStack;
import org.eclipse.collections.api.stack.ImmutableStack;
import org.eclipse.collections.api.stack.MutableStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/// Exercises Eclipse Collections' stack subsystem to model checkout navigation history.
public class CheckoutNavigationStackTest {
    @Test
    @Timeout(60)
    public void keepsAnImmutableNavigationSnapshotWhileCheckoutAdvances() {
        MutableStack<String> navigation = Stacks.mutable.with("home", "catalog", "product");
        ImmutableStack<String> productSnapshot = navigation.toImmutable();

        navigation.push("checkout");
        navigation.push("payment");

        assertEquals("payment", navigation.peek());
        assertEquals("checkout", navigation.peekAt(1));
        assertIterableEquals(List.of("payment", "checkout"), navigation.peek(2));
        assertEquals("payment", navigation.pop());
        assertEquals("checkout", navigation.pop());
        assertEquals("product", navigation.peek());

        ImmutableStack<String> accountBranch = productSnapshot.push("account");
        PartitionImmutableStack<String> boundaryPages = accountBranch.partition(
                page -> page.equals("account") || page.equals("home"));

        assertIterableEquals(List.of("product", "catalog", "home"), productSnapshot);
        assertIterableEquals(List.of("account", "home"), boundaryPages.getSelected());
        assertIterableEquals(List.of("product", "catalog"), boundaryPages.getRejected());
        assertEquals("account", accountBranch.peek());
    }
}
