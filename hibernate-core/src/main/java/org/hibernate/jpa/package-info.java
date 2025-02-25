/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */

/**
 * Responsible for dealing with certain details of compliance with the
 * JPA specification, including:
 * <ul>
 * <li>{@linkplain org.hibernate.jpa.boot bootstrapping} JPA,
 * <li>calling JPA {@linkplain org.hibernate.jpa.event event listeners},
 *     and
 * <li>managing cases where Hibernate intentionally
 *    {@linkplain org.hibernate.jpa.spi violates} the specification by
 *    default (something Hibernate only does if it has a really good
 *    reason to do so).
 * </ul>
 * Enumerates the {@linkplain jakarta.persistence.Query#setHint hints}
 * recognized by Hibernate:
 * <ul>
 * <li>{@link org.hibernate.jpa.SpecHints} enumerates the standard hints
 *     defined by the JPA specification.
 * <li>{@link org.hibernate.jpa.HibernateHints} enumerates hints defined
 *     by Hibernate.
 * </ul>
 */
package org.hibernate.jpa;
