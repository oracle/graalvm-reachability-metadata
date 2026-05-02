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

public class ReflectionAttributeExtractorInnerFieldPartTest {
    @Test
    void extractsPrivateFieldFromElementValue() {
        AccountProfile profile = new AccountProfile("active-customer");
        Element element = new Element("profile-key", profile, 1L);
        ReflectionAttributeExtractor extractor = new ReflectionAttributeExtractor("value.status");

        Object attribute = extractor.attributeFor(element, "status");

        assertThat(attribute).isEqualTo("active-customer");
    }

    @Test
    void extractsInheritedPrivateFieldFromElementValue() {
        PremiumAccountProfile profile = new PremiumAccountProfile("gold-tier");
        Element element = new Element("profile-key", profile, 1L);
        ReflectionAttributeExtractor extractor = new ReflectionAttributeExtractor("value.tier");

        Object attribute = extractor.attributeFor(element, "tier");

        assertThat(attribute).isEqualTo("gold-tier");
    }

    private static class AccountProfile {
        private final String status;

        AccountProfile(String status) {
            this.status = status;
        }
    }

    private static class BaseAccountProfile {
        private final String tier;

        BaseAccountProfile(String tier) {
            this.tier = tier;
        }
    }

    private static final class PremiumAccountProfile extends BaseAccountProfile {
        PremiumAccountProfile(String tier) {
            super(tier);
        }
    }
}
