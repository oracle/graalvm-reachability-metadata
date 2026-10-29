/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.util.Arrays;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.jboss.resteasy.annotations.Separator;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.plugins.providers.MultiValuedParamConverterProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiValuedArrayParamConverterTest {
    @Test
    void convertsSeparatedPrimitiveAndObjectArrayParameters() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getProviderFactory().register(MultiValuedParamConverterProvider.class);
            session.dispatcher.getRegistry().addPerRequestResource(ArrayResource.class);
            MockHttpResponse response = session.get("/arrays?numbers=2,3,7&words=oak;elm");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("12:oak,elm");
        }
    }

    @Path("/arrays")
    public static class ArrayResource {
        @GET
        public String get(@Separator @QueryParam("numbers") int[] numbers,
                @Separator(";") @QueryParam("words") String[] words) {
            return Arrays.stream(numbers).sum() + ":" + String.join(",", words);
        }
    }
}
