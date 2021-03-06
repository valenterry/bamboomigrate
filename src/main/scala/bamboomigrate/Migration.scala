package bamboomigrate

import bamboomigrate.Transform.StepConstraint.OnlySteps
import bamboomigrate.Transform.{ApplyTransformationStep, transformationByAnyStep}
import bamboomigrate.TypelevelUtils.{LazyLeftFolder, LazyLeftScanner, getFieldValue}
import shapeless._
import shapeless.labelled.{FieldType, field}
import shapeless.ops.hlist.{Init, Last, LeftFolder, Patcher, Prepend}
import shapeless.ops.record.{Remover, Renamer}

import scala.annotation.implicitNotFound

/**
  * A typeclass which allows to convert an instance of one type to an instance of another type
  * @tparam From The instance which acts as the base
  * @tparam To The instance that should be created when applying the migration
  */
trait Migration[From, To] {
	/**
	  * Given an instance of type `From` turns it into an instances of type `To`
	  * @param from The instance which acts as the base
	  * @return The instance created by the migration process
	  */
	def migrate(from: From): To
}

object Migration {
	def apply[From, To](implicit migration: Migration[From, To]): Migration[From, To] = migration

	/**
	  * A helper to easily create a migration, given a function that does the conversion
	  */
	def instance[From, To](m: From => To): Migration[From, To] = new Migration[From, To] {
		def migrate(from: From): To = m(from)
	}

	/**
	  * If already given a migration, this helper will create a new migration by converting the target instance of the given migration into a new type
	  */
	def mapTo[From, OldTo, NewTo](old: Migration[From, OldTo], f: OldTo => NewTo): Migration[From, NewTo] =
		Migration.instance( (obj: From) => f(old.migrate(obj)))


	final class StartingFromPartialTypeApplication[BaseType] {
		def apply(steps: HNil) = HNil

		def apply[BaseTypeLG <: HList, FirstStep, StepList <: HList, FirstStepResult, GeneratedMigrations <: HList]
		(steps: FirstStep :: StepList)
		(implicit lgen: LabelledGeneric.Aux[BaseType, BaseTypeLG],
		 firstStep: ApplyTransformationStep.Aux[BaseTypeLG, FirstStep, transformationByAnyStep.type, FirstStepResult],
		 scanner: LazyLeftScanner.Aux[StepList, Migration[BaseType, FirstStepResult], migrationByAnyStep.type, GeneratedMigrations]
		): GeneratedMigrations = {
			val firstMigration: Migration[BaseType, FirstStepResult] = Migration.instance{(obj:BaseType) => firstStep.apply(lgen.to(obj), steps.head)}
			scanner.apply(steps.tail, firstMigration)
		}
	}
	/**
	  * Does the same as the `between` method, but the last migration will lead to an abstract LabelledGeneric which is the result from the transformations
	  * @return The list of migrations from base type to the abstract LabelledGeneric target
	  */
	def startingFrom[BaseType] = new StartingFromPartialTypeApplication[BaseType]

	final class FromTargetPartialTypeApplication[TargetType] {
		def apply[TargetTypeLG <: HList, Second, Migrations <: HList, PenultimateType, MigrationsWithoutLast <: HList, GeneratedMigrations <: HList]
		(migrations: Migrations)
		(implicit
		 targetLG: LabelledGeneric.Aux[TargetType, TargetTypeLG],
		 last: Last.Aux[Migrations, Migration[PenultimateType, TargetTypeLG]],
		 init: Init.Aux[Migrations, MigrationsWithoutLast],
		 prepend: Prepend.Aux[MigrationsWithoutLast, Migration[PenultimateType, TargetType] :: HNil, GeneratedMigrations]
		): GeneratedMigrations = prepend(init(migrations), Migration.instance{(obj:PenultimateType) => targetLG.from(last(migrations).migrate(obj)) } :: HNil)
	}
	/**
	  * Replaces the last migration of a list of migrations (where the last migrations leads to an abstract LabelledGeneric) with a new migration that,
	  * instead, leads to to the provided TargetType
	  * @tparam TargetType The TargetType to which the last migration should lead to
	  * @return The new list of migrations
	  */
	def fromTarget[TargetType] = new FromTargetPartialTypeApplication[TargetType]


	final class BetweenPartialTypeApplication[BaseType, TargetType] {
		/**
		  * Given one transformation step (as an HList), returns one migration (as an HList) to migrate the base type to the target type
		  * @return The migration from base type to target type
		  * Will fail if the provided transformation step will not transform the base type into the target type
		  */
		def apply[BaseTypeLG <: HList, TargetTypeLG <: HList, FirstStep <: TransformStep, Result]
		(steps: FirstStep :: HNil)
		(implicit migrationsBetween: MigrationsBetween.Aux[BaseType, TargetType, FirstStep :: HNil, Result]): Result = migrationsBetween.apply(steps)

		/**
		  * Given transformation steps, returns a list of migrations (as an HList) to step by step migrate the base type to the target type
		  * @return The list of migrations from base type to target type
		  * Will fail if the provided transformation steps will not transform the base type into the target type after beeing applied step by step (starting with the first step in the list)
		  */
		def apply[BaseTypeLG <: HList, Start, Second, FirstStep, StepList <: HList : OnlySteps, NextLG, ResultingMigrations <: HList, LastFrom, LastTo <: HList, ReplaceResult, Replaced, Result]
		(steps: FirstStep :: StepList)
		(implicit migrationsBetween: MigrationsBetween.Aux[BaseType, TargetType, FirstStep :: StepList, Result]
		): Result = migrationsBetween(steps)
	}

	/**
	  * Creates a list (HList) of migrations which can be used step by step to migrate from the base type to the target type
	  * @tparam BaseType The base type where the first migration starts from
	  * @tparam TargetType The target type where the last migration ends
	  * @return A BetweenPartialTypeApplication object (used for partial type application)
	  */
	def between[BaseType, TargetType] = new BetweenPartialTypeApplication[BaseType, TargetType]


	/**
	  * Creates a list (HList) of migrations between the given `BaseType` and `TargetType` by using a `StepList` consisting of TransformationSteps
	  */
	@implicitNotFound("Unable to migrate from base ${BaseType} to target ${TargetType} using steps ${StepList}. Check that transforming the specified base step by step (in order) of the provided steps really leads to the specified base")
	trait MigrationsBetween[BaseType, TargetType, StepList <: HList] extends DepFn1[StepList] with Serializable

	trait LowPriorityMigrationsBetween {
		@implicitNotFound("Unable to migrate from base ${BaseType} to target ${TargetType} using steps ${StepList}. Check that transforming the specified base step by step (in order) of the provided steps really leads to the specified base")
		type Aux[BaseType, TargetType, StepList <: HList, Out0] = MigrationsBetween[BaseType, TargetType, StepList] { type Out = Out0 }

		implicit def multipleSteps[BaseType, TargetType, BaseTypeLG <: HList, FirstStep, StepList <: HList : OnlySteps, NextLG, ResultingMigrations <: HList, LastFrom, LastTo <: HList, ReplaceResult, Replaced]
		(implicit lgen: Lazy[LabelledGeneric.Aux[BaseType, BaseTypeLG]],
		 stepApplier: ApplyTransformationStep.Aux[BaseTypeLG, FirstStep, transformationByAnyStep.type, NextLG],
		 scanner: LazyLeftScanner.Aux[StepList, Migration[BaseType, NextLG], migrationByAnyStep.type, ResultingMigrations],
		 getLastMigration: Last.Aux[ResultingMigrations, Migration[LastFrom, LastTo]],
		 replaceModifier: shapeless.ops.hlist.Modifier.Aux[ResultingMigrations, Migration[LastFrom, LastTo], Migration[LastFrom, TargetType], (Replaced, ReplaceResult)],
		 targetGen: Lazy[LabelledGeneric.Aux[TargetType, LastTo]]
		): Aux[BaseType, TargetType, FirstStep :: StepList, ReplaceResult] =
			new MigrationsBetween[BaseType, TargetType, FirstStep :: StepList] {
				type Out = ReplaceResult
				def apply(steps : FirstStep :: StepList): Out = {
					val firstMigration:Migration[BaseType, NextLG] = Migration.instance { (base: BaseType) => stepApplier(lgen.value.to(base), steps.head) }
					val migrationsResult: ResultingMigrations = scanner.apply(steps.tail, firstMigration)
					val transformLastMigration: Migration[LastFrom, LastTo] => Migration[LastFrom, TargetType] =
						(lastMigration: Migration[LastFrom, LastTo]) => Migration.mapTo(lastMigration, (obj: LastTo) => targetGen.value.from(obj))
					val migrationsWithLastMigrationToTarget:ReplaceResult = replaceModifier(migrationsResult, transformLastMigration)._2
					migrationsWithLastMigrationToTarget
				}
			}
	}

	object MigrationsBetween extends LowPriorityMigrationsBetween {
		def apply[BaseType, TargetType, StepList <: HList]
		(implicit migrationsBetween: MigrationsBetween[BaseType, TargetType, StepList]):
		Aux[BaseType, TargetType, StepList, migrationsBetween.Out] = migrationsBetween

		implicit def oneStep[BaseType, TargetType, BaseTypeLG <: HList, TargetTypeLG <: HList, FirstStep <: TransformStep]
		(implicit lgen: Lazy[LabelledGeneric.Aux[BaseType, BaseTypeLG]],
		 onlyStepApplier: ApplyTransformationStep.Aux[BaseTypeLG, FirstStep, transformationByAnyStep.type, TargetTypeLG],
		 targetGen: Lazy[LabelledGeneric.Aux[TargetType, TargetTypeLG]]): Aux[BaseType, TargetType, FirstStep :: HNil, Migration[BaseType, TargetType] :: HNil] =
			new MigrationsBetween[BaseType, TargetType, FirstStep :: HNil] {
				type Out = Migration[BaseType, TargetType] :: HNil
				def apply(steps : FirstStep :: HNil): Out = {
					val x: Migration[BaseType, TargetType] :: HNil = Migration.instance { (base: BaseType) => targetGen.value.from(onlyStepApplier(lgen.value.to(base), steps.head)) } :: HNil
					x
				}
			}
	}


	object combineMigrations extends Poly2 {
		implicit def combine[A, B, C]: Case.Aux[Migration[A, B], Migration[B, C], Migration[A, C]] =
			at{ (migration: Migration[A, B], m2: Migration[B, C]) => Migration.instance( a => m2.migrate(migration.migrate(a)) ) }
	}

	/**
	  * If given a list of migrations, creates one single new migration that will go from the base type of the first migration to the target type of the last migration
	  * @param migrations The list of migrations
	  * @return The new migration
	  */
	def combinedMigration[Start, Second, MigrationList <: HList](migrations: Migration[Start, Second] :: MigrationList)(
		implicit folder: LeftFolder[MigrationList, Migration[Start, Second], combineMigrations.type]
	) = migrations.tail.foldLeft(migrations.head)(combineMigrations)


	object migrationByAnyStep extends Poly2 {
		implicit def byPrependStep[LastType, B <: HList, Name, Type]: Case.Aux[PrependStep[Name, Type], Migration[LastType, B], Migration[B, FieldType[Name, Type] :: B]] =
			at{ (prep: PrependStep[Name, Type], _: Migration[LastType, B]) => Migration.instance{ (obj:B) => prep.kt :: obj } }

		implicit def byAppendStep[LastType, B <: HList, Name, Type, AppendResult <: HList]
		(implicit prepend : Prepend.Aux[B, FieldType[Name, Type] :: HNil, AppendResult]): Case.Aux[AppendStep[Name, Type], Migration[LastType, B], Migration[B, AppendResult]] =
			at{ (app: AppendStep[Name, Type], _: Migration[LastType, B]) => Migration.instance{ (obj:B) => prepend(obj, app.kt :: HNil) } }

		implicit def byRemoveStep[LastType, B <: HList, Name, _RemovedElement, RemoveResult <:HList]
		(implicit remover1: Remover.Aux[B, Name, (_RemovedElement, RemoveResult)]): Case.Aux[RemoveStep[Name], Migration[LastType, B], Migration[B, RemoveResult]] =
			at{ (_: RemoveStep[Name], _: Migration[LastType, B]) => Migration.instance{ (obj:B) => remover1(obj)._2:RemoveResult } }

		implicit def byInsertAtStep[LastType, B <: HList, InsertPosition <: Nat, Name, Type, InsertResult <: HList]
		(implicit patch: Patcher.Aux[InsertPosition, _0, B, FieldType[Name, Type] :: HNil, InsertResult]):
		Case.Aux[InsertAtStep[InsertPosition, Name, Type], Migration[LastType, B], Migration[B, InsertResult]] =
			at{ (rs: InsertAtStep[InsertPosition, Name, Type], _: Migration[LastType, B]) => Migration.instance{ (obj:B) => patch(obj, rs.kt :: HNil) } }

		implicit def byRenameStep[LastType, B <: HList, OldName, NewName, RenameResult <: HList]
		(implicit rename: Renamer.Aux[B, OldName, NewName, RenameResult]):
		Case.Aux[RenameStep[OldName, NewName], Migration[LastType, B], Migration[B, RenameResult]] =
			at{ (_: RenameStep[OldName, NewName], _: Migration[LastType, B]) => Migration.instance{ (obj:B) => rename(obj):RenameResult } }

		implicit def byReplaceStep[LastType, B <: HList, OldName, NewName, OldType, NewType, _ReplacedElement, ReplaceResult]
		(implicit replaceModifier: shapeless.ops.hlist.Modifier.Aux[B, FieldType[OldName, OldType], FieldType[NewName, NewType], (_ReplacedElement, ReplaceResult)]):
		Case.Aux[ReplaceStep[OldName, NewName, OldType, NewType], Migration[LastType, B], Migration[B, ReplaceResult]] =
			at{ (replaceStep: ReplaceStep[OldName, NewName, OldType, NewType], _: Migration[LastType, B]) => Migration.instance{ (obj:B) =>
				val replaceFunction: FieldType[OldName, OldType] => FieldType[NewName, NewType] = { ft:FieldType[OldName, OldType] =>
					val oldValue: OldType = getFieldValue(ft)
					val newValue: NewType = replaceStep.mapValue(oldValue)
					field[NewName](newValue): FieldType[NewName, NewType]
				}
				replaceModifier(obj, replaceFunction)._2
			}
		}

		implicit def byChangeTypeStep[LastType, B <: HList, Name, OldType, NewType, _ReplacedElement, ReplaceResult]
		(implicit replaceModifier: shapeless.ops.hlist.Modifier.Aux[B, FieldType[Name, OldType], FieldType[Name, NewType], (_ReplacedElement, ReplaceResult)]):
		Case.Aux[ChangeTypeStep[Name, OldType, NewType], Migration[LastType, B], Migration[B, ReplaceResult]] =
			at{ (changeTypeStep: ChangeTypeStep[Name, OldType, NewType], _: Migration[LastType, B]) => Migration.instance{ (obj:B) =>
				val replaceFunction: FieldType[Name, OldType] => FieldType[Name, NewType] = { ft:FieldType[Name, OldType] =>
					val oldValue: OldType = getFieldValue(ft)
					val newValue: NewType = changeTypeStep.mapValue(oldValue)
					field[Name](newValue): FieldType[Name, NewType]
				}
				replaceModifier(obj, replaceFunction)._2
			}
		}

		implicit def byFullTransformStep[LastType, B <: HList, To <: HList]:
		Case.Aux[FullTransformStep[B, To], Migration[LastType, B], Migration[B, To]] =
			at{ (fullTransformStep: FullTransformStep[B, To], _: Migration[LastType, B]) => Migration.instance{ (obj:B) =>
				fullTransformStep.transform(obj):To
			}
		}

		implicit def bySequenceStep[LastType, B <: HList, Steps <: HList, FirstStep <: TransformStep, FoldResult]
		(implicit folder: LazyLeftFolder.Aux[FirstStep :: Steps, B, transformationByAnyStep.type, FoldResult]):
		Case.Aux[Migration[LastType, B], SequenceStep[FirstStep, Steps], Migration[B, FoldResult]] =
			at { (_: Migration[LastType, B], sequenceStep: SequenceStep[FirstStep, Steps]) => Migration.instance{ (obj:B) =>
				folder(sequenceStep.steps, obj)
			}
		}
	}
}
