/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import org.jboss.resteasy.plugins.touri.ObjectToURI;
import org.jboss.resteasy.spi.touri.URITemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractURITemplateAnnotationResolverTest {
    @Test
    void expandsUriTemplateUsingBeanGetters() {
        Article article = new Article("native image", 17);
        assertThat(ObjectToURI.getInstance().resolveURI(article)).isEqualTo("/articles/native%20image/17");
    }

    @URITemplate("/articles/{category}/{id}")
    public static class Article {
        private final String category;
        private final int id;

        public Article(String category, int id) {
            this.category = category;
            this.id = id;
        }

        public String getCategory() {
            return category;
        }

        public int getId() {
            return id;
        }
    }
}
