/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.activation.CommandMap;
import org.junit.jupiter.api.Test;

class CommandMapTest {
    @Test
    void compilerGeneratedClassHelperResolvesCommandMapByName() throws Throwable {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(CommandMap.class, MethodHandles.lookup());
        MethodHandle classLookup = privateLookup.findStatic(
                CommandMap.class,
                "class$",
                MethodType.methodType(Class.class, String.class)
        );

        Class<?> commandMapClass = (Class<?>) classLookup.invokeExact("javax.activation.CommandMap");

        assertThat(commandMapClass).isSameAs(CommandMap.class);
    }
}
