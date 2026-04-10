/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import javax.activation.DataHandler;
import org.junit.jupiter.api.Test;

class DataHandlerTest {
    @Test
    void compilerGeneratedClassHelperResolvesDataHandlerByName() throws ReflectiveOperationException {
        Method classLookup = DataHandler.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);

        Class<?> dataHandlerClass = (Class<?>) classLookup.invoke(null, "javax.activation.DataHandler");

        assertThat(dataHandlerClass).isSameAs(DataHandler.class);
    }
}
