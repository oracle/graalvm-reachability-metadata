/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.activation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandMapTest {
    @Test
    void compilerGeneratedClassHelperResolvesCommandMapByName() {
        Class<?> commandMapClass = CommandMap.class$("javax.activation.CommandMap");

        assertThat(commandMapClass).isSameAs(CommandMap.class);
    }
}
