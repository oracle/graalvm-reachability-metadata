/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.Serializable;

import org.apache.ibatis.javassist.tools.reflect.ClassMetaobject;
import org.apache.ibatis.javassist.tools.reflect.Metalevel;
import org.apache.ibatis.javassist.tools.reflect.Metaobject;

// A statically implemented metalevel object needs no runtime class transformation.
public class ReflectivePerson implements Metalevel, Serializable {
    private static final long serialVersionUID = 1L;
    private static final ClassMetaobject TYPE = new ClassMetaobject(new String[]{ReflectivePerson.class.getName()});
    private transient Metaobject metaobject;
    public String name;

    public ReflectivePerson(String name) {
        this.name = name;
        metaobject = new Metaobject(this, new Object[]{name});
    }

    public String label(String prefix) throws Throwable {
        Object[] arguments = new Object[]{prefix};
        return (String) (metaobject == null ? ClassMetaobject.invoke(this, 0, arguments)
                : metaobject.trapMethodcall(0, arguments));
    }

    public static String category(String prefix) throws Throwable {
        return (String) TYPE.trapMethodcall(1, new Object[]{prefix});
    }

    public String _m_0_label(String prefix) {
        return prefix + name;
    }

    public static String _m_1_category(String prefix) {
        return prefix + "person";
    }

    @Override
    public ClassMetaobject _getClass() {
        return TYPE;
    }

    @Override
    public Metaobject _getMetaobject() {
        return metaobject;
    }

    @Override
    public void _setMetaobject(Metaobject metaobject) {
        this.metaobject = metaobject;
    }
}
