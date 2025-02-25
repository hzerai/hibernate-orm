/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.usertype.UserType;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies a custom {@link UserType} for the annotated attribute mapping.
 * This annotation may be applied:
 * <ul>
 * <li>directly to a property or field of an entity to specify the custom
 *     type of the property or field,
 * <li>indirectly, as a meta-annotation of an annotation type that is then
 *     applied to various properties and fields, or
 * <li>by default, via a {@linkplain TypeRegistration registration}.
 * </ul>
 * For example, as an alternative to:
 * <pre>{@code
 * @Type(MonetaryAmountUserType.class)
 * BigDecimal amount;
 * }</pre>
 * we may define an annotation type:
 * <pre>{@code
 * @Retention(RUNTIME)
 * @Target({METHOD,FIELD})
 * @Type(MonetaryAmountUserType.class)
 * public @interface MonetaryAmount {}
 * }</pre>
 * and then write:
 * <pre>{@code
 * @MonetaryAmount
 * BigDecimal amount;
 * }</pre>
 * which is much cleaner.
 * <p>
 * The use of a {@code UserType} is usually mutually exclusive with the
 * compositional approach of {@link JavaType} and {@link JdbcType}.
 *
 * @see UserType
 * @see TypeRegistration
 * @see CompositeType
 */
@Target({METHOD, FIELD, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface Type {
	/**
	 * The implementation class which implements {@link UserType}.
	 */
	Class<? extends UserType<?>> value();

	/**
	 * Parameters to be injected into the custom type after it is
	 * instantiated. The {@link UserType} implementation must implement
	 * {@link org.hibernate.usertype.ParameterizedType} to receive the
	 * parameters.
	 */
	Parameter[] parameters() default {};
}
