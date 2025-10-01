/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast.identifiedDataSerializable;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

public class SampleDataSerializableFactory implements DataSerializableFactory {
    public static final int FACTORY_ID = 1000;

    @Override
    public IdentifiedDataSerializable create(int typeId) {
        return typeId == 100 ? new Employee() : null;
    }
}
