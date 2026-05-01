/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.kit.SimpleReference;
import com.thoughtworks.proxy.toys.delegate.Delegating;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class DelegatingInvokerTest {
    @Test
    void delegatesToLibraryObjectWithMatchingMethodSignature() {
        ReferenceView proxy = Delegating.proxy(ReferenceView.class)
                .with(new SimpleReference<String>("delegated-value"))
                .build();

        Object value = proxy.get();

        assertThat(value).isEqualTo("delegated-value");
    }

    public interface ReferenceView extends Serializable {
        Object get();
    }
}
