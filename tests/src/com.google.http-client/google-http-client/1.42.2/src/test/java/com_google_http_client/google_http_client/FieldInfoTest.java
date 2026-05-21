/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_http_client.google_http_client;

import com.google.api.client.json.rpc2.JsonRpcRequest;
import com.google.api.client.util.ClassInfo;
import com.google.api.client.util.FieldInfo;
import com.google.api.client.util.GenericData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FieldInfoTest {

    @Test
    public void enumFieldInfoUsesEnumConstantLookup() {
        assertThatThrownBy(() -> FieldInfo.of(GenericData.Flags.IGNORE_CASE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enum constant missing @Value or @NullValue annotation");
    }

    @Test
    public void setValueInvokesMatchingSetterMethod() {
        JsonRpcRequest request = new JsonRpcRequest();
        FieldInfo fieldInfo = ClassInfo.of(JsonRpcRequest.class).getFieldInfo("method");

        fieldInfo.setValue(request, "rpc.method");

        assertThat(request.getMethod()).isEqualTo("rpc.method");
        assertThat(fieldInfo.getValue(request)).isEqualTo("rpc.method");
    }

    @Test
    public void setValueFallsBackToAnnotatedFieldAssignment() {
        JsonRpcRequest request = new JsonRpcRequest();
        FieldInfo fieldInfo = ClassInfo.of(JsonRpcRequest.class).getFieldInfo("params");

        fieldInfo.setValue(request, "request-parameters");

        assertThat(request.getParameters()).isEqualTo("request-parameters");
        assertThat(fieldInfo.getValue(request)).isEqualTo("request-parameters");
    }
}
