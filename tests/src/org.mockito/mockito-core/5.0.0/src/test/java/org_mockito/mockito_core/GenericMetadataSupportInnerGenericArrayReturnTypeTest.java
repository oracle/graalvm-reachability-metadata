/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericMetadataSupportInnerGenericArrayReturnTypeTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void deepStubsResolveRawTypeForGenericArrayReturnValue() {
        GenericArrayService service = Mockito.mock(GenericArrayService.class, Mockito.RETURNS_DEEP_STUBS);

        List<String>[] values = service.values();

        assertThat(values).isNull();
        assertThat(Mockito.mockingDetails(service).getInvocations()).hasSize(1);
    }

    public interface GenericArrayService {
        List<String>[] values();
    }
}
