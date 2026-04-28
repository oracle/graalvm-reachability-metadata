/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static com.thoughtworks.proxy.toys.delegate.DelegationMode.SIGNATURE;
import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.kit.ObjectReference;
import com.thoughtworks.proxy.kit.SimpleReference;
import com.thoughtworks.proxy.toys.delegate.Delegating;
import org.junit.jupiter.api.Test;

public class DelegatingInvokerTest {
    @Test
    public void signatureModeLooksUpAndInvokesDelegateMethod() {
        ObjectReference<String> delegate = new SimpleReference<String>("initial");
        ObjectReference<String> proxy = referenceProxy(delegate);

        assertThat(proxy.get()).isEqualTo("initial");

        proxy.set("updated");

        assertThat(delegate.get()).isEqualTo("updated");
        assertThat(proxy.get()).isEqualTo("updated");
    }

    @SuppressWarnings("unchecked")
    private static ObjectReference<String> referenceProxy(ObjectReference<String> delegate) {
        return (ObjectReference<String>) Delegating.proxy(ObjectReference.class)
                .with(delegate)
                .mode(SIGNATURE)
                .build();
    }
}
