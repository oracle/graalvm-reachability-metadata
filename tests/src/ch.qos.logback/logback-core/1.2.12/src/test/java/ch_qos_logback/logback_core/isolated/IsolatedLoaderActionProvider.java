/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core.isolated;

import ch.qos.logback.core.util.Loader;
import ch_qos_logback.logback_core.IsolatedLoaderAction;

public class IsolatedLoaderActionProvider implements IsolatedLoaderAction {

    @Override
    public String loadClass(String className) throws ClassNotFoundException {
        return Loader.loadClass(className).getName();
    }
}
