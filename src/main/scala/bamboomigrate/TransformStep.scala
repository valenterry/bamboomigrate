package bamboomigrate

import bamboomigrate.Transform.StepConstraint.OnlySteps
import shapeless.labelled.FieldType
import shapeless.{::, HList, Nat, Witness}

sealed trait TransformStep
final case class PrependStep[Name, Type](kt: FieldType[Name, Type]) extends TransformStep
final case class AppendStep[Name, Type](kt: FieldType[Name, Type]) extends TransformStep

/**
  * Type parameter must be provided. Use apply method of compagnion object.
  * Example: RemoveStep(name = 'nameOfFieldToDelete)
  */
final case class RemoveStep[Name]() extends TransformStep
object RemoveStep {
	/**
	  * Call this to create a new RemoveStep. For instance: RemoveStep(name = 'nameOfFieldToDelete)
	  */
	def apply[Name](name: Witness.Lt[Name]): RemoveStep[Name] = RemoveStep[Name]()
}
final case class InsertAtStep[InsertPosition <: Nat, Name, Type](position: InsertPosition, kt: FieldType[Name, Type]) extends TransformStep
/**
  * All type parameters must be provided. Use apply method of compagnion object.
  * Example: ReplaceStep(oldName = 'oldFieldName, newName = oldName = 'newFieldName, mapValue: (oldValue: String) => oldValue + "appendThis")
  */
final case class ReplaceStep[OldName, NewName, OldType, NewType](mapValue: OldType => NewType) extends TransformStep
object ReplaceStep {
	/**
	  * Call this to create a new ReplaceStep.
	  * For instance: ReplaceStep(oldName = 'oldFieldName, newName = oldName = 'newFieldName, mapValue: (oldValue: String) => oldValue + "appendThis")
	  */
	def apply[OldName, NewName, OldType, NewType](oldName: Witness.Lt[OldName], newName: Witness.Lt[NewName], mapValue: OldType => NewType): ReplaceStep[OldName, NewName, OldType, NewType] =
		ReplaceStep[OldName, NewName, OldType, NewType](mapValue)
}
/**
  * All type parameters must be provided. Use apply method of compagnion object.
  * Example: ChangeTypeStep(fieldName = 'someField, mapValue: (_: String) => 42)
  */
final case class ChangeTypeStep[Name, OldType, NewType](mapValue: OldType => NewType) extends TransformStep
object ChangeTypeStep {
	/**
	  * Call this to create a new ChangeTypeStep.
	  * For instance: ChangeTypeStep(fieldName = 'someField, mapValue: (_: String) => 42)
	  */
	def apply[Name, OldType, NewType](fieldName: Witness.Lt[Name], mapValue: OldType => NewType): ChangeTypeStep[Name, OldType, NewType] =
		ChangeTypeStep[Name, OldType, NewType](mapValue)
}


final case class TransformFieldsStep[OldFields <: HList, NewFields <: HList, InsertPosition <: Nat](position: InsertPosition, transform: OldFields => NewFields) extends TransformStep
final case class FullTransformStep[From <: HList, To <: HList](transform: From => To) extends TransformStep
//Hmmm, should we really force SequenceStep to contain at least one step?
final case class SequenceStep[FirstStep <: TransformStep, Steps <: HList : OnlySteps](steps: FirstStep :: Steps) extends TransformStep
//Does not work well with type inference yet - not sure why
/**
  * Allows to rename a field. Type parameters must be provided. RenameStep should only be instantiated by one of the provided apply methods in the compagnion object.
  * Otherwise typeinference will most likely not work correct.
  * Example: RenameStep(oldName = 'oldFieldName, newName = 'newFieldName)
  */
final case class RenameStep[OldName, NewName]() extends TransformStep
object RenameStep {
	//This apply method is just a workaround that uses shapeless FieldTypes, ignores away their values and uses their key.
	//The type inference works better than shapeless' narrow macro. This can later be replaced with usage of literal types
	@deprecated("Use something like RenameStep(oldName = 'oldFieldName, newName = 'newFieldName) instead")
	def apply[OldName, NewName](ktold: FieldType[OldName, _], ktnew: FieldType[NewName, _])
							   (implicit witOld: Witness.Aux[OldName], witNew: Witness.Aux[NewName]):
	RenameStep[OldName, NewName] = RenameStep[OldName, NewName]()

	//Even better type inference, but tuple syntax does not work
	/**
	  * Call this to create a new RenameStep. For instance: RenameStep(oldName = 'oldFieldName, newName = 'newFieldName)
	  */
	def apply[OldName, NewName](oldName: Witness.Lt[OldName], newName: Witness.Lt[NewName]): RenameStep[OldName, NewName] = RenameStep[OldName, NewName]()
}