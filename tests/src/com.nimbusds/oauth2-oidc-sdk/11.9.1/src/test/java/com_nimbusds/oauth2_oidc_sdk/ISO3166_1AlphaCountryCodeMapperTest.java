/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds.oauth2_oidc_sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.openid.connect.sdk.assurance.claims.ISO3166_1Alpha2CountryCode;
import com.nimbusds.openid.connect.sdk.assurance.claims.ISO3166_1Alpha3CountryCode;
import com.nimbusds.openid.connect.sdk.assurance.claims.ISO3166_1AlphaCountryCodeMapper;
import org.junit.jupiter.api.Test;

public class ISO3166_1AlphaCountryCodeMapperTest {
    @Test
    void mapsCountryCodesUsingBundledRegistry() {
        ISO3166_1Alpha3CountryCode alpha3Code = ISO3166_1AlphaCountryCodeMapper.toAlpha3CountryCode(
                new ISO3166_1Alpha2CountryCode("DE"));
        ISO3166_1Alpha2CountryCode alpha2Code = ISO3166_1AlphaCountryCodeMapper.toAlpha2CountryCode(alpha3Code);

        assertEquals("DEU", alpha3Code.getValue());
        assertEquals("DE", alpha2Code.getValue());
    }
}
