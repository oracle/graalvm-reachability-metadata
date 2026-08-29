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

/** Verifies reflective element creation by an auto-populating list. */
public class AutoPopulatingListInnerReflectiveElementFactoryTest {
    @Test
    void createsMissingElementUsingDefaultConstructor() {
        AutoPopulatingList<Element> elements = new AutoPopulatingList<>(Element.class);

        Element element = elements.get(2);

        assertThat(element.created).isTrue();
        assertThat(elements).hasSize(3);
        assertThat(elements.get(2)).isSameAs(element);
    }

    public static final class Element {
        private final boolean created;

        public Element() {
            this.created = true;
        }
    }
}
