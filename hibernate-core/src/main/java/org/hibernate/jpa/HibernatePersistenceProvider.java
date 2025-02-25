/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.spi.LoadState;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.ProviderUtil;

import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.jpa.boot.internal.ParsedPersistenceXmlDescriptor;
import org.hibernate.jpa.boot.internal.PersistenceXmlParser;
import org.hibernate.jpa.boot.spi.Bootstrap;
import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder;
import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;
import org.hibernate.jpa.boot.spi.ProviderChecker;
import org.hibernate.jpa.internal.util.PersistenceUtilHelper;

import org.jboss.logging.Logger;

/**
 * The Hibernate {@link PersistenceProvider} implementation
 *
 * @author Gavin King
 * @author Steve Ebersole
 * @author Brett Meyer
 */
public class HibernatePersistenceProvider implements PersistenceProvider {
	private static final Logger log = Logger.getLogger( HibernatePersistenceProvider.class );

	private final PersistenceUtilHelper.MetadataCache cache = new PersistenceUtilHelper.MetadataCache();
	
	/**
	 * {@inheritDoc}
	 *
	 * @implSpec Per the specification, the values passed as {@code properties} override values found in {@code persistence.xml}
	 */
	@Override
	public EntityManagerFactory createEntityManagerFactory(String persistenceUnitName, Map properties) {
		log.tracef( "Starting createEntityManagerFactory for persistenceUnitName %s", persistenceUnitName );
		final EntityManagerFactoryBuilder builder = getEntityManagerFactoryBuilderOrNull( persistenceUnitName, properties );
		if ( builder == null ) {
			log.trace( "Could not obtain matching EntityManagerFactoryBuilder, returning null" );
			return null;
		}
		return builder.build();
	}

	protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilderOrNull(String persistenceUnitName, Map<?,?> properties) {
		return getEntityManagerFactoryBuilderOrNull( persistenceUnitName, properties, null, null );
	}

	protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilderOrNull(String persistenceUnitName, Map<?,?> properties,
			ClassLoader providedClassLoader) {
		return getEntityManagerFactoryBuilderOrNull( persistenceUnitName, properties, providedClassLoader, null );
	}

	protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilderOrNull(String persistenceUnitName, Map<?,?> properties,
			ClassLoaderService providedClassLoaderService) {
		return getEntityManagerFactoryBuilderOrNull( persistenceUnitName, properties, null, providedClassLoaderService );
	}

	private EntityManagerFactoryBuilder getEntityManagerFactoryBuilderOrNull(String persistenceUnitName, Map<?,?> properties,
			ClassLoader providedClassLoader, ClassLoaderService providedClassLoaderService) {
		log.tracef( "Attempting to obtain correct EntityManagerFactoryBuilder for persistenceUnitName : %s", persistenceUnitName );

		final Map<?,?> integration = wrap( properties );
		final List<ParsedPersistenceXmlDescriptor> units;
		try {
			units = PersistenceXmlParser.locatePersistenceUnits( integration );
		}
		catch (Exception e) {
			log.debug( "Unable to locate persistence units", e );
			throw new PersistenceException( "Unable to locate persistence units", e );
		}

		log.debugf( "Located and parsed %s persistence units; checking each", units.size() );

		if ( persistenceUnitName == null && units.size() > 1 ) {
			// no persistence-unit name to look for was given and we found multiple persistence-units
			throw new PersistenceException( "No name provided and multiple persistence units found" );
		}

		for ( ParsedPersistenceXmlDescriptor persistenceUnit : units ) {
			if ( log.isDebugEnabled() ) {
				log.debugf(
						"Checking persistence-unit [name=%s, explicit-provider=%s] against incoming persistence unit name [%s]",
						persistenceUnit.getName(),
						persistenceUnit.getProviderClassName(),
						persistenceUnitName
				);
			}

			final boolean matches = persistenceUnitName == null || persistenceUnit.getName().equals( persistenceUnitName );
			if ( !matches ) {
				log.debug( "Excluding from consideration due to name mis-match" );
				continue;
			}

			// See if we (Hibernate) are the persistence provider
			if ( ! ProviderChecker.isProvider( persistenceUnit, properties ) ) {
				log.debug( "Excluding from consideration due to provider mis-match" );
				continue;
			}

			if (providedClassLoaderService != null) {
				return getEntityManagerFactoryBuilder( persistenceUnit, integration, providedClassLoaderService );
			}
			else {
				return getEntityManagerFactoryBuilder( persistenceUnit, integration, providedClassLoader );
			}
		}

		log.debug( "Found no matching persistence units" );
		return null;
	}

	protected static Map<?,?> wrap(Map<?,?> properties) {
		return properties == null ? Collections.emptyMap() : Collections.unmodifiableMap( properties );
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Note: per-spec, the values passed as {@code properties} override values found in {@link PersistenceUnitInfo}
	 */
	@Override
	public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map properties) {
		if ( log.isTraceEnabled() ) {
			log.tracef( "Starting createContainerEntityManagerFactory : %s", info.getPersistenceUnitName() );
		}

		return getEntityManagerFactoryBuilder( info, properties ).build();
	}

	@Override
	public void generateSchema(PersistenceUnitInfo info, Map map) {
		if ( log.isTraceEnabled() ) {
			log.tracef( "Starting generateSchema : PUI.name=%s", info.getPersistenceUnitName() );
		}

		final EntityManagerFactoryBuilder builder = getEntityManagerFactoryBuilder( info, map );
		builder.generateSchema();
	}

	@Override
	public boolean generateSchema(String persistenceUnitName, Map map) {
		log.tracef( "Starting generateSchema for persistenceUnitName %s", persistenceUnitName );

		final EntityManagerFactoryBuilder builder = getEntityManagerFactoryBuilderOrNull( persistenceUnitName, map );
		if ( builder == null ) {
			log.trace( "Could not obtain matching EntityManagerFactoryBuilder, returning false" );
			return false;
		}
		builder.generateSchema();
		return true;
	}

	protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilder(PersistenceUnitInfo info, Map<?,?> integration) {
		return Bootstrap.getEntityManagerFactoryBuilder( info, integration );
	}

	protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilder(PersistenceUnitDescriptor persistenceUnitDescriptor,
			Map<?,?> integration, ClassLoader providedClassLoader) {
		return Bootstrap.getEntityManagerFactoryBuilder( persistenceUnitDescriptor, integration, providedClassLoader );
	}

	protected EntityManagerFactoryBuilder getEntityManagerFactoryBuilder(PersistenceUnitDescriptor persistenceUnitDescriptor,
			Map<?,?> integration, ClassLoaderService providedClassLoaderService) {
		return Bootstrap.getEntityManagerFactoryBuilder( persistenceUnitDescriptor, integration, providedClassLoaderService );
	}

	private final ProviderUtil providerUtil = new ProviderUtil() {
		@Override
		public LoadState isLoadedWithoutReference(Object proxy, String property) {
			return PersistenceUtilHelper.isLoadedWithoutReference( proxy, property, cache );
		}
		@Override
		public LoadState isLoadedWithReference(Object proxy, String property) {
			return PersistenceUtilHelper.isLoadedWithReference( proxy, property, cache );
		}
		@Override
		public LoadState isLoaded(Object o) {
			return PersistenceUtilHelper.isLoaded(o);
		}
	};

	@Override
	public ProviderUtil getProviderUtil() {
		return providerUtil;
	}

}
