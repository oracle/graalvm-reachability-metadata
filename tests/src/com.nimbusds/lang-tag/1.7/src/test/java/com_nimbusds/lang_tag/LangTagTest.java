/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds.lang_tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.langtag.LangTag;
import com.nimbusds.langtag.LangTagException;
import com.nimbusds.langtag.LangTagUtils;
import com.nimbusds.langtag.ReadOnlyLangTag;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class LangTagTest {
    @Test
    void parsesAndNormalizesACompleteLanguageTag() throws LangTagException {
        LangTag tag = LangTag.parse("ZH-cmn-HANS-cn-1901-a-EXT-x-private");

        assertThat(tag.getLanguage()).isEqualTo("zh-cmn");
        assertThat(tag.getPrimaryLanguage()).isEqualTo("zh");
        assertThat(tag.getExtendedLanguageSubtags()).containsExactly("cmn");
        assertThat(tag.getScript()).isEqualTo("Hans");
        assertThat(tag.getRegion()).isEqualTo("CN");
        assertThat(tag.getVariants()).containsExactly("1901");
        assertThat(tag.getExtensions()).containsExactly("a-ext");
        assertThat(tag.getPrivateUse()).isEqualTo("x-private");
        assertThat(tag).hasToString("zh-cmn-Hans-CN-1901-a-ext-x-private");
    }

    @Test
    void constructsAndMutatesLanguageTagComponents() throws LangTagException {
        LangTag tag = new LangTag("EN");
        tag.setScript("latn");
        tag.setRegion("us");
        tag.setVariants("POSIX", "1996");
        tag.setExtensions("U-CA", "t-ES");
        tag.setPrivateUse("x-CUSTOM");

        ReadOnlyLangTag readOnlyTag = tag;
        assertThat(readOnlyTag).hasToString("en-Latn-US-posix-1996-u-ca-t-es-x-custom");
        assertThat(tag).isEqualTo(LangTag.parse("en-Latn-US-posix-1996-u-ca-t-es-x-custom"));
        assertThat(tag.hashCode()).isEqualTo(tag.toString().hashCode());

        tag.setScript(null);
        tag.setRegion(null);
        tag.setVariants();
        tag.setExtensions();
        tag.setPrivateUse(null);
        assertThat(tag).hasToString("en");
        assertThat(tag.getScript()).isNull();
        assertThat(tag.getRegion()).isNull();
        assertThat(tag.getVariants()).isNull();
        assertThat(tag.getExtensions()).isNull();
        assertThat(tag.getPrivateUse()).isNull();
    }

    @Test
    void supportsExtendedLanguageWithoutPrimaryLanguage() throws LangTagException {
        LangTag tag = new LangTag(null, "CMN", "YUE");

        assertThat(tag.getPrimaryLanguage()).isNull();
        assertThat(tag.getExtendedLanguageSubtags()).containsExactly("cmn", "yue");
        assertThat(tag.getLanguage()).isEqualTo("cmn-yue");
    }

    @Test
    void supportsNumericRegionCodes() throws LangTagException {
        LangTag tag = LangTag.parse("es-419");

        assertThat(tag.getPrimaryLanguage()).isEqualTo("es");
        assertThat(tag.getRegion()).isEqualTo("419");
        assertThat(tag).hasToString("es-419");
    }

    @Test
    void stripsAndExtractsLanguageTagFragments() throws LangTagException {
        assertThat(LangTagUtils.strip("https://example.test/content#en-US"))
                .isEqualTo("https://example.test/content");
        assertThat(LangTagUtils.strip("plain-value")).isEqualTo("plain-value");
        assertThat(LangTagUtils.strip(List.of("first#en", "second", "#fr")))
                .containsExactly("first", "second", "");
        assertThat(LangTagUtils.strip(Set.of("first#en", "second#fr")))
                .containsExactlyInAnyOrder("first", "second");

        assertThat(LangTagUtils.extract("https://example.test/content#SR-latn-rs"))
                .isEqualTo(LangTag.parse("sr-Latn-RS"));
        assertThat(LangTagUtils.extract("https://example.test/content")).isNull();
    }

    @Test
    void findsValuesByBaseIdentifierAndLanguageFragment() throws LangTagException {
        Map<String, String> localizedValues = new LinkedHashMap<>();
        localizedValues.put("title", "Default title");
        localizedValues.put("title#en-US", "English title");
        localizedValues.put("title#de-DE", "Deutscher Titel");
        localizedValues.put("summary#en-US", "English summary");

        Map<LangTag, String> matches = LangTagUtils.find("title", localizedValues);

        assertThat(matches)
                .hasSize(3)
                .containsEntry(null, "Default title")
                .containsEntry(LangTag.parse("en-US"), "English title")
                .containsEntry(LangTag.parse("de-DE"), "Deutscher Titel");
    }

    @Test
    void splitsIdentifiersAndConvertsLanguageTagCollections() throws LangTagException {
        Map.Entry<String, LangTag> split = LangTagUtils.split("headline#fr-CA");
        assertThat(split.getKey()).isEqualTo("headline");
        assertThat(split.getValue()).isEqualTo(LangTag.parse("fr-CA"));

        Map.Entry<String, LangTag> untagged = LangTagUtils.split("headline");
        assertThat(untagged.getKey()).isEqualTo("headline");
        assertThat(untagged.getValue()).isNull();

        List<LangTag> tags = LangTagUtils.parseLangTagList("en-US fr-CA");
        assertThat(LangTagUtils.toStringList(tags)).containsExactly("en-US", "fr-CA");
        assertThat(LangTagUtils.toStringArray(tags)).containsExactly("en-US", "fr-CA");
        assertThat(LangTagUtils.concat(tags)).isEqualTo("en-US fr-CA");
    }

    @Test
    void parsesCollectionVarargsAndArrayInputs() throws LangTagException {
        assertThat(LangTagUtils.parseLangTagList(List.of("de-DE", "it-IT")))
                .extracting(LangTag::toString)
                .containsExactly("de-DE", "it-IT");
        assertThat(LangTagUtils.parseLangTagList("ja-JP", "ko-KR"))
                .extracting(LangTag::toString)
                .containsExactly("ja-JP", "ko-KR");
        assertThat(LangTagUtils.parseLangTagArray("es-ES", "pt-BR"))
                .extracting(LangTag::toString)
                .containsExactly("es-ES", "pt-BR");
        assertThat(LangTagUtils.concat(Arrays.asList(LangTag.parse("nl-NL"), null, LangTag.parse("sv-SE"))))
                .isEqualTo("nl-NL sv-SE");
    }

    @Test
    void rejectsInvalidLanguageTagComponents() throws LangTagException {
        assertThatThrownBy(() -> new LangTag("e")).isInstanceOf(LangTagException.class);
        assertThatThrownBy(() -> new LangTag((String) null)).isInstanceOf(LangTagException.class);
        assertThatThrownBy(() -> LangTag.parse("en-abcdefghijk"))
                .isInstanceOf(LangTagException.class);

        LangTag tag = new LangTag("en");
        assertThatThrownBy(() -> tag.setScript("latin")).isInstanceOf(LangTagException.class);
        assertThatThrownBy(() -> tag.setRegion("USA")).isInstanceOf(LangTagException.class);
        assertThatThrownBy(() -> tag.setVariants("abc")).isInstanceOf(LangTagException.class);
        assertThatThrownBy(() -> tag.setExtensions("x-private"))
                .isInstanceOf(LangTagException.class);
        assertThatThrownBy(() -> tag.setPrivateUse("private"))
                .isInstanceOf(LangTagException.class);
    }
}
