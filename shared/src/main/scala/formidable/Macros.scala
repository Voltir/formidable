package formidable

import rx._

import scala.reflect.macros._
import scala.language.experimental.macros

object Macros {

  def getCompanion(c: blackbox.Context)(tpe: c.Type) = {
    import c.universe._
    val symTab = c.universe.asInstanceOf[reflect.internal.SymbolTable]
    val pre = tpe.asInstanceOf[symTab.Type].prefix.asInstanceOf[Type]
    c.universe.internal.gen.mkAttributedRef(pre, tpe.typeSymbol.companion)
  }

  def generate[Layout: c.WeakTypeTag, Target: c.WeakTypeTag](c: blackbox.Context): c.Expr[Layout with FormidableRx[Target]] = {
    import c.universe._
    val targetTpe = weakTypeTag[Target].tpe
    val layoutTpe = weakTypeTag[Layout].tpe

    val fields = targetTpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.get.paramLists.head

    val layoutAccessors = layoutTpe.decls.map(_.asTerm).filter(_.isAccessor).toList
    val layoutNames = layoutAccessors.map(_.name.toString).toSet
    val targetNames = fields.map(_.name.toString).toSet
    val missing = targetNames.diff(layoutNames)
    if(missing.size > 0) {
      c.abort(c.enclosingPosition,s"The layout is not fully defined: Missing fields are:\n${missing.mkString("\n")}")
    }

    //Get subset of layout accessors that are rx.core.Var types
    val VAR_SYMBOL = typeOf[rx.core.Var[_]].typeSymbol
    val rxVarAccessors = layoutAccessors.filter { a =>
      a.typeSignature match {
        case NullaryMethodType(TypeRef(_,VAR_SYMBOL, _ :: Nil)) => true
        case _ => false
      }
    }

    val companion = getCompanion(c)(targetTpe)

    val magic: List[c.Tree] = fields.zipWithIndex.map { case (field,idx) =>
      val term = TermName(s"a$idx")
      val accessor = layoutAccessors.find(_.name == field.name).get
      q"implicitly[BindRx[${accessor.info.dealias},${field.info.dealias}]].bind(this.$accessor,$term)"
    }

    val unmagic: List[c.Tree] = fields.zipWithIndex.map { case (field,i) =>
      val accessor = layoutAccessors.find(_.name == field.name).get
      fq"${TermName(s"a$i")} <- implicitly[BindRx[${accessor.info.dealias},${field.info.dealias}]].unbind(this.$accessor)()"
    }

    val resetMagic: List[c.Tree] = fields.map { case field =>
      val accessor = layoutAccessors.find(_.name == field.name).get
      q"implicitly[BindRx[${accessor.info.dealias},${field.info.dealias}]].reset(this.$accessor)"
    }

    def bindN(n: Int) = {
      if(n > 0 && n < 23) {
        val vars = (0 until n).map(i => pq"${TermName(s"a$i")}")
        q"""$companion.unapply(inp).map { case (..$vars) => $magic }"""
      }
      else {
        c.abort(c.enclosingPosition,"Unsupported Case Class Dimension")
      }
    }

    //Hack to make Var types resetable
    //Basic idea: Generate vals that store the default Var value when constructed
    //And on reset, use those defaults vals to reset each Var
    val varDefaultsMagic = rxVarAccessors.map { a =>
      val default = a.name.decodedName.toString + "Default"
      q"val ${TermName(default)} = this.$a.now"
    }

    val varResetMagic = rxVarAccessors.map { a =>
      val default = a.name.decodedName.toString + "Default"
      q"this.$a.update(${TermName(default)})"
    }

    c.Expr[Layout with FormidableRx[Target]](q"""
      new $layoutTpe with FormidableRx[$targetTpe] {

        private var isUpdating: Boolean = false

        ..$varDefaultsMagic

        val current: Rx[scala.util.Try[$targetTpe]] = Rx {
          if(isUpdating) {
            scala.util.Failure(formidable.FormidableProcessingFailure)
           }
          else {
            for(..$unmagic) yield {
              $companion.apply(..${(0 until fields.size).map(i=>TermName("a"+i))})
            }
          }
        }

        private def startUpdate(): Unit = isUpdating = true

        private def stopUpdate(): Unit = {
          isUpdating = false
          current.recalc()
        }

        override def set(inp: $targetTpe): Unit = {
          startUpdate()
          ${bindN(fields.size)}
          stopUpdate()
        }

        def reset(): Unit = {
          startUpdate()
          ..$varResetMagic
          ..$resetMagic
          stopUpdate()
        }
      }
    """)
  }
}