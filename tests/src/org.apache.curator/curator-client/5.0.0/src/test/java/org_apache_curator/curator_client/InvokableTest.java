/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.apache.curator.shaded.com.google.common.reflect.Invokable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InvokableTest {
    @Test
    void invokesMethodsAndConstructorsThroughThePublicInvokableApi() throws Exception {
        Method length = String.class.getMethod("length");
        Constructor<String> constructor = String.class.getConstructor(String.class);

        assertThat(Invokable.from(length).invoke("curator")).isEqualTo(7);
        assertThat(Invokable.from(constructor).invoke("client")).isEqualTo("client");
    }
}
