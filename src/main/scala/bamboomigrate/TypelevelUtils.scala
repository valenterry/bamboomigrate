package bamboomigrate

import shapeless.PolyDefns._
import shapeless._
import shapeless.labelled.FieldType

object TypelevelUtils {

	/**
	  * Same as shapeless LeftScanner but uses Lazy because LeftScanner somehow seems to produce diverging implicits even though it shouldn't.
	  * Using Lazy here definitely seems to help, but maybe this is not necessary.
	  */
	trait LazyLeftScanner[L <: HList, In, P <: Poly] extends DepFn2[L, In] with Serializable {
		type Out <: HList
	}

	object LazyLeftScanner{
		def apply[L <: HList, In, P <: Poly](implicit scan: LazyLeftScanner[L, In, P]): Aux[L, In, P, scan.Out] = scan

		type Aux[L <: HList, In, P <: Poly, Out0 <: HList] = LazyLeftScanner[L, In, P]{ type Out = Out0 }

		implicit def hnilLeftScanner[In, P <: Poly]: Aux[HNil, In, P, In :: HNil] =
			new LazyLeftScanner[HNil, In, P]{
				type Out = In :: HNil

				def apply(l: HNil, in: In) = in :: HNil
			}

		implicit def hlistLeftScanner[H, T <: HList, In, P <: Poly, OutP]
		(implicit ev: Case2.Aux[P, H, In, OutP], scan: Lazy[LazyLeftScanner[T, OutP, P]]): Aux[H :: T, In, P, In :: scan.value.Out] =
			new LazyLeftScanner[H :: T, In, P]{
				type Out = In :: scan.value.Out

				def apply(l: H :: T, in: In) = in :: scan.value(l.tail, ev(l.head, in)) // check it's (h, in) vs (in, h)
			}
	}

	/**
	  * Same as LazyLeftScanner but for Folding
	  */
	trait LazyLeftFolder[L <: HList, In, HF] extends DepFn2[L, In] with Serializable

	object LazyLeftFolder {
		def apply[L <: HList, In, F](implicit folder: LazyLeftFolder[L, In, F]): Aux[L, In, F, folder.Out] = folder

		type Aux[L <: HList, In, HF, Out0] = LazyLeftFolder[L, In, HF] { type Out = Out0 }

		implicit def hnilLeftFolder[In, HF]: Aux[HNil, In , HF, In] =
			new LazyLeftFolder[HNil, In, HF] {
				type Out = In
				def apply(l : HNil, in : In): Out = in
			}

		implicit def hlistLeftFolder[H, T <: HList, In, HF, OutH]
		(implicit f : Case2.Aux[HF, In, H, OutH], ft : Lazy[LazyLeftFolder[T, OutH, HF]]): Aux[H :: T, In, HF, ft.value.Out] =
			new LazyLeftFolder[H :: T, In, HF] {
				type Out = ft.value.Out
				def apply(l : H :: T, in : In) : Out = ft.value(l.tail, f(in, l.head))
			}
	}

	/**
	  * Same as LazyLeftScanner but uses Strict
	  */
	trait StrictLeftScanner[L <: HList, In, P <: Poly] extends DepFn2[L, In] with Serializable {
		type Out <: HList
	}

	object StrictLeftScanner{
		def apply[L <: HList, In, P <: Poly](implicit scan: StrictLeftScanner[L, In, P]): Aux[L, In, P, scan.Out] = scan

		type Aux[L <: HList, In, P <: Poly, Out0 <: HList] = StrictLeftScanner[L, In, P]{ type Out = Out0 }

		implicit def hnilLeftScanner[In, P <: Poly]: Aux[HNil, In, P, In :: HNil] =
			new StrictLeftScanner[HNil, In, P]{
				type Out = In :: HNil

				def apply(l: HNil, in: In) = in :: HNil
			}

		implicit def hlistLeftScanner[H, T <: HList, In, P <: Poly, OutP]
		(implicit ev: Case2.Aux[P, H, In, OutP], scan: Strict[StrictLeftScanner[T, OutP, P]]): Aux[H :: T, In, P, In :: scan.value.Out] =
			new StrictLeftScanner[H :: T, In, P]{
				type Out = In :: scan.value.Out

				def apply(l: H :: T, in: In) = in :: scan.value(l.tail, ev(l.head, in)) // check it's (h, in) vs (in, h)
			}
	}

	/**
	  * Makes us able to not only view case classes as LabelledGenerics but also view LabelledGenerics themselves as they are, just returning them as they are
	  * This is usefull in in places where one needs a LabelledGeneric but does not want to provide a case class but a LabelledGeneric itself
	  */
	implicit def labelledGenericIdentity[LG <: HList]: LabelledGeneric.Aux[LG, LG] = new LabelledGeneric[LG] {
		type Repr = LG
		def to(lg: LG) : Repr = lg
		def from(lg: Repr) : LG = lg
	}

	def getFieldValue[K, V](value: FieldType[K, V]): V = value
}
