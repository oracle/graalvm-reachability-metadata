/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15on;

import java.util.Arrays;
import java.util.List;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.oer.its.CountryAndRegions;
import org.bouncycastle.oer.its.CountryOnly;
import org.bouncycastle.oer.its.Latitude;
import org.bouncycastle.oer.its.Longitude;
import org.bouncycastle.oer.its.PolygonalRegion;
import org.bouncycastle.oer.its.RectangularRegion;
import org.bouncycastle.oer.its.Region;
import org.bouncycastle.oer.its.SequenceOfRectangularRegion;
import org.bouncycastle.oer.its.TwoDLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsAnonymous1Test {
    @Test
    void parsesPolygonalRegionPointsThroughGetInstanceFactory() {
        PolygonalRegion encodedRegion = new PolygonalRegion(Arrays.asList(
            location(100L, 200L),
            location(300L, 400L),
            location(500L, 600L)));

        PolygonalRegion parsedRegion = PolygonalRegion.getInstance(encodedRegion.toASN1Primitive());

        List<TwoDLocation> parsedPoints = parsedRegion.getPoints();
        assertThat(parsedPoints).hasSize(3);
        assertLocation(parsedPoints.get(0), 100L, 200L);
        assertLocation(parsedPoints.get(1), 300L, 400L);
        assertLocation(parsedPoints.get(2), 500L, 600L);
    }

    @Test
    void parsesCountryAndRegionsThroughGetInstanceFactory() {
        CountryAndRegions encodedRegions = CountryAndRegions.builder()
            .setCountryOnly(new CountryOnly(840))
            .addRegion(new Region(12))
            .addRegion(new Region(34))
            .build();

        CountryAndRegions parsedRegions = CountryAndRegions.getInstance(
            encodedRegions.toASN1Primitive());

        assertThat(asInt(parsedRegions.getCountryOnly())).isEqualTo(840);
        assertThat(parsedRegions.getRegions()).hasSize(2);
        assertThat(asInt(parsedRegions.getRegions().get(0))).isEqualTo(12);
        assertThat(asInt(parsedRegions.getRegions().get(1))).isEqualTo(34);
    }

    @Test
    void parsesSequenceOfRectangularRegionsThroughGetInstanceFactory() {
        SequenceOfRectangularRegion encodedRegions = new SequenceOfRectangularRegion(Arrays.asList(
            new RectangularRegion(location(1L, 2L), location(3L, 4L)),
            new RectangularRegion(location(5L, 6L), location(7L, 8L))));

        SequenceOfRectangularRegion parsedRegions = SequenceOfRectangularRegion.getInstance(
            encodedRegions.toASN1Primitive());

        assertThat(parsedRegions.getRectangularRegions()).hasSize(2);
        RectangularRegion firstRegion = parsedRegions.getRectangularRegions().get(0);
        assertLocation(firstRegion.getNorthWest(), 1L, 2L);
        assertLocation(firstRegion.getSouthEast(), 3L, 4L);
        RectangularRegion secondRegion = parsedRegions.getRectangularRegions().get(1);
        assertLocation(secondRegion.getNorthWest(), 5L, 6L);
        assertLocation(secondRegion.getSouthEast(), 7L, 8L);
    }

    private static TwoDLocation location(long latitude, long longitude) {
        return new TwoDLocation(new Latitude(latitude), new Longitude(longitude));
    }

    private static void assertLocation(TwoDLocation location, long latitude, long longitude) {
        assertThat(location.getLatitude().getValue().longValueExact()).isEqualTo(latitude);
        assertThat(location.getLongitude().getValue().longValueExact()).isEqualTo(longitude);
    }

    private static int asInt(ASN1Encodable value) {
        ASN1Integer integer = ASN1Integer.getInstance(value.toASN1Primitive());
        return integer.getValue().intValueExact();
    }
}
