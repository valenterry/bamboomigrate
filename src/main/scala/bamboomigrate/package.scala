package object bamboomigrate {
	//Make it so that importing bamboomigrate let's one write the transformationsteps with shapeless without other imports
	type HNil = shapeless.HNil
	val HNil = shapeless.HNil
	type Nat = shapeless.Nat
	val Nat = shapeless.Nat
	val Record = shapeless.record.Record
}
