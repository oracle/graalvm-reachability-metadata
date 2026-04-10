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
import javax.activation.DataHandler;
import org.junit.jupiter.api.Test;

class DataHandlerTest {
    @Test
    void compilerGeneratedClassHelperResolvesDataHandlerByName() throws Throwable {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(DataHandler.class, MethodHandles.lookup());
        MethodHandle classLookup = privateLookup.findStatic(
                DataHandler.class,
                "class$",
                MethodType.methodType(Class.class, String.class)
        );

        Class<?> dataHandlerClass = (Class<?>) classLookup.invokeExact("javax.activation.DataHandler");

        assertThat(dataHandlerClass).isSameAs(DataHandler.class);
    }
}
