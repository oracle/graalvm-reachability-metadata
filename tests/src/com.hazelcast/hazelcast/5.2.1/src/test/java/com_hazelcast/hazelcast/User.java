/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;

import java.io.IOException;
import java.util.Objects;

class User implements Portable {
    public static final int CLASS_ID = 1;

    public String username;
    public int age;
    public boolean active;

    User(String username, int age, boolean active) {
        this.username = username;
        this.age = age;
        this.active = active;
    }

    User() {
    }

    @Override
    public String toString() {
        return "User{"
                + "username='" + username + '\''
                + ", age=" + age
                + ", active=" + active
                + '}';
    }

    @Override
    public int getFactoryId() {
        return ThePortableFactory.FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void writePortable(PortableWriter writer) throws IOException {
        writer.writeString("username", username);
        writer.writeInt("age", age);
        writer.writeBoolean("active", active);
    }

    @Override
    public void readPortable(PortableReader reader) throws IOException {
        username = reader.readString("username");
        age = reader.readInt("age");
        active = reader.readBoolean("active");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return age == user.age && active == user.active && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, age, active);
    }
}
