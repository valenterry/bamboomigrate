package bamboomigrate

package object playjson {
	import play.api.libs.json.Reads
	import julienrf.json.derived._
	import shapeless.Lazy
	implicit def playNameIdentityReads[A](implicit derivedReads: Lazy[DerivedReads[A]]): Reads[A] = derivedReads.value.reads(TypeTagReads.nested, NameAdapter.identity)
}
