/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.maven.doxia.parser.module.AbstractParserModule;
import org.apache.maven.doxia.parser.module.ParserModule;
import org.apache.maven.doxia.parser.module.ParserModuleManager;
import org.apache.maven.doxia.parser.module.ParserModuleNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ParserModuleManagerTest {
    @Test
    void parserModuleManagerFindsRegisteredParserModulesById() throws ParserModuleNotFoundException {
        ParserModule xdocModule = new DocumentationParserModule("xdoc", "xdoc");
        ParserModule aptModule = new DocumentationParserModule("apt", "apt", "apt.vm");
        ParserModuleManager manager = new InMemoryParserModuleManager(xdocModule, aptModule);

        assertThat(manager.getParserModules()).containsExactly(xdocModule, aptModule);
        assertThat(manager.getParserModule("apt")).isSameAs(aptModule);
        assertThatThrownBy(() -> manager.getParserModule("unknown"))
                .isInstanceOf(ParserModuleNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    private static final class DocumentationParserModule extends AbstractParserModule {
        DocumentationParserModule(String parserId, String... extensions) {
            super("documentation", parserId, extensions);
        }
    }

    private static final class InMemoryParserModuleManager implements ParserModuleManager {
        private final Map<String, ParserModule> parserModules = new LinkedHashMap<>();

        InMemoryParserModuleManager(ParserModule... parserModules) {
            for (ParserModule parserModule : parserModules) {
                this.parserModules.put(parserModule.getParserId(), parserModule);
            }
        }

        @Override
        public Collection<ParserModule> getParserModules() {
            return parserModules.values();
        }

        @Override
        public ParserModule getParserModule(String id) throws ParserModuleNotFoundException {
            ParserModule parserModule = parserModules.get(id);
            if (parserModule == null) {
                throw new ParserModuleNotFoundException("Cannot find parser module id = " + id);
            }
            return parserModule;
        }
    }
}
