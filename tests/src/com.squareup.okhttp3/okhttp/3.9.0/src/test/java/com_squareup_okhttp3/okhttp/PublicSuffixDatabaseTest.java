/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.internal.publicsuffix.PublicSuffixDatabase;
import org.junit.jupiter.api.Test;

public class PublicSuffixDatabaseTest {
    @Test
    void effectiveTldPlusOneLoadsBundledPublicSuffixResource() {
        PublicSuffixDatabase database = PublicSuffixDatabase.get();

        assertThat(database.getEffectiveTldPlusOne("www.google.com")).isEqualTo("google.com");
        assertThat(database.getEffectiveTldPlusOne("adwords.google.co.uk")).isEqualTo("google.co.uk");
        assertThat(database.getEffectiveTldPlusOne("com")).isNull();
    }
}
