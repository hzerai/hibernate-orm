/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.dialect;

import java.sql.BatchUpdateException;
import java.sql.CallableStatement;
import java.sql.SQLException;

import org.hibernate.JDBCException;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.PessimisticLockException;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.QueryTimeoutException;
import org.hibernate.exception.LockAcquisitionException;
import org.hibernate.exception.spi.SQLExceptionConversionDelegate;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.junit4.BaseUnitTestCase;
import org.junit.Test;

import org.mockito.Mockito;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test case for PostgreSQL specific things.
 * @author Bryan Varner
 * @author Christoph Dreis
 */
public class PostgreSQLDialectTestCase extends BaseUnitTestCase {

	@Test
	@TestForIssue( jiraKey = "HHH-7251")
	public void testDeadlockException() {
		PostgreSQLDialect dialect = new PostgreSQLDialect();
		SQLExceptionConversionDelegate delegate = dialect.buildSQLExceptionConversionDelegate();
		assertNotNull(delegate);

		JDBCException exception = delegate.convert(new SQLException("Deadlock Detected", "40P01"), "", "");
		assertTrue(exception instanceof LockAcquisitionException);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-7251")
	public void testTimeoutException() {
		PostgreSQLDialect dialect = new PostgreSQLDialect();
		SQLExceptionConversionDelegate delegate = dialect.buildSQLExceptionConversionDelegate();
		assertNotNull(delegate);

		JDBCException exception = delegate.convert(new SQLException("Lock Not Available", "55P03"), "", "");
		assertTrue(exception instanceof PessimisticLockException);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-13661")
	public void testQueryTimeoutException() {
		final PostgreSQLDialect dialect = new PostgreSQLDialect();
		final SQLExceptionConversionDelegate delegate = dialect.buildSQLExceptionConversionDelegate();
		assertNotNull( delegate );

		final JDBCException exception = delegate.convert( new SQLException("Client cancelled operation", "57014"), "", "" );
		assertTrue( exception instanceof QueryTimeoutException );
	}

	/**
	 * Tests that getForUpdateString(String aliases, LockOptions lockOptions) will return a String
	 * that will effect the SELECT ... FOR UPDATE OF tableAlias1, ..., tableAliasN
	 */
	@TestForIssue( jiraKey = "HHH-5654" )
	public void testGetForUpdateStringWithAliasesAndLockOptions() {
		PostgreSQLDialect dialect = new PostgreSQLDialect();
		LockOptions lockOptions = new LockOptions();
		lockOptions.setAliasSpecificLockMode("tableAlias1", LockMode.PESSIMISTIC_WRITE);

		String forUpdateClause = dialect.getForUpdateString("tableAlias1", lockOptions);
		assertEquals( "for update of tableAlias1", forUpdateClause );

		lockOptions.setAliasSpecificLockMode("tableAlias2", LockMode.PESSIMISTIC_WRITE);
		forUpdateClause = dialect.getForUpdateString("tableAlias1,tableAlias2", lockOptions);
		assertEquals("for update of tableAlias1,tableAlias2", forUpdateClause);
	}

	@Test
	public void testExtractConstraintName() {
		PostgreSQLDialect dialect = new PostgreSQLDialect();
		SQLException psqlException = new SQLException("ERROR: duplicate key value violates unique constraint \"uk_4bm1x2ultdmq63y3h5r3eg0ej\" Detail: Key (username, server_config)=(user, 1) already exists.", "23505");
		BatchUpdateException batchUpdateException = new BatchUpdateException("Concurrent Error", "23505", null);
		batchUpdateException.setNextException(psqlException);
		String constraintName = dialect.getViolatedConstraintNameExtractor().extractConstraintName(batchUpdateException);
		assertThat(constraintName, is("uk_4bm1x2ultdmq63y3h5r3eg0ej"));
	}

	@Test
	@TestForIssue(jiraKey = "HHH-8687")
	public void testMessageException() {
		PostgreSQLDialect dialect = new PostgreSQLDialect();
		try {
			dialect.getResultSet( Mockito.mock( CallableStatement.class), "abc" );
			fail( "Expected UnsupportedOperationException" );
		}
		catch (Exception e) {
			assertTrue( e instanceof UnsupportedOperationException );
			assertEquals( "PostgreSQL only supports accessing REF_CURSOR parameters by position", e.getMessage() );
		}
	}

	/**
	 * Tests that getAlterTableString() will make use of IF EXISTS syntax
	 */
	@Test
	@TestForIssue( jiraKey = "HHH-11647" )
	public void testGetAlterTableString() {
		PostgreSQLDialect dialect = new PostgreSQLDialect();

		assertEquals("alter table if exists table_name", dialect.getAlterTableString( "table_name" ));
	}
}
