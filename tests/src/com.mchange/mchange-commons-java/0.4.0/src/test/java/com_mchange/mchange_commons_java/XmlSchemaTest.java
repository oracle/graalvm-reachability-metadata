/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v1.db.sql.XmlSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlSchemaTest {
    private static final String SCHEMA_XML = """
            <schema>
                <create>
                    <statement>
                        create table test_widget (id integer primary key, name varchar(64))
                    </statement>
                </create>
                <drop>
                    <statement>
                        drop table test_widget
                    </statement>
                </drop>
                <application name="widgetApp">
                    <statement name="findByName">
                        select id, name from test_widget where name = ?
                    </statement>
                </application>
            </schema>
            """;

    @Test
    void parseLoadsPackagedDtdResourceAndNamedApplicationStatements() throws Exception {
        XmlSchema schema = new XmlSchema(new ByteArrayInputStream(SCHEMA_XML.getBytes(StandardCharsets.UTF_8)));

        assertThat(schema.getStatementText("widgetApp", "findByName"))
                .isEqualTo("select id, name from test_widget where name = ?");
        assertThat(schema.getStatementText("widgetApp", "missingStatement")).isNull();
        assertThat(schema.getStatementText("missingApp", "findByName")).isNull();
    }

    @Test
    void mainLoadsBundledHjugSchemaResource() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            XmlSchema.main(new String[0]);
        } finally {
            System.setErr(originalErr);
        }

        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
    }
}
