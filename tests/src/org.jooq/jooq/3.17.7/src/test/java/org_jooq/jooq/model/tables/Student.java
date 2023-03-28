/*
 * This file is generated by jOOQ.
 */
package org_jooq.jooq.model.tables;


import java.time.LocalDate;
import java.util.function.Function;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function6;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row6;
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
import org_jooq.jooq.model.tables.records.StudentRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Student extends TableImpl<StudentRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>college.student</code>
     */
    public static final Student STUDENT = new Student();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<StudentRecord> getRecordType() {
        return StudentRecord.class;
    }

    /**
     * The column <code>college.student.id</code>.
     */
    public final TableField<StudentRecord, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>college.student.age</code>.
     */
    public final TableField<StudentRecord, Integer> AGE = createField(DSL.name("age"), SQLDataType.INTEGER, this, "");

    /**
     * The column <code>college.student.birth_date</code>.
     */
    public final TableField<StudentRecord, LocalDate> BIRTH_DATE = createField(DSL.name("birth_date"), SQLDataType.LOCALDATE, this, "");

    /**
     * The column <code>college.student.first_name</code>.
     */
    public final TableField<StudentRecord, String> FIRST_NAME = createField(DSL.name("first_name"), SQLDataType.VARCHAR(255), this, "");

    /**
     * The column <code>college.student.gender</code>.
     */
    public final TableField<StudentRecord, String> GENDER = createField(DSL.name("gender"), SQLDataType.VARCHAR(255), this, "");

    /**
     * The column <code>college.student.last_name</code>.
     */
    public final TableField<StudentRecord, String> LAST_NAME = createField(DSL.name("last_name"), SQLDataType.VARCHAR(255), this, "");

    private Student(Name alias, Table<StudentRecord> aliased) {
        this(alias, aliased, null);
    }

    private Student(Name alias, Table<StudentRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>college.student</code> table reference
     */
    public Student(String alias) {
        this(DSL.name(alias), STUDENT);
    }

    /**
     * Create an aliased <code>college.student</code> table reference
     */
    public Student(Name alias) {
        this(alias, STUDENT);
    }

    /**
     * Create a <code>college.student</code> table reference
     */
    public Student() {
        this(DSL.name("student"), null);
    }

    public <O extends Record> Student(Table<O> child, ForeignKey<O, StudentRecord> key) {
        super(child, key, STUDENT);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : College.COLLEGE;
    }

    @Override
    public Identity<StudentRecord, Long> getIdentity() {
        return (Identity<StudentRecord, Long>) super.getIdentity();
    }

    @Override
    public UniqueKey<StudentRecord> getPrimaryKey() {
        return Keys.KEY_STUDENT_PRIMARY;
    }

    @Override
    public Student as(String alias) {
        return new Student(DSL.name(alias), this);
    }

    @Override
    public Student as(Name alias) {
        return new Student(alias, this);
    }

    @Override
    public Student as(Table<?> alias) {
        return new Student(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public Student rename(String name) {
        return new Student(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Student rename(Name name) {
        return new Student(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public Student rename(Table<?> name) {
        return new Student(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row6 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row6<Long, Integer, LocalDate, String, String, String> fieldsRow() {
        return (Row6) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function6<? super Long, ? super Integer, ? super LocalDate, ? super String, ? super String, ? super String, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function6<? super Long, ? super Integer, ? super LocalDate, ? super String, ? super String, ? super String, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
