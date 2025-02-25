/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.model.relational;

import java.util.List;

import org.hibernate.Incubating;
import org.hibernate.boot.Metadata;
import org.hibernate.dialect.temptable.TemporaryTableColumn;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UserDefinedType;

/**
 * A pluggable contract that allows ordering of columns within {@link org.hibernate.mapping.Table},
 * {@link org.hibernate.mapping.Constraint} and {@link org.hibernate.mapping.UserDefinedType}.
 * <p>
 * Whenever reasonable, the use of a custom {@linkplain ColumnOrderingStrategy} is highly
 * recommended in preference to tedious and repetitive explicit table and column name
 * mappings. It's anticipated that most projects using Hibernate will feature a custom
 * implementation of {@code ImplicitNamingStrategy}.
 * <p>
 * An {@linkplain ColumnOrderingStrategy} may be selected using the configuration property
 * {@value org.hibernate.cfg.AvailableSettings#COLUMN_ORDERING_STRATEGY}.
 */
@Incubating
public interface ColumnOrderingStrategy {

	/**
	 * Orders the columns of the table.
	 * May return null if columns were not ordered.
	 */
	List<Column> orderTableColumns(Table table, Metadata metadata);

	/**
	 * Orders the columns of the constraint.
	 * May return null if columns were not ordered.
	 */
	List<Column> orderConstraintColumns(Constraint constraint, Metadata metadata);

	/**
	 * Orders the columns of the user defined type.
	 * May return null if columns were not ordered.
	 */
	List<Column> orderUserDefinedTypeColumns(UserDefinedType userDefinedType, Metadata metadata);

	/**
	 * Orders the columns of the temporary table.
	 */
	void orderTemporaryTableColumns(List<TemporaryTableColumn> temporaryTableColumns, Metadata metadata);
}
