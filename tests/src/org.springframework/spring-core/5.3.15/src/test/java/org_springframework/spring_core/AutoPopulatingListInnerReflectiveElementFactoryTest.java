/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.AutoPopulatingList;

/** Verifies reflective element construction for auto-populating lists. */
public class AutoPopulatingListInnerReflectiveElementFactoryTest {
    @Test
    void createsMissingElementsWithDefaultConstructor() {
        AutoPopulatingList<Element> elements = new AutoPopulatingList<>(Element.class);

        Element element = elements.get(2);

        assertThat(element.value).isEqualTo("created");
        assertThat(elements).hasSize(3);
        assertThat(elements.get(2)).isSameAs(element);
    }

    public static final class Element {
        private final String value;

        public Element() {
            this.value = "created";
        }
    }
}
