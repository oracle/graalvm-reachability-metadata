/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds.content_type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.nimbusds.common.contenttype.ContentType;
import com.nimbusds.common.contenttype.ContentType.Parameter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Content_typeTest {
    @Test
    void parsesParameterizedContentType() throws ParseException {
        ContentType contentType =
                ContentType.parse(" application/entity-statement+jwt ; charset=UTF-8; profile=example ");

        assertThat(contentType.getBaseType()).isEqualTo("application");
        assertThat(contentType.getSubType()).isEqualTo("entity-statement+jwt");
        assertThat(contentType.getBaseSubType()).isEqualTo("entity-statement");
        assertThat(contentType.getSubTypeSuffix()).isEqualTo("jwt");
        assertThat(contentType.hasSubTypeSuffix("jwt")).isTrue();
        assertThat(contentType.getType()).isEqualTo("application/entity-statement+jwt");
        assertThat(contentType.getParameters())
                .containsExactly(new Parameter("charset", "UTF-8"), new Parameter("profile", "example"));
        assertThat(contentType.toString())
                .isEqualTo("application/entity-statement+jwt; charset=UTF-8; profile=example");
    }

    @Test
    void handlesContentTypeWithoutSuffixOrParameters() throws ParseException {
        ContentType contentType = ContentType.parse("image/png");

        assertThat(contentType.getBaseSubType()).isEqualTo("png");
        assertThat(contentType.getSubTypeSuffix()).isNull();
        assertThat(contentType.hasSubTypeSuffix("png")).isFalse();
        assertThat(contentType.hasSubTypeSuffix(null)).isFalse();
        assertThat(contentType.getParameters()).isEmpty();
        assertThat(contentType).isEqualTo(ContentType.IMAGE_PNG);
    }

    @Test
    void constructsCharsetContentTypeAndMatchesIgnoringCase() {
        ContentType contentType = new ContentType("Application", "JSON", StandardCharsets.UTF_8);
        ContentType sameTypeWithDifferentParameter =
                new ContentType("application", "json", new Parameter("profile", "example"));

        assertThat(contentType.getParameters()).containsExactly(Parameter.CHARSET_UTF_8);
        assertThat(contentType.matches(sameTypeWithDifferentParameter)).isTrue();
        assertThat(contentType.matches(new ContentType("application", "xml"))).isFalse();
        assertThat(contentType.matches(ContentType.TEXT_PLAIN)).isFalse();
        assertThat(contentType.matches(null)).isFalse();
        assertThat(contentType).isEqualTo(ContentType.APPLICATION_JSON);
        assertThat(contentType).isNotEqualTo(sameTypeWithDifferentParameter);
        assertThat(contentType).isNotEqualTo(null);
        assertThat(contentType).isNotEqualTo("application/json; charset=UTF-8");
        assertThat(contentType.hashCode()).isEqualTo(ContentType.APPLICATION_JSON.hashCode());
    }

    @Test
    void exposesPredefinedCommonMediaTypes() {
        assertThat(
                        List.of(
                                ContentType.APPLICATION_JOSE,
                                ContentType.APPLICATION_JWT,
                                ContentType.APPLICATION_URLENCODED,
                                ContentType.IMAGE_APNG,
                                ContentType.IMAGE_AVIF,
                                ContentType.IMAGE_GIF,
                                ContentType.IMAGE_JPEG,
                                ContentType.IMAGE_SVG_XML,
                                ContentType.IMAGE_WEBP,
                                ContentType.APPLICATION_PDF))
                .extracting(ContentType::toString)
                .containsExactly(
                        "application/jose; charset=UTF-8",
                        "application/jwt; charset=UTF-8",
                        "application/x-www-form-urlencoded; charset=UTF-8",
                        "image/apng",
                        "image/avif",
                        "image/gif",
                        "image/jpeg",
                        "image/svg+xml",
                        "image/webp",
                        "application/pdf");
    }

    @Test
    void comparesParametersIgnoringCase() {
        Parameter upperCase = new Parameter("CHARSET", "UTF-8");
        Parameter lowerCase = new Parameter("charset", "utf-8");

        assertThat(upperCase.getName()).isEqualTo("CHARSET");
        assertThat(upperCase.getValue()).isEqualTo("UTF-8");
        assertThat(upperCase.toString()).isEqualTo("CHARSET=UTF-8");
        assertThat(upperCase).isEqualTo(lowerCase);
        assertThat(upperCase).isNotEqualTo(new Parameter("charset", "US-ASCII"));
        assertThat(upperCase).isNotEqualTo(null);
        assertThat(upperCase).isNotEqualTo("charset=UTF-8");
        assertThat(upperCase.hashCode()).isEqualTo(lowerCase.hashCode());
    }

    @Test
    void exposesParametersAsUnmodifiableList() {
        ContentType contentType =
                new ContentType("application", "problem+json", new Parameter("profile", "validation"));

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(
                        () ->
                                contentType
                                        .getParameters()
                                        .add(new Parameter("charset", "UTF-8")));
        assertThat(contentType.toString()).isEqualTo("application/problem+json; profile=validation");
    }

    @Test
    void rejectsInvalidContentTypesAndParameters() {
        assertThatExceptionOfType(ParseException.class).isThrownBy(() -> ContentType.parse(null));
        assertThatExceptionOfType(ParseException.class).isThrownBy(() -> ContentType.parse(" "));
        assertThatExceptionOfType(ParseException.class).isThrownBy(() -> ContentType.parse("application"));
        assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> ContentType.parse("application/json; charset"));
        assertThatExceptionOfType(ParseException.class)
                .isThrownBy(() -> ContentType.parse("application/json; charset="));
        assertThatIllegalArgumentException().isThrownBy(() -> new ContentType(null, "json"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ContentType(" ", "json"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ContentType("application", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ContentType("application", " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Parameter(null, "value"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Parameter("", "value"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Parameter("name", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Parameter("name", ""));
    }
}
