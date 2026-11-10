/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.control.AbstractFallbackRequestAndResponseControlDirContextProcessor;
import org.springframework.ldap.control.SortControlDirContextProcessor;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

import javax.naming.directory.SearchControls;
import javax.naming.ldap.Control;
import javax.naming.ldap.SortResponseControl;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractFallbackRequestAndResponseControlDirContextProcessorTest {
    @Test
    void sortsSearchResultsWithTheStandardControls() throws Exception {
        try (LdapServer server = new LdapServer()) {
            SortControlDirContextProcessor processor = new SortControlDirContextProcessor("cn");
            SearchControls search = new SearchControls();
            search.setSearchScope(SearchControls.SUBTREE_SCOPE);
            search.setTimeLimit(10000);
            List<String> names = new LdapTemplate(server.contextSource()).search("", "(objectclass=person)", search,
                    (AttributesMapper<String>) attributes -> (String) attributes.get("cn").get(), processor);
            assertThat(names).containsExactly("Ada", "Grace");
            assertThat(processor.isSorted()).isTrue();
            assertThat(processor.getResultCode()).isZero();
        }
    }

    @Test
    void usesConfiguredFallbackControlImplementations() throws Exception {
        try (LdapServer server = new LdapServer()) {
            CompatibleSortProcessor processor = new CompatibleSortProcessor();
            SearchControls search = new SearchControls();
            search.setSearchScope(SearchControls.SUBTREE_SCOPE);
            search.setTimeLimit(10000);
            List<String> names = new LdapTemplate(server.contextSource()).search("", "(objectclass=person)", search,
                    (AttributesMapper<String>) attributes -> (String) attributes.get("cn").get(), processor);
            assertThat(names).containsExactly("Ada", "Grace");
            assertThat(processor.sorted).isTrue();
        }
    }

    private static final class CompatibleSortProcessor
            extends AbstractFallbackRequestAndResponseControlDirContextProcessor {
        private boolean sorted;

        private CompatibleSortProcessor() {
            defaultRequestControl = "com.sun.jndi.ldap.ctl.SortControl";
            defaultResponseControl = "com.sun.jndi.ldap.ctl.SortResponseControl";
            fallbackRequestControl = "javax.naming.ldap.SortControl";
            fallbackResponseControl = "javax.naming.ldap.SortResponseControl";
            loadControlClasses();
        }

        @Override
        public Control createRequestControl() {
            return createRequestControl(new Class<?>[]{String[].class, boolean.class},
                    new Object[]{new String[]{"cn"}, true});
        }

        @Override
        protected void handleResponse(Object control) {
            sorted = ((SortResponseControl) control).isSorted();
        }
    }
}
