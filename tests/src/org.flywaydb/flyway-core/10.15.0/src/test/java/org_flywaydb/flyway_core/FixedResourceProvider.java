/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.resource.classpath.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is needed as GraalVM doesn't support enumerating resources. It uses a hardcoded list of migrations.
 */
class FixedResourceProvider implements ResourceProvider {
    private static final Set<String> MIGRATIONS;

    static {
        Set<String> migrations = new HashSet<>();
        migrations.add("db/migration/V1__create_table.sql");
        migrations.add("db/migration/V2__alter_table.sql");
        MIGRATIONS = Collections.unmodifiableSet(migrations);
    }

    @Override
    public LoadableResource getResource(String name) {
        if (!MIGRATIONS.contains(name)) {
            return null;
        }
        return new ClassPathResource(null, name, getClass().getClassLoader(), StandardCharsets.UTF_8);
    }

    @Override
    public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
        return MIGRATIONS.stream()
                .map(file -> new ClassPathResource(null, file, getClass().getClassLoader(), StandardCharsets.UTF_8))
                .collect(Collectors.toList());
    }
}
