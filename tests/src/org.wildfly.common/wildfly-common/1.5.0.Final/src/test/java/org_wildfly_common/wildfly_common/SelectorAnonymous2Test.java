/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.wildfly.common.selector.DefaultSelector;
import org.wildfly.common.selector.Selector;

public class SelectorAnonymous2Test {
    @Test
    void createsDefaultSelectorDeclaredBySelectableType() {
        Selector<SelectableValue> selector = Selector.selectorFor(SelectableValue.class);

        assertThat(selector).isInstanceOf(DefaultValueSelector.class);
        assertThat(selector.get()).isInstanceOf(SelectableValue.class);
    }

    @DefaultSelector(DefaultValueSelector.class)
    public static class SelectableValue {
    }

    public static class DefaultValueSelector extends Selector<SelectableValue> {
        public DefaultValueSelector() {
        }

        @Override
        public SelectableValue get() {
            return new SelectableValue();
        }
    }
}
