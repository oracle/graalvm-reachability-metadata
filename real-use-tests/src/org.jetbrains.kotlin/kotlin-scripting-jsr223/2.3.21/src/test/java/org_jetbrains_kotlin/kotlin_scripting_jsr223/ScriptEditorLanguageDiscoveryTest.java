/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jsr223;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEngineFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * JSR-223 provider discovery supplies Kotlin language choices for a script editor.
 *
 * An editor discovers installed providers rather than hardcoding their registration, selects a
 * language for an opened file, and displays the provider's language version and content type.
 * Merely opening a document does not execute its contents.
 */
@Timeout(60)
public class ScriptEditorLanguageDiscoveryTest {

    @Test
    void recognizesKotlinScriptDocumentsFromInstalledProviders() {
        ScriptEngineManager manager = new ScriptEngineManager();
        List<ScriptEngineFactory> providers = manager.getEngineFactories();
        ScriptEngineFactory selected = providers.stream()
                .filter(provider -> provider.getExtensions().contains("kts"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No provider for Kotlin scripts"));
        assertThat(selected).isInstanceOf(KotlinJsr223DefaultScriptEngineFactory.class);
        KotlinJsr223DefaultScriptEngineFactory kotlin = (KotlinJsr223DefaultScriptEngineFactory) selected;

        LanguageCatalog catalog = new LanguageCatalog(providers);
        LanguageOption option = catalog.forFile("invoice-validation.kts").orElseThrow();

        assertThat(option.name()).isEqualTo("kotlin");
        assertThat(option.contentType()).isEqualTo("text/x-kotlin");
        assertThat(option.version()).isNotBlank().isEqualTo(kotlin.getLanguageVersion());
        assertThat(kotlin.getNames()).contains(option.name());
        assertThat(catalog.forFile("invoice-validation.txt")).isEmpty();
    }

    private record LanguageOption(String name, String version, String contentType) {
    }

    private record LanguageCatalog(List<ScriptEngineFactory> providers) {
        Optional<LanguageOption> forFile(String filename) {
            String extension = filename.substring(filename.lastIndexOf('.') + 1);
            return providers.stream()
                    .filter(provider -> provider.getExtensions().contains(extension))
                    .map(provider -> new LanguageOption(provider.getLanguageName(),
                            provider.getLanguageVersion(), provider.getMimeTypes().getFirst()))
                    .findFirst();
        }
    }
}
