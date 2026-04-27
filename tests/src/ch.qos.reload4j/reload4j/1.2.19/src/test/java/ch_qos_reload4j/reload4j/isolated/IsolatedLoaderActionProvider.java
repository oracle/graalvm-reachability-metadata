/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j.isolated;

import ch_qos_reload4j.reload4j.IsolatedLoaderAction;

import org.apache.log4j.helpers.Loader;

public class IsolatedLoaderActionProvider implements IsolatedLoaderAction {
    @Override
    public String loadClass(String className) throws ClassNotFoundException {
        return Loader.loadClass(className).getName();
    }
}
