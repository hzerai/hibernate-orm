/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.interceptor;

import jakarta.persistence.PersistenceException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import org.junit.Test;

import org.hibernate.AssertionFailure;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.TransactionException;
import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;

import org.hibernate.resource.jdbc.internal.EmptyStatementInspector;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.type.Type;

import static org.hibernate.testing.junit4.ExtraAssertions.assertTyping;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Gavin King
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 */
public class InterceptorTest extends BaseCoreFunctionalTestCase {
	@Override
	public String[] getMappings() {
		return new String[] { "interceptor/User.hbm.xml", "interceptor/Image.hbm.xml" };
	}

	@Override
	protected String getBaseForMappings() {
		return "org/hibernate/orm/test/";
	}

	@Test
	public void testCollectionIntercept() {
		Session s = openSession( new CollectionInterceptor() );
		Transaction t = s.beginTransaction();
		User u = new User( "Gavin", "nivag" );
		s.persist( u );
		u.setPassword( "vagni" );
		t.commit();
		s.close();

		s = openSession();
		t = s.beginTransaction();
		u = s.get( User.class, "Gavin" );
		assertEquals( 2, u.getActions().size() );
		s.delete( u );
		t.commit();
		s.close();
	}

	@Test
	public void testPropertyIntercept() {
		Session s = openSession( new PropertyInterceptor() );
		Transaction t = s.beginTransaction();
		User u = new User( "Gavin", "nivag" );
		s.persist( u );
		u.setPassword( "vagni" );
		t.commit();
		s.close();

		s = openSession();
		t = s.beginTransaction();
		u = s.get( User.class, "Gavin" );
		assertNotNull( u.getCreated() );
		assertNotNull( u.getLastUpdated() );
		s.delete( u );
		t.commit();
		s.close();
	}

	/**
	 * Test case from HHH-1921.  Here the interceptor resets the
	 * current-state to the same thing as the current db state; this
	 * causes EntityPersister.findDirty() to return no dirty properties.
	 */
	@Test
	@TestForIssue(jiraKey = "HHH-1921")
	public void testPropertyIntercept2() {
		Session s = openSession();
		Transaction t = s.beginTransaction();
		User u = new User( "Josh", "test" );
		s.persist( u );
		t.commit();
		s.close();

		s = openSession(
				new EmptyInterceptor() {
					@Override
					public boolean onFlushDirty(
							Object entity,
							Serializable id,
							Object[] currentState,
							Object[] previousState,
							String[] propertyNames,
							Type[] types) {
						for ( int i = 0; i < propertyNames.length; i++ ) {
							if ( propertyNames[i].equals( "password" ) ) {
								currentState[i] = "test";
							}
						}

						return true;
					}
				}
		);
		t = s.beginTransaction();
		u = s.get( User.class, u.getName() );
		u.setPassword( "nottest" );
		t.commit();
		s.close();

		s = openSession();
		t = s.beginTransaction();
		u = s.get( User.class, "Josh" );
		assertEquals( "test", u.getPassword() );
		s.delete( u );
		t.commit();
		s.close();

	}

	/**
	 * Test that setting a transaction timeout will cause an Exception to occur
	 * if the transaction timeout is exceeded.
	 */
	@Test
	public void testTimeout() throws Exception {
		final int TIMEOUT = 2;
		final int WAIT = TIMEOUT + 1;
		Session s = openSession();
		// Get the transaction and set the timeout BEFORE calling begin()
		Transaction t = s.getTransaction();
		t.setTimeout( TIMEOUT );
		t.begin();
		// Sleep for an amount of time that exceeds the transaction timeout
		Thread.sleep( WAIT * 1000 );
		try {
			// Do something with the transaction and try to commit it
			s.persist( new User( "john", "test" ) );
			t.commit();
			fail( "Transaction should have timed out" );
		}
		catch (PersistenceException e) {
			assertTyping( TransactionException.class, e );
			assertTrue(
					"Transaction failed for the wrong reason.  Expecting transaction timeout, but found [" +
							e.getMessage() + "]",
					e.getMessage().contains( "transaction timeout expired" )
			);
		}
	}

	@Test
	public void testComponentInterceptor() {
		final int checkPerm = 500;
		final String checkComment = "generated from interceptor";

		Session s = openSession(
				new EmptyInterceptor() {
					@Override
					public boolean onSave(
							Object entity,
							Object id,
							Object[] state,
							String[] propertyNames,
							Type[] types) {
						if ( state[0] == null ) {
							Image.Details detail = new Image.Details();
							detail.setPerm1( checkPerm );
							detail.setComment( checkComment );
							state[0] = detail;
						}
						return true;
					}
				}
		);
		s.beginTransaction();
		Image i = new Image();
		i.setName( "compincomp" );
		i = (Image) s.merge( i );
		assertNotNull( i.getDetails() );
		assertEquals( checkPerm, i.getDetails().getPerm1() );
		assertEquals( checkComment, i.getDetails().getComment() );
		s.getTransaction().commit();
		s.close();

		s = openSession();
		s.beginTransaction();
		i = s.get( Image.class, i.getId() );
		assertNotNull( i.getDetails() );
		assertEquals( checkPerm, i.getDetails().getPerm1() );
		assertEquals( checkComment, i.getDetails().getComment() );
		s.delete( i );
		s.getTransaction().commit();
		s.close();
	}

	@Test
	public void testStatefulIntercept() {
		User u = new User( "Gavin", "nivag" );

		final StatefulInterceptor statefulInterceptor = new StatefulInterceptor();
		try (Session s = openSession( statefulInterceptor )) {
			statefulInterceptor.setSession( s );

			Transaction t = s.beginTransaction();
			try {
				s.persist( u );
				u.setPassword( "vagni" );
				t.commit();
			}
			catch (Exception e) {
				if ( t.isActive() ) {
					t.rollback();
				}
				throw e;
			}
		}

		inTransaction(
				s -> {
					CriteriaBuilder criteriaBuilder = s.getCriteriaBuilder();
					CriteriaQuery<Log> criteria = criteriaBuilder.createQuery( Log.class );
					criteria.from( Log.class );
					List<Log> logs = s.createQuery( criteria ).list();
//		List logs = s.createCriteria(Log.class).list();
					assertEquals( 2, logs.size() );
					s.delete( u );
					s.createQuery( "delete from Log" ).executeUpdate();

				}
		);
	}

	@Test
	public void testInitiateIntercept() {
		final String injectedString = "******";
		final InstantiateInterceptor initiateInterceptor = new InstantiateInterceptor( injectedString );
		Session s = openSession( initiateInterceptor );

		Transaction t = s.beginTransaction();
		User u = new User( "Gavin", "nivag" );
		s.persist( u );
		t.commit();
		s.close();

		assertNull( u.getInjectedString() );
		u.setPassword( "blah" );

		s = openSession( initiateInterceptor );
		t = s.beginTransaction();

		User merged = (User) s.merge( u );
		assertEquals( injectedString, merged.getInjectedString() );
		assertEquals( u.getName(), merged.getName() );
		assertEquals( u.getPassword(), merged.getPassword() );

		merged.setInjectedString( null );

		User loaded = s.load( User.class, merged.getName() );
		// the session-bound instance was not instantiated by the interceptor, load simply returns it
		assertSame( merged, loaded );
		assertNull( merged.getInjectedString() );

		// flush the session and evict the merged instance from session to force an actual load
		s.flush();
		s.evict( merged );

		User reloaded = s.load( User.class, merged.getName() );
		// Interceptor IS called for instantiating the persistent instance associated to the session when using load
		assertEquals( injectedString, reloaded.getInjectedString() );
		assertEquals( u.getName(), reloaded.getName() );
		assertEquals( u.getPassword(), reloaded.getPassword() );

		s.delete( reloaded );
		t.commit();
		s.close();
	}

	@Test
	@TestForIssue(jiraKey = "HHH-6594")
	public void testPrepareStatementIntercept() {
		final Queue<String> expectedSQLs = new LinkedList<>();
		// Transaction 1
		expectedSQLs.add( "insert" );
		// Transaction 2
		expectedSQLs.add( "select" );
		expectedSQLs.add( "select" );
		// Transaction 3
		expectedSQLs.add( "select" );
//		expectedSQLs.add( "select" );
		expectedSQLs.add( "update" );
		// Transaction 4
		expectedSQLs.add( "select" );
		expectedSQLs.add( "delete" );

		final StatementInspector statementInspector = new EmptyStatementInspector() {
			@Override
			public String inspect(String sql) {
				assertNotNull( sql );
				String expectedSql = expectedSQLs.poll().toLowerCase( Locale.ROOT );
				assertTrue(
						"sql:\n " + sql.toLowerCase( Locale.ROOT ) + "\n doesn't start with \n" + expectedSql + "\n",
						sql.toLowerCase( Locale.ROOT ).startsWith( expectedSql )
				);
				return sql;
			}
		};

		Session s = sessionFactory().withOptions().statementInspector( statementInspector ).openSession();
		Transaction t = s.beginTransaction();
		User u = new User( "Lukasz", "Antoniak" );
		s.persist( u );
		t.commit();
		s.close();

		s = sessionFactory().withOptions().statementInspector( statementInspector ).openSession();
		t = s.beginTransaction();
		s.get( User.class, "Lukasz" );
		s.createQuery( "from User u" ).list();
		t.commit();
		s.close();

		u.setPassword( "Kinga" );
		s = sessionFactory().withOptions().statementInspector( statementInspector ).openSession();
		t = s.beginTransaction();
		s.merge( u );
		t.commit();
		s.close();

		s = sessionFactory().withOptions().statementInspector( statementInspector ).openSession();
		t = s.beginTransaction();
		s.delete( u );
		t.commit();
		s.close();

		assertTrue( expectedSQLs.isEmpty() );
	}

	public void testPrepareStatementFaultIntercept() {
		final StatementInspector statementInspector = new EmptyStatementInspector() {
			@Override
			public String inspect(String sql) {
				return null;
			}
		};

		Session s = sessionFactory().withOptions().statementInspector( statementInspector ).openSession();
		try {

			Transaction t = s.beginTransaction();
			User u = new User( "Kinga", "Mroz" );
			s.persist( u );
			t.commit();
		}
		catch (TransactionException e) {
			assertTrue( e.getCause() instanceof AssertionFailure );
		}
		finally {
			s.close();
		}
	}
}

