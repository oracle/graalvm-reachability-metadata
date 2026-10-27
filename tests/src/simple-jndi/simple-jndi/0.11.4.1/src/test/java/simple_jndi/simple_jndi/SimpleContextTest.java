/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package simple_jndi.simple_jndi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osjava.sj.loader.SJDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Loads one simple-jndi root directory of property files and checks the objects it binds.
public class SimpleContextTest {

    @TempDir
    static Path root;

    private static Context context;

    @BeforeAll
    static void loadRoot() throws IOException, NamingException {
        write("config.properties", """
                name=orders
                retries=3
                retries.type=java.lang.Integer
                retries.converter=org.osjava.sj.loader.convert.ConstructorConverter
                enabled=true
                enabled.type=java.lang.Boolean
                launch=2024-01-15
                launch.type=java.util.Date
                launch.format=yyyy-MM-dd
                """);
        write("components.properties", """
                owner.type=simple_jndi.simple_jndi.Person
                owner.converter=org.osjava.sj.loader.convert.BeanConverter
                owner.name=Arthur
                owner.answer=42
                owner.nickname=Art
                owner.nickname=Dent
                limits.type=java.util.TreeMap
                limits.converter=org.osjava.sj.loader.convert.MapConverter
                limits.daily=100
                limits.weekly=500
                """);
        write("jdbc.properties", """
                orders.type=javax.sql.DataSource
                orders.driver=simple_jndi.simple_jndi.RecordingDriver
                orders.url=jdbc:recording:orders
                orders.user=app
                orders.password=secret
                """);

        Properties environment = new Properties();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "org.osjava.sj.SimpleContextFactory");
        environment.put("org.osjava.sj.root", root.toString());
        environment.put("org.osjava.sj.delimiter", ".");
        context = new InitialContext(environment);
    }

    @Test
    void convertsTypedValuesWithConstructorConverters() throws Exception {
        assertThat(context.lookup("config.name")).isEqualTo("orders");
        assertThat(context.lookup("config.retries")).isEqualTo(3);
        assertThat(context.lookup("config.enabled")).isEqualTo(Boolean.TRUE);
        assertThat((Date) context.lookup("config.launch"))
                .isEqualTo(new SimpleDateFormat("yyyy-MM-dd").parse("2024-01-15"));
    }

    @Test
    void buildsBeansAndMapsWithNamedConverters() throws NamingException {
        Person owner = (Person) context.lookup("components.owner");
        assertThat(owner.getName()).isEqualTo("Arthur");
        assertThat(owner.getAnswer()).isEqualTo(42);
        assertThat(owner.getNicknames()).containsExactly("Art", "Dent");

        @SuppressWarnings("unchecked")
        Map<String, String> limits = (Map<String, String>) context.lookup("components.limits");
        assertThat(limits).isInstanceOf(TreeMap.class)
                .containsEntry("daily", "100")
                .containsEntry("weekly", "500");
    }

    @Test
    void createsDataSourceThatLoadsConfiguredDriver() throws Exception {
        DataSource dataSource = (DataSource) context.lookup("jdbc.orders");

        assertThat(dataSource).isInstanceOf(SJDataSource.class);
        assertThat(DriverManager.getDriver("jdbc:recording:orders")).isInstanceOf(RecordingDriver.class);
        assertThatThrownBy(dataSource::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessage("Connection requested for jdbc:recording:orders as app");
    }

    @Test
    void exposesPropertyFilesAsSubcontexts() throws NamingException {
        assertThat(context.lookup("config")).isInstanceOf(Context.class);
        assertThat(((Context) context.lookup("config")).lookup("name")).isEqualTo("orders");
        assertThat(context.lookup("config.missing")).isNull();
    }

    private static void write(String fileName, String content) throws IOException {
        Files.writeString(root.resolve(fileName), content, StandardCharsets.UTF_8);
    }
}
