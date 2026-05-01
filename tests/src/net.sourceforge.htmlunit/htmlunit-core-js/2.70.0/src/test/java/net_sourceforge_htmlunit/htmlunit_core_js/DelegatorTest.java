/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.Delegator;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatorTest {
    @Test
    void constructOnPrototypeCreatesFreshDelegatorWithDefaultDelegee() {
        Context context = Context.enter();
        try {
            Scriptable scope = context.initStandardObjects();
            Delegator prototype = new Delegator();

            Scriptable constructed = prototype.construct(context, scope, new Object[0]);

            assertThat(constructed).isInstanceOf(Delegator.class);
            Delegator delegator = (Delegator) constructed;
            assertThat(delegator).isNotSameAs(prototype);
            Scriptable delegee = delegator.getDelegee();
            assertThat(delegee).isNotNull();

            delegator.put("marker", delegee, "delegated");
            assertThat(delegator.get("marker", delegee)).isEqualTo("delegated");
        } finally {
            Context.exit();
        }
    }
}
