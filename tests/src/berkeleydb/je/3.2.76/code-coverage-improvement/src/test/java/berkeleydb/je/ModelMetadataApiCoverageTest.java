/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.persist.model.ClassMetadata;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.EntityMetadata;
import com.sleepycat.persist.model.FieldMetadata;
import com.sleepycat.persist.model.PrimaryKeyMetadata;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKeyMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelMetadataApiCoverageTest {

    @Test
    void metadataObjectsCompareAndExposePersistentSchemaInformation() {
        FieldMetadata name = new FieldMetadata("name", String.class.getName(), "Example");
        PrimaryKeyMetadata primary = new PrimaryKeyMetadata("id", int.class.getName(),
                "Example", "ids");
        SecondaryKeyMetadata secondary = new SecondaryKeyMetadata("group", String.class.getName(),
                "Example", String.class.getName(), "group", Relationship.MANY_TO_ONE,
                null, DeleteAction.NULLIFY);
        ClassMetadata classMetadata = new ClassMetadata("Example", 1, null, true, primary,
                Map.of("group", secondary), List.of(name));
        ClassMetadata equalClassMetadata = new ClassMetadata("Example", 1, null, true,
                primary, Map.of("group", secondary), List.of(name));
        assertThat(classMetadata.getClassName()).isEqualTo("Example");
        assertThat(classMetadata.getVersion()).isEqualTo(1);
        assertThat(classMetadata.isEntityClass()).isTrue();
        assertThat(classMetadata.getPrimaryKey()).isSameAs(primary);
        assertThat(classMetadata.getSecondaryKeys()).containsEntry("group", secondary);
        assertThat(classMetadata.getCompositeKeyFields()).containsExactly(name);
        assertThat(classMetadata).isEqualTo(equalClassMetadata);
        assertThat(classMetadata.hashCode()).isEqualTo(equalClassMetadata.hashCode());

        EntityMetadata entityMetadata = new EntityMetadata("Example", primary,
                Map.of("group", secondary));
        EntityMetadata equalEntityMetadata = new EntityMetadata("Example", primary,
                Map.of("group", secondary));
        assertThat(entityMetadata.getClassName()).isEqualTo("Example");
        assertThat(entityMetadata.getPrimaryKey()).isSameAs(primary);
        assertThat(entityMetadata.getSecondaryKeys()).containsEntry("group", secondary);
        assertThat(entityMetadata).isEqualTo(equalEntityMetadata);
        assertThat(entityMetadata.hashCode()).isEqualTo(equalEntityMetadata.hashCode());

        assertThat(name.getName()).isEqualTo("name");
        assertThat(name.getClassName()).isEqualTo(String.class.getName());
        assertThat(name.getDeclaringClassName()).isEqualTo("Example");
        assertThat(name).isEqualTo(new FieldMetadata("name", String.class.getName(), "Example"));
        assertThat(name.hashCode()).isNotZero();
        assertThat(primary.getSequenceName()).isEqualTo("ids");
        assertThat(secondary.getElementClassName()).isEqualTo(String.class.getName());
        assertThat(secondary.getKeyName()).isEqualTo("group");
        assertThat(secondary.getRelationship()).isSameAs(Relationship.MANY_TO_ONE);
        assertThat(secondary.getDeleteAction()).isSameAs(DeleteAction.NULLIFY);
        assertThat(DeleteAction.valueOf("CASCADE")).isSameAs(DeleteAction.CASCADE);
        assertThat(DeleteAction.values()).contains(DeleteAction.ABORT, DeleteAction.NULLIFY);
        assertThat(Relationship.valueOf("ONE_TO_ONE")).isSameAs(Relationship.ONE_TO_ONE);
        assertThat(Relationship.values()).contains(Relationship.MANY_TO_MANY);
    }
}
