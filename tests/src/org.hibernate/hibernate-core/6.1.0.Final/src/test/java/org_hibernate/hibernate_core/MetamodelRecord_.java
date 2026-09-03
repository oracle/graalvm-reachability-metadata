/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(MetamodelRecord.class)
public class MetamodelRecord_ {
    public static volatile SingularAttribute<MetamodelRecord, Long> id;
    public static volatile SingularAttribute<MetamodelRecord, String> name;
}
