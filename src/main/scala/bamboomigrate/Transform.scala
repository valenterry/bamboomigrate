package bamboomigrate

import bamboomigrate.TypelevelUtils.{LazyLeftFolder, getFieldValue}
import shapeless.PolyDefns._
import shapeless._
import shapeless.labelled._
import shapeless.ops.hlist._
import shapeless.ops.record._

import scala.annotation.implicitNotFound

object Transform {
	/**
	  * Uses a TransformationStep to transform an HList L.
	  */
	@implicitNotFound("Unable to apply transformation ${Step} on ${L}")
	trait ApplyTransformationStep[L <: HList, Step, P <: Poly] extends DepFn2[L, Step] with Serializable

	object ApplyTransformationStep {
		def apply[L <: HList, Step, P <: Poly](implicit appPreStep: ApplyTransformationStep[L, Step, P]): Aux[L, Step, P, appPreStep.Out] = appPreStep

		@implicitNotFound("Unable to apply transformation ${Step} on ${L}")
		type Aux[L <: HList, Step, P <: Poly, Out0] = ApplyTransformationStep[L, Step, P] { type Out = Out0 }

		implicit def always[H <: HList, Step, P <: Poly, OutP]
		(implicit applyStep: Case2.Aux[P, H, Step, OutP]): Aux[H, Step, P, OutP]=
			new ApplyTransformationStep[H, Step, P] {
				type Out = OutP
				def apply(lg: H, step: Step): Out = applyStep(lg, step)
			}
	}

	object transformationByAnyStep extends Poly2 {
		implicit def byPrependStep[B <: HList, Name, Type]: Case.Aux[B, PrependStep[Name, Type], FieldType[Name, Type] :: B] =
			at{ (base: B, prep: PrependStep[Name, Type]) => prep.kt :: base }

		implicit def byAppendStep[B <: HList, Name, Type, AppendResult <: HList]
		(implicit prepend : Prepend.Aux[B, FieldType[Name, Type] :: HNil, AppendResult]): Case.Aux[B, AppendStep[Name, Type], AppendResult] =
			at{ (base: B, app: AppendStep[Name, Type]) => prepend(base, app.kt :: HNil) }

		implicit def byRemoveStep[B <: HList, Name, _RemovedElem, RemoveResult <:HList]
		(implicit remover1: Remover.Aux[B, Name, (_RemovedElem, RemoveResult)]): Case.Aux[B, RemoveStep[Name], RemoveResult] =
			at{ (base: B, _: RemoveStep[Name]) => remover1(base)._2 }

		implicit def byInsertAtStep[B <: HList, InsertPosition <: Nat, Name, Type, InsertResult <: HList]
		(implicit patch: Patcher.Aux[InsertPosition, _0, B, FieldType[Name, Type] :: HNil, InsertResult]):
		Case.Aux[B, InsertAtStep[InsertPosition, Name, Type], InsertResult] =
			at{ (base: B, rs: InsertAtStep[InsertPosition, Name, Type]) => patch(base, rs.kt :: HNil) }

		implicit def byRenameStep[B <: HList, OldName, NewName, RenameResult <: HList]
		(implicit rename: Renamer.Aux[B, OldName, NewName, RenameResult]):
		Case.Aux[B, RenameStep[OldName, NewName], RenameResult] =
			at{ (base: B, _: RenameStep[OldName, NewName]) => rename(base):RenameResult }

		implicit def byReplaceStep[B <: HList, OldName, NewName, OldType, NewType, TossAway, ReplaceResult]
		(implicit replaceModifier: shapeless.ops.hlist.Modifier.Aux[B, FieldType[OldName, OldType], FieldType[NewName, NewType], (TossAway, ReplaceResult)]):
		Case.Aux[B, ReplaceStep[OldName, NewName, OldType, NewType], ReplaceResult] =
			at{ (base: B, replaceStep: ReplaceStep[OldName, NewName, OldType, NewType]) => {
				val replaceFunction: FieldType[OldName, OldType] => FieldType[NewName, NewType] = { ft:FieldType[OldName, OldType] =>
					val oldValue: OldType = getFieldValue(ft)
					val newValue: NewType = replaceStep.mapValue(oldValue)
					field[NewName](newValue): FieldType[NewName, NewType]
				}
				replaceModifier(base, replaceFunction)._2
			}
		}

		implicit def byChangeTypeStep[B <: HList, Name, OldType, NewType, _ReplacedElement, ReplaceResult]
		(implicit replaceModifier: shapeless.ops.hlist.Modifier.Aux[B, FieldType[Name, OldType], FieldType[Name, NewType], (_ReplacedElement, ReplaceResult)]):
		Case.Aux[B, ChangeTypeStep[Name, OldType, NewType], ReplaceResult] =
			at{ (base: B, changeTypeStep: ChangeTypeStep[Name, OldType, NewType]) => {
				val replaceFunction: FieldType[Name, OldType] => FieldType[Name, NewType] = { ft:FieldType[Name, OldType] =>
					val oldValue: OldType = getFieldValue(ft)
					val newValue: NewType = changeTypeStep.mapValue(oldValue)
					field[Name](newValue): FieldType[Name, NewType]
				}
				replaceModifier(base, replaceFunction)._2
			}
		}

		implicit def byTransformFieldsStep[B <: HList, OldFields <: HList, NewFields <: HList, InsertPosition <: Nat, ValidatedOldFields <: HList, SelectedFields <: HList, InsertResult <: HList, FieldsRemovedList <: HList]
		(implicit 	selectOldFields: shapeless.ops.record.SelectAll.Aux[B, OldFields, OldFields],
					remove: shapeless.ops.hlist.RemoveAll.Aux[OldFields, B, FieldsRemovedList],
					insertNewFields: Patcher.Aux[InsertPosition, _0, FieldsRemovedList, NewFields, InsertResult]
		):
		Case.Aux[B, TransformFieldsStep[OldFields, NewFields, InsertPosition], InsertResult] =
			at{ (base: B, transformFieldsStep: TransformFieldsStep[OldFields, NewFields, InsertPosition]) => {
				val oldFields = selectOldFields.apply(base)
				val fieldsRemovedList = remove(oldFields)
				val newFields = transformFieldsStep.transform(oldFields)
				insertNewFields(fieldsRemovedList, newFields)
			}
		}

		implicit def byFullTransformStep[B <: HList, To <: HList]:
		Case.Aux[B, FullTransformStep[B, To], To] =
			at{ (base: B, fullTransformStep: FullTransformStep[B, To]) => fullTransformStep.transform(base):To }

		implicit def bySequenceStep[B <: HList, Steps <: HList, FirstStep <: TransformStep, Second, FoldResult]
		(implicit folder: LazyLeftFolder.Aux[FirstStep :: Steps, B, transformationByAnyStep.type, FoldResult]):
		Case.Aux[B, SequenceStep[FirstStep, Steps], FoldResult] =
			at{ (base: B, sequenceStep: SequenceStep[FirstStep, Steps]) => folder(sequenceStep.steps, base) }
	}

	/**
	  * Enforce that an HList contains only TransformSteps to make it fail fast if something else is provided
	  */
	@implicitNotFound("${L} must contain only elements of type `TransformStep`")
	trait StepConstraint[L <: HList] extends Serializable
	object StepConstraint {
		def apply[L <: HList](implicit lc: StepConstraint[L]): StepConstraint[L] = lc

		@implicitNotFound("${L} must contain only elements of type `TransformStep`")
		type OnlySteps[L <: HList] = StepConstraint[L]

		implicit val hnilStepConstraint = new StepConstraint[HNil] {}
		implicit def hlistStepConstraint[H, T <: HList](implicit bct : StepConstraint[T], ev: H <:< TransformStep) =
			new StepConstraint[H :: T] {}
	}
}


