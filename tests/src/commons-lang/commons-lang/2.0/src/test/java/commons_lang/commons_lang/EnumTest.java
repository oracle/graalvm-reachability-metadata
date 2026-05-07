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
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class EnumTest {
    private static final String GENERATED_ENUM_CLASS = "commons_lang.commons_lang.GeneratedEnumValue";
    private static final byte[] GENERATED_ENUM_BYTES = Base64.getMimeDecoder().decode("""
            yv66vgAAADEAFgEALGNvbW1vbnNfbGFuZy9jb21tb25zX2xhbmcvR2VuZXJhdGVkRW51bVZhbHVlBwAB
            AQAhb3JnL2FwYWNoZS9jb21tb25zL2xhbmcvZW51bS9FbnVtBwADAQAFQUxQSEEBAC5MY29tbW9u
            c19sYW5nL2NvbW1vbnNfbGFuZy9HZW5lcmF0ZWRFbnVtVmFsdWU7AQAGPGluaXQ+AQADKClWAQAE
            Q29kZQEABWFscGhhCAAKAQAVKExqYXZhL2xhbmcvU3RyaW5nOylWDAAHAAwKAAQADQEACDxjbGlu
            aXQ+DAAFAAYJAAIAEAwABwAICgACABIBAApTb3VyY2VGaWxlAQAXR2VuZXJhdGVkRW51bVZhbHVl
            LmphdmEAMQACAAQAAAABABkABQAGAAAAAgABAAcACAABAAkAAAATAAIAAQAAAAcqEgu3AA6xAAAA
            AAAIAA8ACAABAAkAAAAXAAIAAAAAAAu7AAJZtwATswARsQAAAAAAAQAUAAAAAgAV
            """);

    @Order(1)
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

    @Order(2)
    @Test
    public void enumUtilsReturnsEmptyCollectionsForEnumBaseClass() throws Exception {
        Class<?> enumClass = ClassLoader.getSystemClassLoader().loadClass("org.apache.commons.lang.enum.Enum");
        Class<?> enumUtilsClass = ClassLoader.getSystemClassLoader().loadClass("org.apache.commons.lang.enum.EnumUtils");
        Method getEnumList = enumUtilsClass.getMethod("getEnumList", Class.class);

        Object enumList = getEnumList.invoke(null, enumClass);

        assertThat(enumList).isInstanceOf(List.class);
        assertThat((List<?>) enumList).isEmpty();
    }

    @Order(3)
    @Test
    public void enumUtilsReturnsEmptyMapForEnumBaseClass() throws Exception {
        Class<?> enumClass = ClassLoader.getSystemClassLoader().loadClass("org.apache.commons.lang.enum.Enum");
        Class<?> enumUtilsClass = ClassLoader.getSystemClassLoader().loadClass("org.apache.commons.lang.enum.EnumUtils");
        Method getEnumMap = enumUtilsClass.getMethod("getEnumMap", Class.class);

        Object enumMap = getEnumMap.invoke(null, enumClass);

        assertThat(enumMap).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) enumMap).isEmpty();
    }

    @Order(4)
    @Test
    public void constructorRegistersGeneratedEnumSubclass() throws Exception {
        try {
            ByteArrayClassLoader classLoader = new ByteArrayClassLoader(EnumTest.class.getClassLoader());
            Class<?> generatedEnumClass = classLoader.define(GENERATED_ENUM_BYTES);
            Class.forName(GENERATED_ENUM_CLASS, true, classLoader);

            Class<?> enumUtilsClass = ClassLoader.getSystemClassLoader().loadClass("org.apache.commons.lang.enum.EnumUtils");
            Method getEnumList = enumUtilsClass.getMethod("getEnumList", Class.class);
            Object enumList = getEnumList.invoke(null, generatedEnumClass);

            assertThat(enumList).isInstanceOf(List.class);
            assertThat((List<?>) enumList).hasSize(1);
            assertThat(((List<?>) enumList).get(0).toString()).isEqualTo("GeneratedEnumValue[alpha]");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        private ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(byte[] classBytes) {
            return defineClass(GENERATED_ENUM_CLASS, classBytes, 0, classBytes.length);
        }
    }
}
