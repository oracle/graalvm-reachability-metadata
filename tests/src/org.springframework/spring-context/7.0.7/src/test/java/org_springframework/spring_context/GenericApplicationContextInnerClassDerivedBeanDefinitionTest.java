/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class GenericApplicationContextInnerClassDerivedBeanDefinitionTest {
    @Test
    void selectsPublicConstructorAndAutowiresItsDependency() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(Settings.class, () -> new Settings("catalog"));
            context.registerBean(Catalog.class);
            context.refresh();
            Catalog catalog = context.getBean(Catalog.class);
            assertSame(context.getBean(Settings.class), catalog.settings);
            assertEquals("catalog", catalog.settings.name());
        }
    }

    public record Settings(String name) {
    }

    public static class Catalog {
        private final Settings settings;

        public Catalog(Settings settings) {
            this.settings = settings;
        }
    }
}
