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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Form of {@link Type} for describing the id of an id-bag mapping
 *
 * @since 6.0
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface CollectionIdType {

	/**
	 * The custom type implementor class
	 *
	 * @see Type#value
	 */
	Class<? extends UserType<?>> value();

	/**
	 * Parameters to be injected into the custom type after
	 * it is instantiated.
	 *
	 * The type should implement {@link org.hibernate.usertype.ParameterizedType}
	 * to receive the parameters
	 *
	 * @see Type#parameters
	 */
	Parameter[] parameters() default {};
}
