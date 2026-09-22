/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds.oauth2_oidc_sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.openid.connect.sdk.assurance.claims.ISO3166_1Alpha2CountryCode;
import org.junit.jupiter.api.Test;

public class ISO3166_1Alpha2CountryCodeTest {
    @Test
    void resolvesCountryNameFromBundledRegistry() throws ParseException {
        ISO3166_1Alpha2CountryCode countryCode = ISO3166_1Alpha2CountryCode.parse("de");

        assertEquals("DE", countryCode.getValue());
        assertEquals("Germany", countryCode.getCountryName());
    }
}
