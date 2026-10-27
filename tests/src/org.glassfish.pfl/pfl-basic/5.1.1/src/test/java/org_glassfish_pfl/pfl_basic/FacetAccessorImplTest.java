/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.glassfish.pfl.basic.facet.FacetAccessorImpl;
import org.junit.jupiter.api.Test;

public class FacetAccessorImplTest {
    public static class Facet {
        public String value = "initial";

        public String greet(String name) {
            return value + name;
        }
    }

    @Test
    public void accessesAddedFacetMethodsAndFields() throws Exception {
        Facet facet = new Facet();
        FacetAccessorImpl accessor = new FacetAccessorImpl(new Object());
        accessor.addFacet(facet);
        Method method = Facet.class.getMethod("greet", String.class);
        Field field = Facet.class.getField("value");

        assertThat(accessor.invoke(method, " visitor")).isEqualTo("initial visitor");
        assertThat(accessor.get(field)).isEqualTo("initial");
        accessor.set(field, "updated");
        assertThat(accessor.invoke(method, " visitor")).isEqualTo("updated visitor");
    }
}
