/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.persister.entity.mutation;

import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.jdbc.batch.internal.BasicBatchKey;
import org.hibernate.engine.jdbc.mutation.JdbcValueBindings;
import org.hibernate.engine.jdbc.mutation.MutationExecutor;
import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.engine.jdbc.mutation.group.PreparedStatementDetails;
import org.hibernate.engine.jdbc.mutation.spi.MutationExecutorService;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EntityRowIdMapping;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.sql.model.MutationOperationGroup;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.ast.builder.MutationGroupBuilder;
import org.hibernate.sql.model.ast.builder.RestrictedTableMutationBuilder;
import org.hibernate.sql.model.ast.builder.TableDeleteBuilder;
import org.hibernate.sql.model.ast.builder.TableDeleteBuilderSkipped;
import org.hibernate.sql.model.ast.builder.TableDeleteBuilderStandard;

import static org.hibernate.engine.jdbc.mutation.internal.ModelMutationHelper.identifiedResultsCheck;

/**
 * Coordinates the deleting of an entity.
 *
 * @see #coordinateDelete
 *
 * @author Steve Ebersole
 */
public class DeleteCoordinator extends AbstractMutationCoordinator {
	private final MutationOperationGroup staticOperationGroup;
	private final BasicBatchKey batchKey;

	private MutationOperationGroup noVersionDeleteGroup;

	public DeleteCoordinator(AbstractEntityPersister entityPersister, SessionFactoryImplementor factory) {
		super( entityPersister, factory );

		this.staticOperationGroup = generateOperationGroup( null, true, null );
		this.batchKey = new BasicBatchKey( entityPersister.getEntityName() + "#DELETE" );

		if ( !entityPersister.isVersioned() ) {
			noVersionDeleteGroup = staticOperationGroup;
		}
	}

	public MutationOperationGroup getStaticDeleteGroup() {
		return staticOperationGroup;
	}

	@SuppressWarnings("unused")
	public BasicBatchKey getBatchKey() {
		return batchKey;
	}

	public void coordinateDelete(
			Object entity,
			Object id,
			Object version,
			SharedSessionContractImplementor session) {

		boolean isImpliedOptimisticLocking = entityPersister().optimisticLockStyle().isAllOrDirty();

		final PersistenceContext persistenceContext = session.getPersistenceContextInternal();
		final EntityEntry entry = persistenceContext.getEntry( entity );
		final Object[] loadedState = entry != null && isImpliedOptimisticLocking ? entry.getLoadedState() : null;
		final Object rowId = entry != null && entityPersister().hasRowId() ? entry.getRowId() : null;

		if ( ( isImpliedOptimisticLocking && loadedState != null ) || rowId != null ) {
			doDynamicDelete( entity, id, rowId, loadedState, session );
		}
		else {
			doStaticDelete( entity, id, entry == null ? null : entry.getLoadedState(), version, session );
		}
	}

	protected void doDynamicDelete(
			Object entity,
			Object id,
			Object rowId,
			Object[] loadedState,
			SharedSessionContractImplementor session) {
		final MutationOperationGroup operationGroup = generateOperationGroup( loadedState, true, session );

		final MutationExecutor mutationExecutor = executor( session, operationGroup );

		operationGroup.forEachOperation( (position, mutation) -> {
			if ( mutation != null ) {
				final String tableName = mutation.getTableDetails().getTableName();
				mutationExecutor.getPreparedStatementDetails( tableName );
			}
		} );

		applyLocking( null, loadedState, mutationExecutor, session );

		applyId( id, rowId, mutationExecutor, operationGroup, session );

		try {
			mutationExecutor.execute(
					entity,
					null,
					null,
					(statementDetails, affectedRowCount, batchPosition) -> identifiedResultsCheck(
							statementDetails,
							affectedRowCount,
							batchPosition,
							entityPersister(),
							id,
							factory()
					),
					session
			);
		}
		finally {
			mutationExecutor.release();
		}
	}

	private MutationExecutor executor(SharedSessionContractImplementor session, MutationOperationGroup group) {
		return session.getFactory()
				.getServiceRegistry()
				.getService( MutationExecutorService.class )
				.createExecutor( () -> batchKey, group, session );
	}

	protected void applyLocking(
			Object version,
			Object[] loadedState,
			MutationExecutor mutationExecutor,
			SharedSessionContractImplementor session) {
		final JdbcValueBindings jdbcValueBindings = mutationExecutor.getJdbcValueBindings();
		final OptimisticLockStyle optimisticLockStyle = entityPersister().optimisticLockStyle();
		switch ( optimisticLockStyle ) {
			case VERSION:
				applyVersionLocking( version, session, jdbcValueBindings );
				break;
			case ALL:
			case DIRTY:
				applyAllOrDirtyLocking( loadedState, session, jdbcValueBindings );
				break;
		}
	}

	private void applyAllOrDirtyLocking(
			Object[] loadedState,
			SharedSessionContractImplementor session,
			JdbcValueBindings jdbcValueBindings) {
		if ( loadedState != null ) {
			final boolean[] versionability = entityPersister().getPropertyVersionability();
			entityPersister().forEachAttributeMapping( (attributeIndex, attribute) -> {
				if ( versionability[attributeIndex] && attribute instanceof SingularAttributeMapping ) {
					final Object loadedValue = loadedState[attributeIndex];
					if (loadedValue != null) {
						attribute.breakDownJdbcValues(
								loadedValue,
								(jdbcValue, jdbcValueMapping) -> {
									if ( jdbcValue == null ) {
										// presumably the SQL was generated with `is null`
										return;
									}

									jdbcValueBindings.bindValue(
											jdbcValue,
											entityPersister().getAttributeMutationTableName( attributeIndex ),
											jdbcValueMapping.getSelectionExpression(),
											ParameterUsage.RESTRICT,
											session
									);
								},
								session
						);
					}
				}
			} );
		}
	}

	private void applyVersionLocking(
			Object version,
			SharedSessionContractImplementor session,
			JdbcValueBindings jdbcValueBindings) {
		if ( version != null && entityPersister().getVersionMapping() != null ) {
			jdbcValueBindings.bindValue(
					version,
					entityPersister().getIdentifierTableMapping().getTableName(),
					entityPersister().getVersionMapping().getSelectionExpression(),
					ParameterUsage.RESTRICT,
					session
			);
		}
	}

	protected void applyId(
			Object id,
			Object rowId,
			MutationExecutor mutationExecutor,
			MutationOperationGroup operationGroup,
			SharedSessionContractImplementor session) {
		final JdbcValueBindings jdbcValueBindings = mutationExecutor.getJdbcValueBindings();
		final EntityRowIdMapping rowIdMapping = entityPersister().getRowIdMapping();

		operationGroup.forEachOperation( (position, jdbcMutation) -> {
			final EntityTableMapping tableDetails = (EntityTableMapping) jdbcMutation.getTableDetails();
			breakDownIdJdbcValues( id, rowId, session, jdbcValueBindings, rowIdMapping, tableDetails );
			final PreparedStatementDetails statementDetails = mutationExecutor.getPreparedStatementDetails( tableDetails.getTableName() );
			if ( statementDetails != null ) {
				// force creation of the PreparedStatement
				//noinspection resource
				statementDetails.resolveStatement();
			}
		} );
	}

	private static void breakDownIdJdbcValues(
			Object id,
			Object rowId,
			SharedSessionContractImplementor session,
			JdbcValueBindings jdbcValueBindings,
			EntityRowIdMapping rowIdMapping,
			EntityTableMapping tableDetails) {
		if ( rowId != null && rowIdMapping != null && tableDetails.isIdentifierTable() ) {
			jdbcValueBindings.bindValue(
					rowId,
					tableDetails.getTableName(),
					rowIdMapping.getRowIdName(),
					ParameterUsage.RESTRICT,
					session
			);
		}
		else {
			tableDetails.getKeyMapping().breakDownKeyJdbcValues(
					id,
					(jdbcValue, columnMapping) -> jdbcValueBindings.bindValue(
							jdbcValue,
							tableDetails.getTableName(),
							columnMapping.getColumnName(),
							ParameterUsage.RESTRICT,
							session
					),
					session
			);
		}
	}

	protected void doStaticDelete(
			Object entity,
			Object id,
			Object[] loadedState,
			Object version,
			SharedSessionContractImplementor session) {

		final boolean applyVersion;
		final MutationOperationGroup operationGroupToUse;
		if ( entity == null ) {
			applyVersion = false;
			operationGroupToUse = resolveNoVersionDeleteGroup( session );
		}
		else {
			applyVersion = true;
			operationGroupToUse = staticOperationGroup;
		}

		final MutationExecutor mutationExecutor = executor( session, operationGroupToUse );

		staticOperationGroup.forEachOperation( (position, mutation) -> {
			if ( mutation != null ) {
				mutationExecutor.getPreparedStatementDetails( mutation.getTableDetails().getTableName() );
			}
		} );

		if ( applyVersion ) {
			applyLocking( version, null, mutationExecutor, session );
		}
		final JdbcValueBindings jdbcValueBindings = mutationExecutor.getJdbcValueBindings();

		bindPartitionColumnValueBindings( loadedState, session, jdbcValueBindings );

		applyId( id, null, mutationExecutor, staticOperationGroup, session );

		mutationExecutor.execute(
				entity,
				null,
				null,
				(statementDetails, affectedRowCount, batchPosition) -> identifiedResultsCheck(
						statementDetails,
						affectedRowCount,
						batchPosition,
						entityPersister(),
						id,
						factory()
				),
				session
		);

		mutationExecutor.release();
	}

	protected MutationOperationGroup resolveNoVersionDeleteGroup(SharedSessionContractImplementor session) {
		if ( noVersionDeleteGroup == null ) {
			noVersionDeleteGroup = generateOperationGroup( null, false, session );
		}

		return noVersionDeleteGroup;
	}

	protected MutationOperationGroup generateOperationGroup(
			Object[] loadedState,
			boolean applyVersion,
			SharedSessionContractImplementor session) {
		final MutationGroupBuilder deleteGroupBuilder = new MutationGroupBuilder( MutationType.DELETE, entityPersister() );

		entityPersister().forEachMutableTableReverse( (tableMapping) -> {
			final TableDeleteBuilder tableDeleteBuilder = tableMapping.isCascadeDeleteEnabled()
					? new TableDeleteBuilderSkipped( tableMapping )
					: new TableDeleteBuilderStandard( entityPersister(), tableMapping, factory() );
			deleteGroupBuilder.addTableDetailsBuilder( tableDeleteBuilder );
		} );

		applyTableDeleteDetails( deleteGroupBuilder, loadedState, applyVersion, session );

		return createOperationGroup( null, deleteGroupBuilder.buildMutationGroup() );
	}

	private void applyTableDeleteDetails(
			MutationGroupBuilder deleteGroupBuilder,
			Object[] loadedState,
			boolean applyVersion,
			SharedSessionContractImplementor session) {
		// first, the table key column(s)
		deleteGroupBuilder.forEachTableMutationBuilder( (builder) -> {
			final EntityTableMapping tableMapping = (EntityTableMapping) builder.getMutatingTable().getTableMapping();
			final TableDeleteBuilder tableDeleteBuilder = (TableDeleteBuilder) builder;
			applyKeyDetails( tableDeleteBuilder, tableMapping );
		} );

		if ( applyVersion ) {
			// apply any optimistic locking
			applyOptimisticLocking( deleteGroupBuilder, loadedState, session );
			if ( entityPersister().hasPartitionedSelectionMapping() ) {
				entityPersister().forEachSelectable(
						(selectionIndex, selectableMapping) -> {
							if ( selectableMapping.isPartitioned() ) {
								final String tableNameForMutation =
										entityPersister().physicalTableNameForMutation( selectableMapping );
								final RestrictedTableMutationBuilder<?, ?> rootTableMutationBuilder =
										deleteGroupBuilder.findTableDetailsBuilder( tableNameForMutation );
								rootTableMutationBuilder.addKeyRestrictionLeniently( selectableMapping );
							}
						}
				);
			}
		}
		// todo (6.2) : apply where + where-fragments
	}

	private static void applyKeyDetails(TableDeleteBuilder tableDeleteBuilder, EntityTableMapping tableMapping) {
		tableMapping.getKeyMapping().forEachKeyColumn(
				(columnMapping) -> tableDeleteBuilder.addKeyRestriction(
						columnMapping.getColumnName(),
						columnMapping.getWriteExpression(),
						columnMapping.getJdbcMapping()
				)
		);
	}

	protected void applyOptimisticLocking(
			MutationGroupBuilder mutationGroupBuilder,
			Object[] loadedState,
			SharedSessionContractImplementor session) {
		final OptimisticLockStyle optimisticLockStyle = entityPersister().optimisticLockStyle();
		if ( optimisticLockStyle.isVersion() && entityPersister().getVersionMapping() != null ) {
			applyVersionBasedOptLocking( mutationGroupBuilder );
		}
		else if ( loadedState != null && entityPersister().optimisticLockStyle().isAllOrDirty() ) {
			applyNonVersionOptLocking(
					optimisticLockStyle,
					mutationGroupBuilder,
					loadedState,
					session
			);
		}
	}

	protected void applyVersionBasedOptLocking(MutationGroupBuilder mutationGroupBuilder) {
		assert entityPersister().optimisticLockStyle() == OptimisticLockStyle.VERSION;
		assert entityPersister().getVersionMapping() != null;

		final String tableNameForMutation = entityPersister().physicalTableNameForMutation( entityPersister().getVersionMapping() );
		final RestrictedTableMutationBuilder<?,?> rootTableMutationBuilder = mutationGroupBuilder.findTableDetailsBuilder( tableNameForMutation );
		rootTableMutationBuilder.addOptimisticLockRestriction( entityPersister().getVersionMapping() );
	}

	protected void applyNonVersionOptLocking(
			OptimisticLockStyle lockStyle,
			MutationGroupBuilder mutationGroupBuilder,
			Object[] loadedState,
			SharedSessionContractImplementor session) {
		assert loadedState != null;
		assert lockStyle.isAllOrDirty();
		assert entityPersister().optimisticLockStyle().isAllOrDirty();
		assert session != null;

		final boolean[] versionability = entityPersister().getPropertyVersionability();
		entityPersister().forEachAttributeMapping( (attributeIndex, attribute) -> {
			// only makes sense to lock on singular attributes which are not excluded from optimistic locking
			if ( versionability[attributeIndex] && attribute instanceof SingularAttributeMapping ) {
				breakDownJdbcValues( mutationGroupBuilder, session, attribute, loadedState[attributeIndex] );
			}
		} );
	}

	private void breakDownJdbcValues(
			MutationGroupBuilder mutationGroupBuilder,
			SharedSessionContractImplementor session,
			AttributeMapping attribute,
			Object loadedValue) {
		attribute.breakDownJdbcValues(
				loadedValue,
				(jdbcValue, columnMapping) -> {
					final String physicalTableName = entityPersister().physicalTableNameForMutation( columnMapping );
					final RestrictedTableMutationBuilder<?, ?> tableMutationBuilder =
							mutationGroupBuilder.findTableDetailsBuilder( physicalTableName );
					if ( tableMutationBuilder != null ) {
						if (jdbcValue == null) {
							tableMutationBuilder.addNullOptimisticLockRestriction( columnMapping );
						}
						else {
							tableMutationBuilder.addOptimisticLockRestriction( columnMapping );
						}
					}
					// else there is no actual delete statement for that table,
					// generally indicates we have an on-delete=cascade situation
				},
				session
		);
	}
}
