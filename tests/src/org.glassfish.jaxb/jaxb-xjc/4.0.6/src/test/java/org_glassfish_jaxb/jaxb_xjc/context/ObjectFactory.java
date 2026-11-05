/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc.context;

import org_glassfish_jaxb.jaxb_xjc.context.impl.Greeting;

public class ObjectFactory {
    public Greeting createGreeting() {
        return new Greeting();
    }
}
