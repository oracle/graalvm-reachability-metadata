/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy_observation.datasource_micrometer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.observation.tracing.DataSourceBaseContext;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import org.h2.jdbcx.JdbcDataSource;

final class ObservationTestSupport {
    private ObservationTestSupport() {
    }

    static JdbcDataSource database() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:observations_" + UUID.randomUUID());
        return dataSource;
    }

    static ProxyDataSource observe(DataSource dataSource, DataSourceObservationListener listener) {
        return ProxyDataSourceBuilder.create(dataSource)
                .name("inventory")
                .listener(listener)
                .methodListener(listener)
                .proxyResultSet()
                .proxyGeneratedKeys()
                .build();
    }

    static final class RecordingHandler implements ObservationHandler<Observation.Context> {
        final List<Observation.Context> stopped = new ArrayList<>();
        final List<String> events = new ArrayList<>();

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof DataSourceBaseContext;
        }

        @Override
        public void onStop(Observation.Context context) {
            stopped.add(context);
        }

        @Override
        public void onEvent(Observation.Event event, Observation.Context context) {
            events.add(event.getName());
        }

        <T extends Observation.Context> List<T> contexts(Class<T> type) {
            return stopped.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }
}
