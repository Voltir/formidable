package formidable.implicits

import formidable.FormidableRx
import formidable.Implicits.Radio
import scala.util.Try


trait RadioRx {
  //Binders for T <=> Radio elements
  class RadioRx[T](name: String)(val head: Radio[T], val radios: Radio[T] *) extends FormidableRx[T] {
    private val selected: rx.Var[T] = rx.Var(head.value)
    private val _all = head :: radios.toList
    _all.foreach(_.input.name = name)

    override val current: rx.Rx[Try[T]] = rx.Rx { Try(selected()) }

    override def set(value: T, propagate: Boolean): Unit = _all.find(_.value == value).foreach { r =>
      r.input.checked = true
      selected.updateSilent(r.value)
      if(propagate) selected.propagate()
    }

    override def reset(propagate: Boolean): Unit = {
      set(head.value, propagate)
    }
  }

  object RadioRx {
    def apply[T](name: String)(head: Radio[T], radios: Radio[T] *) = new RadioRx[T](name)(head,radios.toList:_*)
  }
}
