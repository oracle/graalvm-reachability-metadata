/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

public class OGNLShortcutExpressionTest {

    @Test
    void rendersNestedBeanPropertiesWithShortcutExpressions() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("person", new Person(new Profile("Ada Lovelace")));

        String output = templateEngine.process(
                "<p th:text=\"${person.profile.displayName}\">placeholder</p>",
                context);

        assertThat(output).isEqualTo("<p>Ada Lovelace</p>");
    }

    public static final class Person {

        private final Profile profile;

        public Person(final Profile profile) {
            this.profile = profile;
        }

        public Profile getProfile() {
            return this.profile;
        }
    }

    public static final class Profile {

        private final String displayName;

        public Profile(final String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }
}
