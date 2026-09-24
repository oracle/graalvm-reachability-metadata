/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Locale;

import org.apache.catalina.util.CharsetMapper;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(2)
public class CharsetMapperTest {

    @Test
    void mapsDefaultEnglishLocaleToIso88591() {
        CharsetMapper mapper = new CharsetMapper();

        assertThat(mapper.getCharset(Locale.ENGLISH)).isEqualTo("ISO-8859-1");
    }
}
