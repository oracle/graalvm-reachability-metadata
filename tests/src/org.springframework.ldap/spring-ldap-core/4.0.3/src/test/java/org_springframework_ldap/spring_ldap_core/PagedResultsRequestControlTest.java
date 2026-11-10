/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.control.PagedResultsRequestControl;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.support.SingleContextSource;

import javax.naming.directory.SearchControls;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PagedResultsRequestControlTest {
    @Test
    void readsTwoPagesUsingTheServersContinuationCookie() throws Exception {
        try (LdapServer server = new LdapServer()) {
            List<String> names = SingleContextSource.doWithSingleContext(server.contextSource(), operations -> {
                SearchControls search = new SearchControls();
                search.setSearchScope(SearchControls.SUBTREE_SCOPE);
                search.setTimeLimit(10000);
                AttributesMapper<String> mapper = attributes -> (String) attributes.get("cn").get();
                PagedResultsRequestControl first = new PagedResultsRequestControl(1);
                List<String> pageOne = operations.search("", "(objectclass=person)", search, mapper, first);
                assertThat(pageOne).hasSize(1);
                assertThat(first.getCookie().getCookie()).isNotEmpty();
                PagedResultsRequestControl second = new PagedResultsRequestControl(1, first.getCookie());
                List<String> pageTwo = operations.search("", "(objectclass=person)", search, mapper, second);
                assertThat(pageTwo).hasSize(1);
                assertThat(second.getCookie().getCookie()).isNullOrEmpty();
                List<String> combined = new ArrayList<>(pageOne);
                combined.addAll(pageTwo);
                return combined;
            });
            assertThat(names).containsExactlyInAnyOrder("Ada", "Grace");
        }
    }
}
