/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import org.hibernate.dialect.DerbyDialect;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.jdbc.SQLStatementInspector;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SkipForDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ServiceRegistry
@DomainModel( annotatedClasses = {
		InsertSelectTests.EntityEntry.class,
		InsertSelectTests.EntitySource.class
})
@SessionFactory(statementInspectorClass = SQLStatementInspector.class)
public class InsertSelectTests {

	@BeforeEach
	public void prepareTestData(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.persist( new EntitySource( "A" ) );
					session.persist( new EntitySource( "A" ) );
				}
		);
	}

	@AfterEach
	public void cleanupTestData(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createMutationQuery( "delete from java.lang.Object" ).executeUpdate();
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-15527")
	public void testInsertSelectGeneratedAssigned(SessionFactoryScope scope) {
		final SQLStatementInspector statementInspector = (SQLStatementInspector) scope.getStatementInspector();
		scope.inTransaction(
				session -> {
					statementInspector.clear();
					session.createMutationQuery(
							"insert into EntityEntry (id, name) " +
									"select 1, 'abc' from EntityEntry e"
					).executeUpdate();
					statementInspector.assertExecutedCount( 1 );
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-15531")
	@SkipForDialect(dialectClass = DerbyDialect.class, reason = "Derby doesn't really support window functions, " +
			"but this requires the use of a dense_rank window function. We could emulate this, but don't think it's worth it")
	public void testInsertSelectDistinct(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					final int rows = session.createMutationQuery(
							"insert into EntityEntry (name) " +
									"select distinct e.name from EntitySource e"
					).executeUpdate();
					assertEquals( 1, rows );
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-15531")
	@SkipForDialect(dialectClass = DerbyDialect.class, reason = "Derby doesn't really support window functions and " +
			"its attempt at a row_number function fails to deliver the desired semantics")
	public void testInsertSelectGroupBy(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					final int rows = session.createMutationQuery(
							"insert into EntityEntry (name) " +
									"select e.name from EntitySource e group by e.name"
					).executeUpdate();
					assertEquals( 1, rows );
				}
		);
	}

	@Entity(name = "EntityEntry")
	public static class EntityEntry {
		@Id
		@GeneratedValue
		Integer id;
		String name;
	}

	@Entity(name = "EntitySource")
	public static class EntitySource {
		@Id
		@GeneratedValue
		Integer id;
		String name;

		public EntitySource() {
		}

		public EntitySource(String name) {
			this.name = name;
		}
	}
}
