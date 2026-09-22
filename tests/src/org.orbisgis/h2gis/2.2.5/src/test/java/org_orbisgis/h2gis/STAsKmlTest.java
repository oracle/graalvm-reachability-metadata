/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_orbisgis.h2gis;

import org.h2gis.functions.factory.H2GISFunctions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class STAsKmlTest {

    @Test
    void convertsThreeDimensionalGeometryToKml() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:kml_conversion")) {
            H2GISFunctions.load(connection);

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("""
                            SELECT ST_AsKml(ST_GeomFromText('POINT Z (2 3 4)')) AS basic_kml,
                                   ST_AsKml(ST_GeomFromText('POINT Z (2 3 4)'), TRUE, 4) AS absolute_kml
                            """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("basic_kml"))
                        .isEqualTo("<Point><coordinates>2.0,3.0,4.0</coordinates></Point>");
                assertThat(resultSet.getString("absolute_kml"))
                        .isEqualTo("""
                                <Point><extrude>1</extrude><kml:altitudeMode>absolute</kml:altitudeMode>\
                                <coordinates>2.0,3.0,4.0</coordinates></Point>""");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }
}
