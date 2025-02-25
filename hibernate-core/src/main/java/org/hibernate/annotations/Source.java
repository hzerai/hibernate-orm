/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

import org.hibernate.Remove;
import org.hibernate.generator.internal.SourceGeneration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates the source of timestamps for an entity
 * {@linkplain jakarta.persistence.Version version property} of
 * type {@link java.sql.Timestamp}:
 * <ul>
 * <li>{@link SourceType#VM} indicates that the virtual machine
 *     {@linkplain java.time.Clock#instant() current instance}
 *     is used, and
 * <li>{@link SourceType#DB} indicates that the database
 *     {@code current_timestamp} function should be used.
 * </ul>
 * For example, the following timestamp is generated by the
 * database:
 * <pre>
 * {@code
 * @Version @Source(DB)
 * private LocalDateTime version;
 * }
 * </pre>
 * This annotation is always used in conjunction with the JPA
 * {@link jakarta.persistence.Version @Version} annotation.
 *
 * @author Hardy Ferentschik
 *
 * @see jakarta.persistence.Version
 *
 * @deprecated use {@link CurrentTimestamp} instead
 */
@Deprecated(since = "6.2") @Remove
@Target({ METHOD, FIELD })
@Retention(RUNTIME)
@ValueGenerationType(generatedBy = SourceGeneration.class)
public @interface Source {
	/**
	 * The source of timestamps. By default, the {@linkplain
	 * SourceType#VM virtual machine} is the source.
	 */
	SourceType value() default SourceType.VM;
}
