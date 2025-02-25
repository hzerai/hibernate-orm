/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */

/**
 * Defines contracts for transactional and concurrent access to cached
 * {@linkplain org.hibernate.cache.spi.access.EntityDataAccess entity} and
 * {@linkplain org.hibernate.cache.spi.access.CollectionDataAccess collection} data. Transactions pass in a
 * timestamp indicating transaction start time which is then used to protect against concurrent access (exactly how
 * that occurs is based on the actual access-strategy impl used). Two different implementation patterns are provided
 * for:
 * <ul>
 * <li>
 *     A transaction-aware cache implementation might be wrapped by a <i>synchronous</i> access strategy,
 *     where updates to the cache are written to the cache inside the transaction.
 * </li>
 * <li>
 *     A non-transaction-aware cache would be wrapped by an <i>asynchronous</i> access strategy, where items
 *     are merely "soft locked" during the transaction and then updated during the "after transaction completion"
 *     phase; the soft lock is not an actual lock on the database row - only upon the cached representation of the
 *     item.
 * </li>
 * </ul>
 * The <i>asynchronous</i> access strategies are: {@link org.hibernate.cache.spi.access.AccessType#READ_ONLY read-only},
 * {@link org.hibernate.cache.spi.access.AccessType#READ_WRITE read-write} and
 * {@link org.hibernate.cache.spi.access.AccessType#NONSTRICT_READ_WRITE nonstrict-read-write}.  The only
 * <i>synchronous</i> access strategy is {@linkplain org.hibernate.cache.spi.access.AccessType#TRANSACTIONAL transactional}.
 * <p>
 * Note that, for an <i>asynchronous</i> cache, cache invalidation must be a two-step process (lock->unlock or
 * lock->afterUpdate), since this is the only way to guarantee consistency with the database for a nontransactional
 * cache implementation. For a <i>synchronous</i> cache, cache invalidation is a single step process (evict or update).
 * Hence, these contracts ({@link org.hibernate.cache.spi.access.EntityDataAccess} and
 * {@link org.hibernate.cache.spi.access.CollectionDataAccess}) define a three-step process to cater for both
 * models (see the individual contracts for details).
 * <p>
 * Note that query result caching does not go through an access strategy; those caches are managed directly against
 * the underlying {@link org.hibernate.cache.spi.QueryResultsRegion}.
 */
package org.hibernate.cache.spi.access;
