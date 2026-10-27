/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_params;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

@ParameterizedClass
@ValueSource(strings = {"jupiter", "params"})
public class ResolverFacadeTest {

    @Parameter
    private String injectedValue;

    @Test
    void injectsParameterizedClassArgumentIntoField() {
        assertThat(injectedValue).isIn("jupiter", "params");
    }
}
