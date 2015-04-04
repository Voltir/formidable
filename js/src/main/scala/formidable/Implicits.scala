package formidable

import scala.util.{Try,Success}

object Implicits {
  import org.scalajs.dom._
  import scalatags.JsDom.all.{html => _, _}

  class FormidableBinder[F <: Formidable[Target],Target] extends Binder[F,Target] {
    override def bind(inp: F, value: Target) = inp.unbuild(value)
    override def unbind(inp: F): Try[Target] = inp.build()
  }

  implicit def implicitFormidableBinder[F <: Formidable[Target],Target]: Binder[F,Target] = new FormidableBinder[F,Target]

  //Binder for Ignored fields
  class Ignored[T](val default: T) extends Formidable[T] {
    override def unbuild(value: T): Unit = Unit
    override def build(): Try[T] = Success(default)
  }
  object Ignored {
    def apply[T](default: T) = new Ignored(default)
  }

  //Binder for HTMLInputElement
  implicit object InputBinder extends Binder[html.Input,String] {
    def bind(inp: html.Input, value: String): Unit = { inp.value = value }
    def unbind(inp: html.Input): Try[String] = { Success(inp.value) }
  }

  class InputNumericBinder[N: Numeric](unbindf: String => N) extends Binder[html.Input,N] {
    def bind(inp: html.Input, value: N): Unit = { inp.value = value.toString }
    def unbind(inp: html.Input): Try[N] = Try { unbindf(inp.value) }
  }

  implicit val InputIntBinder = new InputNumericBinder[Int](_.toInt)
  implicit val InputLongBinder = new InputNumericBinder[Long](_.toLong)
  implicit val InputFloatBinder = new InputNumericBinder[Float](_.toFloat)
  implicit val InputDoubleBinder = new InputNumericBinder[Double](_.toDouble)

  //Binders for T <=> Select element
  class Opt[+T](val value: T)(mods: Modifier *) {
    val option = scalatags.JsDom.all.option(mods)
  }

  object Opt {
    def apply[T](value: T)(mods: Modifier *) = new Opt(value)(mods)
  }

  class SelectionOf[T](selectMods: Modifier *)(options: Opt[T] *) extends Formidable[T] {
    val select = scalatags.JsDom.all.select(selectMods)(options.map(_.option):_*).render

    override def unbuild(value: T): Unit = select.selectedIndex = options.indexWhere(_.value == value)

    override def build(): Try[T] = Try { options(select.selectedIndex).value }
  }

  object SelectionOf {
    def apply[T](selectMods: Modifier*)(options: Opt[T] *) = new SelectionOf[T](selectMods)(options:_*)
  }

  //Binders for T <=> Radio elements
  class Radio[+T](val value: T)(mods: Modifier *) {
    val input = scalatags.JsDom.all.input(`type`:="radio",mods).render
  }

  object Radio {
    def apply[T](value: T)(mods: Modifier *) = new Radio(value)(mods)
  }

  class RadioOf[T](name: String)(val radios: Radio[T] *) extends Formidable[T] {
    radios.foreach(_.input.name = name)

    def unbuild(value: T): Unit = radios.find(_.value == value).foreach { _.input.checked = true }

    def build(): Try[T] = Try { radios.find(_.input.checked).get.value }
  }

  object RadioOf {
    def apply[T](name: String)(radios: Radio[T] *) = new RadioOf[T](name)(radios:_*)
  }

  //Binders for Boolean <=> checkbox
  class CheckboxBool(mods: Modifier *) {
    val input = scalatags.JsDom.all.input(`type`:="checkbox", mods).render
  }
  object CheckboxBool {
    def apply(mods: Modifier *) = new CheckboxBool(mods:_*)
  }

  class CheckboxBoolBinder extends Binder[CheckboxBool,Boolean] {
    override def bind(inp: CheckboxBool, value: Boolean): Unit = inp.input.checked = value
    override def unbind(inp: CheckboxBool): Try[Boolean] = Try { inp.input.checked }
  }

  implicit def implicitCheckboxBoolBinder: Binder[CheckboxBool,Boolean] = new CheckboxBoolBinder

  //Binders for Set[T] <=> Checkbox elements
  class Chk[+T](val value: T)(mods: Modifier *) {
    val input = scalatags.JsDom.all.input(`type`:="checkbox",mods).render
  }

  object Chk {
    def apply[T](value: T)(mods: Modifier *) = new Chk(value)(mods)
  }

  //  class CheckboxSet[T](name: String)(checks: Chk[T] *) extends Formidable[Set[T]] {
  //    val checkboxes: Array[Chk[T]] = {
  //      checks.map { c => c.input.name = name; c }.toArray
  //    }
  //
  //    override def unbuild(values: Set[T]) = {
  //      val (checked,unchecked) = checks.partition(c => values.contains(c.value))
  //      checked.foreach   { _.input.checked = true  }
  //      unchecked.foreach { _.input.checked = false }
  //    }
  //
  //    override def build(): Try[Set[T]] = Try {
  //      checks.filter(_.input.checked).map(_.value).toSet
  //    }
  //  }
  //
  //  object CheckboxSet {
  //    def apply[T](name: String)(checks: Chk[T] *) = new CheckboxSet[T](name)(checks:_*)
  //  }

  class CheckboxBase[T, Container[_]](name: String)(buildFrom: Seq[T] => Container[T], hasValue: Container[T] => T => Boolean)(checks: Chk[T] *) extends Formidable[Container[T]] {
    val checkboxes: Array[Chk[T]] = checks.map { c => c.input.name = name; c }.toArray

    override def unbuild(values: Container[T]) = {
      val (checked,unchecked) = checks.partition(c => hasValue(values)(c.value))
      checked.foreach   { _.input.checked = true  }
      unchecked.foreach { _.input.checked = false }
    }

    override def build(): Try[Container[T]] = Try {
      buildFrom(checks.filter(_.input.checked).map(_.value))
    }
  }

  object CheckboxOf {
    def set[T](name: String)(checks: Chk[T] *)    = new CheckboxBase[T,Set](name)(_.toSet, c => v => c.contains(v))(checks:_*)
    def list[T](name: String)(checks: Chk[T] *)   = new CheckboxBase[T,List](name)(_.toList, c => v => c.contains(v))(checks:_*)
    //def vector[T](name: String)(checks: Chk[T] *) = new CheckboxBase[T,Vector](name)(_.toVector)(checks:_*)
  }
}