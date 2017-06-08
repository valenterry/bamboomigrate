import Transform.StepConstraint.OnlySteps
import shapeless.{::, HList, Nat, Witness}
import shapeless.labelled.FieldType

sealed trait TransformStep
final case class PrependStep[Name, Type](kt: FieldType[Name, Type]) extends TransformStep
final case class AppendStep[Name, Type](kt: FieldType[Name, Type]) extends TransformStep
final case class RemoveStep[Name](name: Name) extends TransformStep
final case class InsertAtStep[InsertPosition <: Nat, Name, Type](position: InsertPosition, kt: FieldType[Name, Type]) extends TransformStep
final case class ReplaceStep[OldName, NewName, OldType, NewType](mapName: (OldName, NewName), mapValue: OldType => NewType) extends TransformStep
final case class ChangeTypeStep[Name, OldType, NewType](fieldName: Name, mapValue: OldType => NewType) extends TransformStep
final case class TransformFieldsStep[OldFields <: HList, NewFields <: HList, InsertPosition <: Nat](position: InsertPosition, transform: OldFields => NewFields) extends TransformStep
final case class FullTransformStep[From <: HList, To <: HList](transform: From => To) extends TransformStep
//Hmmm, should we really force SequenceStep to contain at least one step?
final case class SequenceStep[FirstStep <: TransformStep, Steps <: HList : OnlySteps](steps: FirstStep :: Steps) extends TransformStep
//Does not work well with type inference yet - not sure why
final case class RenameStep[OldName, NewName](oldToNewName: (OldName, NewName)) extends TransformStep
object RenameStep {
	//This apply method is just a workaround that uses shapeless FieldTypes, ignores away their values and uses their key.
	//The type inference works better than shapeless' narrow macro. This can later be replaced with usage of literal types
	def apply[OldName, NewName](ktold: FieldType[OldName, _], ktnew: FieldType[NewName, _])
							   (implicit witOld: Witness.Aux[OldName], witNew: Witness.Aux[NewName]):
	RenameStep[OldName, NewName] = RenameStep(witOld.value -> witNew.value)
}