/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpResponse;
import com.azure.core.implementation.ReflectiveInvoker;
import com.azure.core.implementation.http.rest.ResponseExceptionConstructorCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ResponseExceptionConstructorCacheTest {
    @Test
    void locatesTheConstructorForAStronglyTypedResponseException() {
        ReflectiveInvoker constructor =
                new ResponseExceptionConstructorCache().get(WidgetException.class, Problem.class);

        assertThat(constructor.getParameterCount()).isEqualTo(3);
    }

    public static final class WidgetException extends HttpResponseException {
        public WidgetException(String message, HttpResponse response, Problem problem) {
            super(message, response, problem);
        }
    }

    public static final class Problem {
        public String code;
    }
}
