/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import org.apache.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ELSupportTest {

    @Test
    void coercesStringArrayToIntegerArray() {
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();

        Object value = expressionFactory.coerceToType(new String[] {"1", "2", "3"}, Integer[].class);

        assertThat(value).isInstanceOf(Integer[].class);
        assertThat((Integer[]) value).containsExactly(1, 2, 3);
    }
}
