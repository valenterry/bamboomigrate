package bamboomigrate

import shapeless.{HList, LabelledGeneric}
import shapeless.LabelledGeneric.Aux

/**
  * Created by valenterry on 11.06.2017.
  */
package object circe extends io.circe.generic.AutoDerivation {
	implicit def lgi[LG <: HList]: LabelledGeneric.Aux[LG, LG] = TypelevelUtils.labelledGenericIdentity
}
