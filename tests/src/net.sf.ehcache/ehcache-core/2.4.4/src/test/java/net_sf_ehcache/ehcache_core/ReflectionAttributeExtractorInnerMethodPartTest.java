/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.ehcache.Element;
import net.sf.ehcache.search.attribute.ReflectionAttributeExtractor;

import org.junit.jupiter.api.Test;

public class ReflectionAttributeExtractorInnerMethodPartTest {
    @Test
    void extractsMethodResultFromElementValue() {
        CustomerProfile profile = new CustomerProfile("active-customer");
        Element element = new Element("profile-key", profile, 1L);
        ReflectionAttributeExtractor extractor = new ReflectionAttributeExtractor("value.statusLabel()");

        Object attribute = extractor.attributeFor(element, "statusLabel");

        assertThat(attribute).isEqualTo("profile:active-customer");
    }

    @Test
    void extractsInheritedMethodResultFromElementValue() {
        PreferredCustomerProfile profile = new PreferredCustomerProfile("gold-tier");
        Element element = new Element("profile-key", profile, 1L);
        ReflectionAttributeExtractor extractor = new ReflectionAttributeExtractor("value.tierName()");

        Object attribute = extractor.attributeFor(element, "tierName");

        assertThat(attribute).isEqualTo("gold-tier");
    }

    private static class CustomerProfile {
        private final String status;

        CustomerProfile(String status) {
            this.status = status;
        }

        private String statusLabel() {
            return "profile:" + status;
        }
    }

    private static class BaseCustomerProfile {
        private final String tierName;

        BaseCustomerProfile(String tierName) {
            this.tierName = tierName;
        }

        private String tierName() {
            return tierName;
        }
    }

    private static final class PreferredCustomerProfile extends BaseCustomerProfile {
        PreferredCustomerProfile(String tierName) {
            super(tierName);
        }
    }
}
