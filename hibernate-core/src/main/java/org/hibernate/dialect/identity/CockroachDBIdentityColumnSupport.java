/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect.identity;

import java.sql.Types;

import org.hibernate.MappingException;

public class CockroachDBIdentityColumnSupport extends IdentityColumnSupportImpl {
	@Override
	public boolean supportsIdentityColumns() {
		// Full support requires setting the sql.defaults.serial_normalization=sql_sequence in CockroachDB.
		// Also, support for serial4 is not enabled by default: https://github.com/cockroachdb/cockroach/issues/26925#issuecomment-1255293916
		return false;
	}

	@Override
	// CockroachDB does not create a sequence for id columns
	public String getIdentitySelectString(String table, String column, int type) {
		return "select 1";
	}

	@Override
	public String getIdentityColumnString(int type) {
		switch ( type ) {
			case Types.TINYINT:
			case Types.SMALLINT:
				return "serial2 not null";
			case Types.INTEGER:
				return "serial4 not null";
			case Types.BIGINT:
				return "serial8 not null";
			default:
				throw new MappingException( "illegal identity column type");
		}
	}

	@Override
	public boolean hasDataTypeInIdentityColumn() {
		return false;
	}
}
