/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds.oauth2_oidc_sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.openid.connect.sdk.assurance.claims.ISO3166_1Alpha3CountryCode;
import org.junit.jupiter.api.Test;

public class ISO3166_1Alpha3CountryCodeTest {
    @Test
    void resolvesCountryNameFromBundledRegistry() throws ParseException {
        ISO3166_1Alpha3CountryCode countryCode = ISO3166_1Alpha3CountryCode.parse("deu");

        assertEquals("DEU", countryCode.getValue());
        assertEquals("Germany", countryCode.getCountryName());
        assertEquals("DE", countryCode.toAlpha2CountryCode().getValue());
    }
}
