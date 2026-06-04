/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class InvocationInfoTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void acceptsCheckedExceptionDeclaredByParentInterfaceMethod() {
        ChildService service = Mockito.mock(ChildService.class);
        IOException failure = new IOException("parent contract exception");

        Mockito.when(service.read()).thenThrow(failure);

        assertThat(Mockito.mockingDetails(service).getStubbings()).hasSize(1);
    }

    public interface ParentService {
        String read() throws IOException;
    }

    public interface ChildService extends ParentService {
        @Override
        String read();
    }
}
