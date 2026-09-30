/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.basic.facet.FacetAccessorImpl;
import org.junit.jupiter.api.Test;

public class FacetAccessorImplAnonymous2Test {
    @Test
    public void addsFacetWithDiscoverableMethods() {
        FacetAccessorImpl accessor = new FacetAccessorImpl(new Object());
        accessor.addFacet(new GreetingFacet());

        assertThat(accessor.facet(GreetingFacet.class)).isNotNull();
    }

    public static class GreetingFacet {
        public String greet() {
            return "hello";
        }
    }
}
