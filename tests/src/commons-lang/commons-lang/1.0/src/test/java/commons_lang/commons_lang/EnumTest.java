/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_lang.commons_lang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

public class EnumTest {

    @Test
    public void enumUtilsValidatesThatClassArgumentsExtendCommonsEnum() throws Exception {
        Class<?> enumUtilsClass = ClassLoader.getSystemClassLoader().loadClass("org.apache.commons.lang.enum.EnumUtils");
        Method getEnumMap = enumUtilsClass.getMethod("getEnumMap", Class.class);

        try {
            getEnumMap.invoke(null, Object.class);
            fail("Expected EnumUtils to reject classes that do not extend commons Enum");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("The Class must be a subclass of Enum");
        }
    }

    @Test
    public void enumUtilsReturnsEmptyCollectionsForEnumBaseClass() throws Exception {
        Class<?> enumClass = ClassLoader.getSystemClassLoader().loadClass("org.apache.commons.lang.enum.Enum");
        Class<?> enumUtilsClass = ClassLoader.getSystemClassLoader().loadClass("org.apache.commons.lang.enum.EnumUtils");
        Method getEnumList = enumUtilsClass.getMethod("getEnumList", Class.class);

        Object enumList = getEnumList.invoke(null, enumClass);

        assertThat(enumList).isInstanceOf(List.class);
        assertThat((List<?>) enumList).isEmpty();
    }
}
