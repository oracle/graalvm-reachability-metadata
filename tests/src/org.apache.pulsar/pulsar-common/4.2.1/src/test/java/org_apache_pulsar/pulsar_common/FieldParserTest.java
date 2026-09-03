/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.pulsar.common.util.FieldParser;
import org.junit.jupiter.api.Test;

public class FieldParserTest {

    @Test
    void updatesDeclaredFieldsFromStringProperties() {
        final FieldParserTarget target = new FieldParserTarget();
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("name", "broker-a");
        properties.put("maxConnections", "25");
        properties.put("timeoutMillis", "123456789");
        properties.put("loadFactor", "0.75");
        properties.put("enabled", "true");
        properties.put("clusters", "alpha, beta, gamma");
        properties.put("ports", "6650, 6651");
        properties.put("roles", "admin, proxy, admin");
        properties.put("weights", "primary=10, secondary=20");
        properties.put("maybePort", "7000");

        FieldParser.update(properties, target);

        assertThat(target.getName()).isEqualTo("broker-a");
        assertThat(target.getMaxConnections()).isEqualTo(25);
        assertThat(target.getTimeoutMillis()).isEqualTo(123456789L);
        assertThat(target.getLoadFactor()).isEqualTo(0.75D);
        assertThat(target.isEnabled()).isTrue();
        assertThat(target.getClusters()).containsExactly("alpha", "beta", "gamma");
        assertThat(target.getPorts()).containsExactly(6650, 6651);
        assertThat(target.getRoles()).containsExactly("admin", "proxy");
        assertThat(target.getWeights()).containsEntry("primary", 10).containsEntry("secondary", 20);
        assertThat(target.getMaybePort()).contains(7000);
    }

    @Test
    void clearsContainerOptionalAndNumberFieldsFromBlankProperties() {
        final FieldParserTarget target = FieldParserTarget.withConfiguredValues();
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("clusters", "   ");
        properties.put("roles", "");
        properties.put("maybePort", " ");
        properties.put("maxConnections", "");

        FieldParser.update(properties, target);

        assertThat(target.getClusters()).isEmpty();
        assertThat(target.getRoles()).isEmpty();
        assertThat(target.getMaybePort()).isEmpty();
        assertThat(target.getMaxConnections()).isNull();
        assertThat(target.getName()).isEqualTo("initial");
    }
}

final class FieldParserTarget {

    private String name = "initial";
    private Integer maxConnections = 1;
    private Long timeoutMillis = 1L;
    private Double loadFactor = 1.0D;
    private Boolean enabled = Boolean.FALSE;
    private List<String> clusters = new ArrayList<>(List.of("initial-cluster"));
    private List<Integer> ports = new ArrayList<>(List.of(1));
    private Set<String> roles = new LinkedHashSet<>(Arrays.asList("initial-role"));
    private Map<String, Integer> weights = new LinkedHashMap<>(Map.of("initial", 1));
    private Optional<Integer> maybePort = Optional.of(1);

    static FieldParserTarget withConfiguredValues() {
        return new FieldParserTarget();
    }

    String getName() {
        return name;
    }

    Integer getMaxConnections() {
        return maxConnections;
    }

    Long getTimeoutMillis() {
        return timeoutMillis;
    }

    Double getLoadFactor() {
        return loadFactor;
    }

    Boolean isEnabled() {
        return enabled;
    }

    List<String> getClusters() {
        return clusters;
    }

    List<Integer> getPorts() {
        return ports;
    }

    Set<String> getRoles() {
        return roles;
    }

    Map<String, Integer> getWeights() {
        return weights;
    }

    Optional<Integer> getMaybePort() {
        return maybePort;
    }
}
