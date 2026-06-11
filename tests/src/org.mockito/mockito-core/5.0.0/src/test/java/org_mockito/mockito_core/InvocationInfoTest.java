/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InvocationInfoTest {
    @Test
    void checkedExceptionDeclaredOnParentInterfaceCanBeStubbedOnChildMock() throws Exception {
        try {
            ChildApi child =
                    Mockito.mock(
                            ChildApi.class,
                            Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
            IOException failure = new IOException("declared on parent");

            Mockito.doThrow(failure).when(child).risky();

            assertThatThrownBy(((ParentApi) child)::risky).isSameAs(failure);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public interface ParentApi {
        void risky() throws IOException;
    }

    public interface ChildApi extends ParentApi {
        @Override
        void risky();
    }
}
