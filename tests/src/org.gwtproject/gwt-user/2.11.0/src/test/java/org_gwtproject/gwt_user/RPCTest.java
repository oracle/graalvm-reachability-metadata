/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.impl.AbstractSerializationStream;
import com.google.gwt.user.server.rpc.RPC;
import com.google.gwt.user.server.rpc.RPCRequest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RPCTest {
    private static final String MODULE_BASE_URL = "module";
    private static final String STRONG_NAME = "policy";

    @Test
    void decodesRpcRequestAndInvokesResolvedServiceMethod() throws Exception {
        String encodedRequest = rpcRequest(NoArgumentService.class, "markCalled");

        RPCRequest request = RPC.decodeRequest(encodedRequest, NoArgumentServiceImpl.class);
        NoArgumentServiceImpl service = new NoArgumentServiceImpl();
        String encodedResponse = RPC.invokeAndEncodeResponse(service, request.getMethod(),
                request.getParameters(), request.getSerializationPolicy());

        assertThat(request.getMethod().getName()).isEqualTo("markCalled");
        assertThat(request.getParameters()).isEmpty();
        assertThat(service.isCalled()).isTrue();
        assertThat(encodedResponse).startsWith("//OK");
    }

    private static String rpcRequest(Class<? extends RemoteService> serviceInterface,
            String methodName) {
        List<String> stringTable = new ArrayList<>();
        int moduleIndex = addString(stringTable, MODULE_BASE_URL);
        int strongNameIndex = addString(stringTable, STRONG_NAME);
        int serviceIndex = addString(stringTable, serviceInterface.getName());
        int methodIndex = addString(stringTable, methodName);

        StringBuilder payload = new StringBuilder();
        append(payload, String.valueOf(AbstractSerializationStream.SERIALIZATION_STREAM_VERSION));
        append(payload, String.valueOf(AbstractSerializationStream.DEFAULT_FLAGS));
        append(payload, String.valueOf(stringTable.size()));
        for (String tableEntry : stringTable) {
            append(payload, tableEntry);
        }
        append(payload, String.valueOf(moduleIndex));
        append(payload, String.valueOf(strongNameIndex));
        append(payload, String.valueOf(serviceIndex));
        append(payload, String.valueOf(methodIndex));
        append(payload, "0");
        return payload.toString();
    }

    private static int addString(List<String> stringTable, String value) {
        stringTable.add(value);
        return stringTable.size();
    }

    private static void append(StringBuilder payload, String token) {
        payload.append(token).append(AbstractSerializationStream.RPC_SEPARATOR_CHAR);
    }

    public interface NoArgumentService extends RemoteService {
        void markCalled();
    }

    private static final class NoArgumentServiceImpl implements NoArgumentService {
        private boolean called;

        @Override
        public void markCalled() {
            called = true;
        }

        boolean isCalled() {
            return called;
        }
    }
}
