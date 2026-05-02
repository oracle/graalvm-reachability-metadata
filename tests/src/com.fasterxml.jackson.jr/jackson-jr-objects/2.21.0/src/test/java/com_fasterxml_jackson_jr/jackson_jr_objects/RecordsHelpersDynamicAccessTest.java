/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.impl.RecordsHelpers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordsHelpersDynamicAccessTest {
    private static final JSON JSON_WITH_RECORD_DECLARATION_ORDER = JSON.std.with(
            JSON.Feature.WRITE_RECORD_FIELDS_IN_DECLARATION_ORDER);

    @Test
    void findsCanonicalConstructorFromRecordComponents() {
        assertThat(RecordsHelpers.findCanonicalConstructor(ArticleRecord.class)).isNotNull();
    }

    @Test
    void deserializesRecordUsingCanonicalConstructorComponents() throws Exception {
        ArticleRecord article = JSON.std.beanFrom(ArticleRecord.class,
                """
                {"published":true,"pageCount":42,"title":"GraalVM"}
                """);

        assertThat(article.title()).isEqualTo("GraalVM");
        assertThat(article.pageCount()).isEqualTo(42);
        assertThat(article.published()).isTrue();
    }

    @Test
    void serializesRecordInDeclarationOrderWhenFeatureIsEnabled() throws Exception {
        String json = JSON_WITH_RECORD_DECLARATION_ORDER.asString(new ArticleRecord("Native Image", 7, false));

        assertThat(json).isEqualTo("{\"title\":\"Native Image\",\"pageCount\":7,\"published\":false}");
    }

    public record ArticleRecord(String title, int pageCount, boolean published) {
    }
}
