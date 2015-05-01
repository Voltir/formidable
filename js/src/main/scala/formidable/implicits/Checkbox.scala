package formidable.implicits

import formidable.FormidableRx
import formidable.Implicits.Chk
import org.scalajs.dom._
import scala.util.Try
import scalatags.JsDom.all._
import rx._

trait Checkbox {

  //Basic checkbox as a bool
  class CheckboxBoolRx(default: Boolean)(mods: Modifier *) extends FormidableRx[Boolean] {
    val input = scalatags.JsDom.all.input(`type`:="checkbox", mods).render
    val current: rx.Rx[Try[Boolean]] = rx.Rx { Try(input.checked)}

    override def set(inp: Boolean): Unit = {
      input.checked = inp
      current.recalc()
    }

    override def reset(): Unit = {
      set(default)
    }

    input.onchange = { (_:Event) => current.recalc() }
  }

  //BindRx for Set[T]/List[T] <=> Checkbox elements
  class CheckboxBaseRx[T, Container[_] <: Traversable[_]]
      (name: String)
      (buildFrom: Seq[T] => Container[T], hasValue: Container[T] => T => Boolean)
      (checks: Chk[T] *) extends FormidableRx[Container[T]] {
    val checkboxes = checks.map { c =>
      c.input.name = name
      c.input.onchange = { (_:Event) => current.recalc() }
      c }.toBuffer

    private val changeme : Var[Container[T]] = Var(buildFrom(Seq.empty))

//    override lazy val current: rx.Rx[Try[Container[T]]] = rx.Rx { Try {
//      buildFrom(checks.filter(_.input.checked).map(_.value))
//    }}

    private def meh(): Seq[T] = {
      checks.filter(_.input.checked).map(_.value)
    }

    override lazy val current: Rx[Try[Container[T]]] = Rx { Try(changeme()) }

    override def set(values: Container[T]) = {
      val (checked,unchecked) = checks.partition(c => hasValue(values)(c.value))
      checked.foreach   { _.input.checked = true  }
      unchecked.foreach { _.input.checked = false }
      changeme() = buildFrom(meh())
    }

    override def reset(): Unit = {
      checks.foreach { _.input.checked = false }
      changeme() = buildFrom(meh())
    }
  }

  object CheckboxRx {
    def set[T](name: String)(checks: Chk[T] *)    = new CheckboxBaseRx[T,Set](name)(_.toSet, c => v => c.contains(v))(checks:_*)
    def list[T](name: String)(checks: Chk[T] *)   = new CheckboxBaseRx[T,List](name)(_.toList, c => v => c.contains(v))(checks:_*)
  }
}
