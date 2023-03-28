/*
 * This file is generated by jOOQ.
 */
package org_jooq.jooq.model.tables;


import java.util.function.Function;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function3;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row3;
import org.jooq.Schema;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import org_jooq.jooq.model.College;
import org_jooq.jooq.model.Keys;
import org_jooq.jooq.model.tables.records.TeacherRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Teacher extends TableImpl<TeacherRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>college.teacher</code>
     */
    public static final Teacher TEACHER = new Teacher();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<TeacherRecord> getRecordType() {
        return TeacherRecord.class;
    }

    /**
     * The column <code>college.teacher.id</code>.
     */
    public final TableField<TeacherRecord, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>college.teacher.first_name</code>.
     */
    public final TableField<TeacherRecord, String> FIRST_NAME = createField(DSL.name("first_name"), SQLDataType.VARCHAR(255), this, "");

    /**
     * The column <code>college.teacher.last_name</code>.
     */
    public final TableField<TeacherRecord, String> LAST_NAME = createField(DSL.name("last_name"), SQLDataType.VARCHAR(255), this, "");

    private Teacher(Name alias, Table<TeacherRecord> aliased) {
        this(alias, aliased, null);
    }

    private Teacher(Name alias, Table<TeacherRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>college.teacher</code> table reference
     */
    public Teacher(String alias) {
        this(DSL.name(alias), TEACHER);
    }

    /**
     * Create an aliased <code>college.teacher</code> table reference
     */
    public Teacher(Name alias) {
        this(alias, TEACHER);
    }

    /**
     * Create a <code>college.teacher</code> table reference
     */
    public Teacher() {
        this(DSL.name("teacher"), null);
    }

    public <O extends Record> Teacher(Table<O> child, ForeignKey<O, TeacherRecord> key) {
        super(child, key, TEACHER);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : College.COLLEGE;
    }

    @Override
    public Identity<TeacherRecord, Long> getIdentity() {
        return (Identity<TeacherRecord, Long>) super.getIdentity();
    }

    @Override
    public UniqueKey<TeacherRecord> getPrimaryKey() {
        return Keys.KEY_TEACHER_PRIMARY;
    }

    @Override
    public Teacher as(String alias) {
        return new Teacher(DSL.name(alias), this);
    }

    @Override
    public Teacher as(Name alias) {
        return new Teacher(alias, this);
    }

    @Override
    public Teacher as(Table<?> alias) {
        return new Teacher(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public Teacher rename(String name) {
        return new Teacher(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Teacher rename(Name name) {
        return new Teacher(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public Teacher rename(Table<?> name) {
        return new Teacher(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row3 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row3<Long, String, String> fieldsRow() {
        return (Row3) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function3<? super Long, ? super String, ? super String, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function3<? super Long, ? super String, ? super String, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
