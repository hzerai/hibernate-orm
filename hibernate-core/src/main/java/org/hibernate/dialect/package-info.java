/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

/**
 * This package abstracts over the multifarious dialects of SQL
 * understood by the databases supported by Hibernate. A concrete
 * implementation of {@link org.hibernate.dialect.Dialect} defines
 * the variant understood by a certain RDBMS.
 * <ul>
 * <li>An integrator may directly extend {@code Dialect} in order
 *     to provide support for a database unknown to Hibernate, or
 * <li>a program may extend one of the concrete {@code Dialect}s
 *     in this package in order to customize certain aspects of
 *     the SQL generated by Hibernate.
 * </ul>
 * A concrete {@code Dialect} may be explicitly selected using
 * {@value org.hibernate.cfg.AvailableSettings#DIALECT}, but
 * this is not usually necessary unless a program uses a custom
 * implementation.
 */
package org.hibernate.dialect;
