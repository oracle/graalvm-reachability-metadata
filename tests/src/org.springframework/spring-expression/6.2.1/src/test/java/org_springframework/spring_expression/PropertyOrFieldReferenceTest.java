/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyOrFieldReferenceTest {
    private final SpelExpressionParser parser = new SpelExpressionParser(new SpelParserConfiguration(true, true));

    @Test
    void growsNullPropertyByConstructingNestedBean() {
        Customer customer = new Customer();
        Expression expression = this.parser.parseExpression("profile.status");

        String status = expression.getValue(customer, String.class);

        assertThat(status)
                .isEqualTo("new profile");
        assertThat(customer.getProfile())
                .isNotNull();
    }

    public static class Customer {
        private Profile profile;

        public Profile getProfile() {
            return this.profile;
        }

        public void setProfile(Profile profile) {
            this.profile = profile;
        }
    }

    public static class Profile {
        private final String status;

        public Profile() {
            this.status = "new profile";
        }

        public String getStatus() {
            return this.status;
        }
    }
}
