/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.implementation.ReflectiveInvoker;
import com.azure.core.implementation.http.rest.ResponseConstructorsCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ReflectionUtilsMethodHandleTest {
    @Test
    void createsAConstructorInvokerForAResponseType() {
        ReflectiveInvoker constructor = new ResponseConstructorsCache().get(CustomResponse.class);

        assertThat(constructor.getParameterCount()).isEqualTo(4);
    }

    public static final class CustomResponse extends SimpleResponse<String> {
        public CustomResponse(HttpRequest request, int statusCode, HttpHeaders headers, String value) {
            super(request, statusCode, headers, value);
        }
    }
}
