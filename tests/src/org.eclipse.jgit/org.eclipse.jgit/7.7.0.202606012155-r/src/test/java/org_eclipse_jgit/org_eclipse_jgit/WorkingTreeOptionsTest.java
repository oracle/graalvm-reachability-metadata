/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jgit.org_eclipse_jgit;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig.AutoCRLF;
import org.eclipse.jgit.lib.CoreConfig.CheckStat;
import org.eclipse.jgit.lib.CoreConfig.EOL;
import org.eclipse.jgit.lib.CoreConfig.HideDotFiles;
import org.eclipse.jgit.lib.CoreConfig.SymLinks;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkingTreeOptionsTest {

    @Test
    void readsCoreWorkingTreeOptionsFromConfig() {
        Config config = new Config();
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
                ConfigConstants.CONFIG_KEY_AUTOCRLF, "input");
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
                ConfigConstants.CONFIG_KEY_EOL, "crlf");
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
                ConfigConstants.CONFIG_KEY_CHECKSTAT, "minimal");
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
                ConfigConstants.CONFIG_KEY_SYMLINKS, "false");
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
                ConfigConstants.CONFIG_KEY_HIDEDOTFILES, "true");

        WorkingTreeOptions options = config.get(WorkingTreeOptions.KEY);

        assertThat(options.getAutoCRLF()).isEqualTo(AutoCRLF.INPUT);
        assertThat(options.getEOL()).isEqualTo(EOL.CRLF);
        assertThat(options.getCheckStat()).isEqualTo(CheckStat.MINIMAL);
        assertThat(options.getSymLinks()).isEqualTo(SymLinks.FALSE);
        assertThat(options.getHideDotFiles()).isEqualTo(HideDotFiles.TRUE);
    }
}
