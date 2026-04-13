/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_codec.commons_codec;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.language.bm.Lang;
import org.apache.commons.codec.language.bm.NameType;
import org.junit.jupiter.api.Test;

class LangTest {
    @Test
    void loadsGenericLanguageRules() {
        Lang genericLang = Lang.instance(NameType.GENERIC);

        assertThat(genericLang.guessLanguage("ţ")).isEqualTo("romanian");
    }

    @Test
    void loadsAshkenaziLanguageRules() {
        Lang ashkenaziLang = Lang.instance(NameType.ASHKENAZI);

        assertThat(ashkenaziLang.guessLanguage("ß")).isEqualTo("german");
    }

    @Test
    void loadsSephardicLanguageRules() {
        Lang sephardicLang = Lang.instance(NameType.SEPHARDIC);

        assertThat(sephardicLang.guessLanguage("ñ")).isEqualTo("spanish");
    }
}
