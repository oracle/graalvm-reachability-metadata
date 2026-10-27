/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.ehcache.Element;
import net.sf.ehcache.config.SearchAttribute;
import net.sf.ehcache.search.attribute.AttributeExtractor;

import org.junit.jupiter.api.Test;

public class JavaBeanAttributeExtractorTest {
    @Test
    void extractsGetterBeanPropertyFromElementKey() {
        AttributeExtractor extractor = new SearchAttribute().name("title").constructExtractor();
        ArticleKey key = new ArticleKey("Native Image Guide");
        Element element = new Element(key, "stored-value");

        Object title = extractor.attributeFor(element, "title");

        assertThat(title).isEqualTo("Native Image Guide");
    }

    @Test
    void extractsBooleanBeanPropertyFromElementValue() {
        AttributeExtractor extractor = new SearchAttribute().name("published").constructExtractor();
        ArticleValue value = new ArticleValue(true);
        Element element = new Element("article-1", value);

        Object published = extractor.attributeFor(element, "published");

        assertThat(published).isEqualTo(Boolean.TRUE);
    }

    public static final class ArticleKey {
        private final String title;

        ArticleKey(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    public static final class ArticleValue {
        private final boolean published;

        ArticleValue(boolean published) {
            this.published = published;
        }

        public boolean isPublished() {
            return published;
        }
    }
}
