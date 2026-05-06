/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15on;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.oer.its.Latitude;
import org.bouncycastle.oer.its.Longitude;
import org.bouncycastle.oer.its.PolygonalRegion;
import org.bouncycastle.oer.its.RectangularRegion;
import org.bouncycastle.oer.its.SequenceOfRectangularRegion;
import org.bouncycastle.oer.its.TwoDLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsAnonymous1Test {
    @Test
    void polygonalRegionDecodingUsesGetInstanceForEachSequenceElement() throws ReflectiveOperationException {
        List<TwoDLocation> locations = Arrays.asList(
            location(100, 200),
            location(300, 400),
            location(500, 600));
        PolygonalRegion originalRegion = new PolygonalRegion(locations);
        Method factory = TwoDLocation.class.getMethod("getInstance", Object.class);
        assertThat(factory.invoke(null, locations.get(0).toASN1Primitive())).isInstanceOf(TwoDLocation.class);

        ASN1Primitive encodedRegion = originalRegion.toASN1Primitive();
        PolygonalRegion decodedRegion = PolygonalRegion.getInstance(encodedRegion);

        assertThat(decodedRegion.getPoints()).hasSize(3);
        assertThat(decodedRegion.getPoints())
            .extracting(point -> point.getLatitude().getValue())
            .containsExactly(BigInteger.valueOf(100), BigInteger.valueOf(300), BigInteger.valueOf(500));
        assertThat(decodedRegion.getPoints())
            .extracting(point -> point.getLongitude().getValue())
            .containsExactly(BigInteger.valueOf(200), BigInteger.valueOf(400), BigInteger.valueOf(600));
    }

    @Test
    void rectangularRegionSequenceDecodingUsesGetInstanceForEachSequenceElement() throws ReflectiveOperationException {
        List<RectangularRegion> regions = Arrays.asList(
            new RectangularRegion(location(10, 20), location(30, 40)),
            new RectangularRegion(location(50, 60), location(70, 80)));
        SequenceOfRectangularRegion originalSequence = new SequenceOfRectangularRegion(regions);
        Method factory = RectangularRegion.class.getMethod("getInstance", Object.class);
        assertThat(factory.invoke(null, regions.get(0).toASN1Primitive())).isInstanceOf(RectangularRegion.class);

        ASN1Primitive encodedSequence = originalSequence.toASN1Primitive();
        SequenceOfRectangularRegion decodedSequence = SequenceOfRectangularRegion.getInstance(encodedSequence);

        assertThat(decodedSequence.getRectangularRegions()).hasSize(2);
        assertThat(decodedSequence.getRectangularRegions())
            .extracting(region -> region.getNorthWest().getLatitude().getValue())
            .containsExactly(BigInteger.valueOf(10), BigInteger.valueOf(50));
        assertThat(decodedSequence.getRectangularRegions())
            .extracting(region -> region.getSouthEast().getLongitude().getValue())
            .containsExactly(BigInteger.valueOf(40), BigInteger.valueOf(80));
    }

    private static TwoDLocation location(long latitude, long longitude) {
        return new TwoDLocation(new Latitude(latitude), new Longitude(longitude));
    }
}
