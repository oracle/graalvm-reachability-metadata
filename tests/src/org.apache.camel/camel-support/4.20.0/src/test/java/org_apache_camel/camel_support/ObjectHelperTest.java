/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.lang.reflect.Method;

import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.SimpleUuidGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectHelperTest {
    @Test
    void newInstanceUsesDefaultConstructor() {
        SimpleUuidGenerator generator = ObjectHelper.newInstance(SimpleUuidGenerator.class);

        assertThat(generator.generateUuid()).isEqualTo("1");
    }

    @Test
    void newInstanceCastsDefaultConstructedObjectToExpectedType() {
        UuidGenerator generator = ObjectHelper.newInstance(SimpleUuidGenerator.class, UuidGenerator.class);

        assertThat(generator.generateUuid()).isEqualTo("1");
    }

    @Test
    void invokeMethodSupportsParametersAndNullParameterArray() throws NoSuchMethodException {
        Method resolveScheme = ExchangeHelper.class.getMethod("resolveScheme", String.class);
        Object scheme = ObjectHelper.invokeMethod(resolveScheme, null, "direct:start");

        Method generateUuid = SimpleUuidGenerator.class.getMethod("generateUuid");
        SimpleUuidGenerator generator = new SimpleUuidGenerator();
        Object generated = ObjectHelper.invokeMethod(generateUuid, generator, (Object[]) null);

        assertThat(scheme).isEqualTo("direct");
        assertThat(generated).isEqualTo("1");
    }

    @Test
    void invokeMethodSafeSupportsParametersAndNullParameterArray() throws Exception {
        Method resolveScheme = ExchangeHelper.class.getMethod("resolveScheme", String.class);
        Object scheme = ObjectHelper.invokeMethodSafe(resolveScheme, null, "seda:work");

        Method generateUuid = SimpleUuidGenerator.class.getMethod("generateUuid");
        SimpleUuidGenerator generator = new SimpleUuidGenerator();
        Object generated = ObjectHelper.invokeMethodSafe(generateUuid, generator, (Object[]) null);

        assertThat(scheme).isEqualTo("seda");
        assertThat(generated).isEqualTo("1");
    }
}
