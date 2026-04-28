/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Delegator;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatorTest {
    @Test
    void constructOnPrototypeCreatesDelegatorWithNativeObjectDelegee() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Delegator prototype = new Delegator();

            Scriptable constructed = prototype.construct(cx, scope, new Object[0]);

            assertThat(constructed).isInstanceOf(Delegator.class);
            Delegator delegator = (Delegator) constructed;
            assertThat(delegator).isNotSameAs(prototype);
            assertThat(delegator.getDelegee()).isInstanceOf(NativeObject.class);
            assertThat(delegator.getClassName()).isEqualTo("Object");
        } finally {
            Context.exit();
        }
    }
}
