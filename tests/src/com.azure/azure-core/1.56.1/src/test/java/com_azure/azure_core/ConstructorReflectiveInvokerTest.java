/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpHeaders;
import com.azure.core.implementation.ReflectionUtils;
import com.azure.core.implementation.ReflectiveInvoker;
import com.azure.core.util.serializer.JacksonAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ConstructorReflectiveInvokerTest {
    @Test
    void deserializesHeadersThroughTheStrongTypeConstructor() throws Exception {
        HttpHeaders source = new HttpHeaders().set("etag", "widget-7");

        ConstructedHeaders decoded = new JacksonAdapter().deserialize(source, ConstructedHeaders.class);

        assertThat(decoded.etag).isEqualTo("widget-7");
    }

    @Test
    void invokesConstructorsThroughBothInvokerEntryPoints() throws Exception {
        ReflectiveInvoker constructor = ReflectionUtils.getConstructorInvoker(ArrayConstructed.class,
                ArrayConstructed.class.getConstructor(Object[].class));

        ArrayConstructed staticResult = (ArrayConstructed) constructor.invokeStatic((Object) new Object[] { "azure" });
        ArrayConstructed argumentsResult =
                (ArrayConstructed) constructor.invokeWithArguments(new Object[] { "core" });

        assertThat(staticResult.constructed).isTrue();
        assertThat(argumentsResult.constructed).isTrue();
    }

    public static final class ConstructedHeaders {
        private final String etag;

        public ConstructedHeaders(HttpHeaders headers) {
            this.etag = headers.getValue("etag");
        }
    }

    public static final class ArrayConstructed {
        private final boolean constructed;

        public ArrayConstructed(Object[] values) {
            this.constructed = values != null;
        }
    }
}
