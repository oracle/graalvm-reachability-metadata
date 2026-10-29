/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.util.List;
import java.util.SortedSet;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.jboss.resteasy.annotations.Separator;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.plugins.providers.MultiValuedParamConverterProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiValuedCollectionParamConverterTest {
    @Test
    void constructsSeparatedListsAndSortedSets() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getProviderFactory().register(MultiValuedParamConverterProvider.class);
            session.dispatcher.getRegistry().addPerRequestResource(CollectionResource.class);
            MockHttpResponse response = session.get("/collections?names=oak;elm;oak&ids=7,2,7");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("oak/elm/oak:[2, 7]");
        }
    }

    @Path("/collections")
    public static class CollectionResource {
        @GET
        public String get(@Separator(";") @QueryParam("names") List<String> names,
                @Separator @QueryParam("ids") SortedSet<Integer> ids) {
            return String.join("/", names) + ":" + ids;
        }
    }
}
