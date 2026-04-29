/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package reflectasm.java.util;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import java.util.ArrayList;

public class ArrayListConstructorAccess extends ConstructorAccess<ArrayList> {
    @Override
    public ArrayList newInstance() {
        return new ArrayList();
    }

    @Override
    public ArrayList newInstance(Object enclosingInstance) {
        return newInstance();
    }
}
