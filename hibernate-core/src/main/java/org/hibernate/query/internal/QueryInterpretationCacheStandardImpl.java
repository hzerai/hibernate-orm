/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.internal;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import jakarta.persistence.Tuple;

import org.hibernate.internal.util.collections.BoundedConcurrentHashMap;
import org.hibernate.query.QueryLogging;
import org.hibernate.query.spi.HqlInterpretation;
import org.hibernate.query.spi.NonSelectQueryPlan;
import org.hibernate.query.spi.ParameterMetadataImplementor;
import org.hibernate.query.spi.QueryInterpretationCache;
import org.hibernate.query.spi.QueryPlan;
import org.hibernate.query.spi.SelectQueryPlan;
import org.hibernate.query.spi.SimpleHqlInterpretationImpl;
import org.hibernate.query.sql.spi.ParameterInterpretation;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.stat.spi.StatisticsImplementor;

import org.jboss.logging.Logger;

/**
 * Standard QueryInterpretationCache implementation
 *
 * @author Steve Ebersole
 */
public class QueryInterpretationCacheStandardImpl implements QueryInterpretationCache {
	private static final Logger log = QueryLogging.subLogger( "plan.cache" );

	/**
	 * the cache of the actual plans...
	 */
	private final BoundedConcurrentHashMap<Key, QueryPlan> queryPlanCache;

	private final BoundedConcurrentHashMap<String, HqlInterpretation> hqlInterpretationCache;
	private final BoundedConcurrentHashMap<String, ParameterInterpretation> nativeQueryParamCache;
	private final Supplier<StatisticsImplementor> statisticsSupplier;

	public QueryInterpretationCacheStandardImpl(int maxQueryPlanCount, Supplier<StatisticsImplementor> statisticsSupplier) {
		log.debugf( "Starting QueryPlanCache(%s)", maxQueryPlanCount );

		this.queryPlanCache = new BoundedConcurrentHashMap<>( maxQueryPlanCount, 20, BoundedConcurrentHashMap.Eviction.LIRS );
		this.hqlInterpretationCache = new BoundedConcurrentHashMap<>( maxQueryPlanCount, 20, BoundedConcurrentHashMap.Eviction.LIRS );
		this.nativeQueryParamCache = new BoundedConcurrentHashMap<>( maxQueryPlanCount, 20, BoundedConcurrentHashMap.Eviction.LIRS );
		this.statisticsSupplier = statisticsSupplier;
	}

	@Override
	public int getNumberOfCachedHqlInterpretations() {
		return hqlInterpretationCache.size();
	}

	@Override
	public int getNumberOfCachedQueryPlans() {
		return queryPlanCache.size();
	}

	@Override
	public <R> SelectQueryPlan<R> resolveSelectQueryPlan(
			Key key,
			Supplier<SelectQueryPlan<R>> creator) {
		log.tracef( "QueryPlan#getSelectQueryPlan(%s)", key );
		final StatisticsImplementor statistics = statisticsSupplier.get();
		final boolean stats = statistics.isStatisticsEnabled();

		@SuppressWarnings("unchecked")
		final SelectQueryPlan<R> cached = (SelectQueryPlan<R>) queryPlanCache.get( key );
		if ( cached != null ) {
			if ( stats ) {
				statistics.queryPlanCacheHit( key.getQueryString() );
			}
			return cached;
		}

		final SelectQueryPlan<R> plan = creator.get();
		queryPlanCache.put( key.prepareForStore(), plan );
		if ( stats ) {
			statistics.queryPlanCacheMiss( key.getQueryString() );
		}
		return plan;
	}

	@Override
	public NonSelectQueryPlan getNonSelectQueryPlan(Key key) {
		log.tracef( "QueryPlan#getNonSelectQueryPlan(%s)", key );
		return null;
	}

	@Override
	public void cacheNonSelectQueryPlan(Key key, NonSelectQueryPlan plan) {
		log.tracef( "QueryPlan#cacheNonSelectQueryPlan(%s)", key );
	}

	@Override
	public HqlInterpretation resolveHqlInterpretation(
			String queryString,
			Class<?> expectedResultType,
			Function<String, SqmStatement<?>> creator) {
		log.tracef( "QueryPlan#resolveHqlInterpretation( `%s` )", queryString );

		final String cacheKey;
		if ( expectedResultType != null
				&& ( expectedResultType.isArray() || Tuple.class.isAssignableFrom( expectedResultType ) ) ) {
			cacheKey = "multi_" + queryString;
		}
		else {
			cacheKey = queryString;
		}


		final HqlInterpretation existing = hqlInterpretationCache.get( cacheKey );
		if ( existing != null ) {
			final StatisticsImplementor statistics = statisticsSupplier.get();
			if ( statistics.isStatisticsEnabled() ) {
				statistics.queryPlanCacheHit( queryString );
			}
			return existing;
		}

		final HqlInterpretation hqlInterpretation = createHqlInterpretation( queryString, creator, statisticsSupplier );
		hqlInterpretationCache.put( cacheKey, hqlInterpretation );
		return hqlInterpretation;
	}

	protected static HqlInterpretation createHqlInterpretation(
			String queryString,
			Function<String, SqmStatement<?>> creator,
			Supplier<StatisticsImplementor> statisticsSupplier) {
		final StatisticsImplementor statistics = statisticsSupplier.get();
		final boolean stats = statistics.isStatisticsEnabled();
		final long startTime = ( stats ) ? System.nanoTime() : 0L;

		final SqmStatement<?> sqmStatement = creator.apply( queryString );
		final ParameterMetadataImplementor parameterMetadata;
		final DomainParameterXref domainParameterXref;

		if ( sqmStatement.getSqmParameters().isEmpty() ) {
			domainParameterXref = DomainParameterXref.empty();
			parameterMetadata = ParameterMetadataImpl.EMPTY;
		}
		else {
			domainParameterXref = DomainParameterXref.from( sqmStatement );
			parameterMetadata = new ParameterMetadataImpl( domainParameterXref.getQueryParameters() );
		}

		if ( stats ) {
			final long endTime = System.nanoTime();
			final long microseconds = TimeUnit.MICROSECONDS.convert( endTime - startTime, TimeUnit.NANOSECONDS );
			statistics.queryCompiled( queryString, microseconds );
		}

		return new SimpleHqlInterpretationImpl( sqmStatement, parameterMetadata, domainParameterXref );
	}

	@Override
	public ParameterInterpretation resolveNativeQueryParameters(
			String queryString,
			Function<String, ParameterInterpretation> creator) {
		log.tracef( "QueryPlan#resolveNativeQueryParameters(%s)", queryString );
		return nativeQueryParamCache.computeIfAbsent(
				queryString,
				s -> {
					final ParameterInterpretation interpretation = creator.apply( queryString );
					log.debugf( "Creating and caching NativeQuery ParameterInterpretation - %s", interpretation );
					return interpretation;
				}
		);
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public void close() {
		// todo (6.0) : clear maps/caches and LOG
		hqlInterpretationCache.clear();
		nativeQueryParamCache.clear();
		queryPlanCache.clear();
	}

}
