/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DetailPanelTest {

    @Test
    void syntheticClassLookupResolvesTheDetailPanelTypeByName() throws Throwable {
        Class<?> detailPanelClass = Class.forName(
                "org.apache.log4j.chainsaw.DetailPanel",
                false,
                DetailPanelTest.class.getClassLoader()
        );
        MethodHandle classLookup = MethodHandles.privateLookupIn(detailPanelClass, MethodHandles.lookup())
                .findStatic(detailPanelClass, "class$", MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invokeExact("org.apache.log4j.chainsaw.DetailPanel");

        assertThat(resolvedClass).isSameAs(detailPanelClass);
    }
}
