/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.convert.internal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.PersistenceException;

import org.hibernate.boot.model.convert.spi.JpaAttributeConverterCreationContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.metamodel.model.convert.spi.JpaAttributeConverter;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.type.descriptor.converter.AttributeConverterMutabilityPlanImpl;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.hibernate.type.descriptor.java.spi.RegistryHelper;
import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * Standard implementation of JpaAttributeConverter
 *
 * @author Steve Ebersole
 */
public class JpaAttributeConverterImpl<O,R> implements JpaAttributeConverter<O,R> {
	private final ManagedBean<? extends AttributeConverter<O,R>> attributeConverterBean;
	private final JavaType<? extends AttributeConverter<O, R>> converterJtd;
	private final JavaType<O> domainJtd;
	private final JavaType<R> jdbcJtd;

	public JpaAttributeConverterImpl(
			ManagedBean<? extends AttributeConverter<O, R>> attributeConverterBean,
			JavaType<? extends AttributeConverter<O,R>> converterJtd,
			JavaType<O> domainJtd,
			JavaType<R> jdbcJtd) {
		this.attributeConverterBean = attributeConverterBean;
		this.converterJtd = converterJtd;
		this.domainJtd = domainJtd;
		this.jdbcJtd = jdbcJtd;
	}

	public JpaAttributeConverterImpl(
			ManagedBean<? extends AttributeConverter<O,R>> attributeConverterBean,
			JavaType<? extends AttributeConverter<O,R>> converterJtd,
			Class<O> domainJavaType,
			Class<R> jdbcJavaType,
			JpaAttributeConverterCreationContext context) {
		this.attributeConverterBean = attributeConverterBean;
		this.converterJtd = converterJtd;

		final JavaTypeRegistry jtdRegistry = context.getJavaTypeRegistry();

		jdbcJtd = jtdRegistry.getDescriptor( jdbcJavaType );
		//noinspection unchecked
		domainJtd = (JavaType<O>) jtdRegistry.resolveDescriptor(
				domainJavaType,
				() -> RegistryHelper.INSTANCE.createTypeDescriptor(
						domainJavaType,
						() -> {
							final Class<? extends AttributeConverter<O, R>> converterClass = attributeConverterBean.getBeanClass();
							final MutabilityPlan<Object> mutabilityPlan = RegistryHelper.INSTANCE.determineMutabilityPlan(
									converterClass,
									context.getTypeConfiguration()
							);

							if ( mutabilityPlan != null ) {
								return mutabilityPlan;
							}

							return new AttributeConverterMutabilityPlanImpl<>( this, true );
						},
						context.getTypeConfiguration()
				)
		);
	}

	@Override
	public ManagedBean<? extends AttributeConverter<O, R>> getConverterBean() {
		return attributeConverterBean;
	}

	@Override
	public O toDomainValue(R relationalForm) {
		try {
			return attributeConverterBean.getBeanInstance().convertToEntityAttribute( relationalForm );
		}
		catch (PersistenceException pe) {
			throw pe;
		}
		catch (RuntimeException re) {
			throw new PersistenceException( "Error attempting to apply AttributeConverter", re );
		}
	}

	@Override
	public R toRelationalValue(O domainForm) {
		try {
			return attributeConverterBean.getBeanInstance().convertToDatabaseColumn( domainForm );
		}
		catch (PersistenceException pe) {
			throw pe;
		}
		catch (RuntimeException re) {
			throw new PersistenceException( "Error attempting to apply AttributeConverter", re );
		}
	}

	@Override
	public String getCheckCondition(String columnName, JdbcType sqlType, Dialect dialect) {
		if ( BasicValueConverter.class.isAssignableFrom( attributeConverterBean.getBeanClass() ) ) {
			return ((BasicValueConverter<?, ?>) attributeConverterBean.getBeanInstance())
					.getCheckCondition( columnName, sqlType, dialect );
		}
		else {
			return null;
		}
	}

	@Override
	public String getSpecializedTypeDeclaration(JdbcType jdbcType, Dialect dialect) {
		if ( BasicValueConverter.class.isAssignableFrom( attributeConverterBean.getBeanClass() ) ) {
			return ((BasicValueConverter<?, ?>) attributeConverterBean.getBeanInstance())
					.getSpecializedTypeDeclaration( jdbcType, dialect );
		}
		else {
			return null;
		}
	}

	@Override
	public JavaType<? extends AttributeConverter<O, R>> getConverterJavaType() {
		return converterJtd;
	}

	@Override
	public JavaType<O> getDomainJavaType() {
		return domainJtd;
	}

	@Override
	public JavaType<R> getRelationalJavaType() {
		return jdbcJtd;
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}

		JpaAttributeConverterImpl<?, ?> that = (JpaAttributeConverterImpl<?, ?>) o;

		if ( !attributeConverterBean.equals( that.attributeConverterBean ) ) {
			return false;
		}
		if ( !converterJtd.equals( that.converterJtd ) ) {
			return false;
		}
		if ( !domainJtd.equals( that.domainJtd ) ) {
			return false;
		}
		return jdbcJtd.equals( that.jdbcJtd );
	}

	@Override
	public int hashCode() {
		int result = attributeConverterBean.hashCode();
		result = 31 * result + converterJtd.hashCode();
		result = 31 * result + domainJtd.hashCode();
		result = 31 * result + jdbcJtd.hashCode();
		return result;
	}
}
