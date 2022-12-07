/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableFactory;

class ThePortableFactory implements PortableFactory {
    public static final int FACTORY_ID = 1;

    @Override
    public Portable create(int classId) {
        return classId == User.CLASS_ID ? new User() : null;
    }
}
