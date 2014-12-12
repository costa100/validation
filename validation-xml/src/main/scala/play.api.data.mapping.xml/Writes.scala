package play.api.data.mapping.xml

import play.api.data.mapping._

import scala.xml._

trait DefaultMonoids {
  import play.api.libs.functional.Monoid

  // We define a monoid of the endofunctor xml.Elem => xml.Elem (alias XmlWriter)
  // Monoid[XmlWriter] thus has the propriety of a Monad in xml.Elem (being a monoid in the category of endofunctor)
  implicit def xmlMonoid = new Monoid[XmlWriter] {
    def append(a1: XmlWriter, a2: XmlWriter): XmlWriter = a1 andThen a2
    def identity: XmlWriter = scala.Predef.identity
  }
}

object Writes extends DefaultWritesWithPrimitiveTypes with DefaultMonoids with GenericWrites[XmlWriter] {

  implicit def nodeW[I](implicit w: WriteLike[I, String]): Write[I, XmlWriter] = Write { i =>
    node => node.copy(child = node.child :+ new Text(w.writes(i)))
  }
  
  def attributeW[I](name: String)(implicit w: WriteLike[I, String]): Write[I, XmlWriter] = Write { i =>
    node => node.copy(attributes = node.attributes.append(new UnprefixedAttribute(name, w.writes(i), Null)))
  }

  def optAttributeW[I](name: String)(implicit w: WriteLike[I, String]): Write[Option[I], XmlWriter] = Write {
    case Some(i) => attributeW(name)(w).writes(i)
    case None => xmlMonoid.identity
  }

  implicit def writeXml[I](path: Path)(implicit w: WriteLike[I, XmlWriter]): Write[I, XmlWriter] = Write { i =>
    val reversedPath = path.path.reverse
    reversedPath match {
      case Nil => w.writes(i)

      case KeyPathNode(key) :: tail =>
        val lastElem = w.writes(i).apply(new Elem(null, key, Null, TopScope, false, Seq.empty: _*))
        val newNode = tail.foldLeft(lastElem) {
          case (acc, IdxPathNode(_)) => acc
          case (acc, KeyPathNode(key)) => new Elem(null, key, Null, TopScope, false, acc)
        }
        node => node.copy(child = node.child :+ newNode)

      case IdxPathNode(_) :: _ => throw new RuntimeException("cannot write an attribute to a node with an index path")
    }
  }

  implicit def seqToNodeSeq[I](implicit w: WriteLike[I, XmlWriter]): Write[Seq[I], XmlWriter] = Write { is =>
    is.map(w.writes).foldLeft(xmlMonoid.identity)(xmlMonoid.append)
  }

  def optionW[I, J](r: => WriteLike[I, J])(implicit w: Path => WriteLike[J, XmlWriter]): Path => Write[Option[I], XmlWriter] =
    super.optionW[I, J, XmlWriter](r, xmlMonoid.identity)

  implicit def optionW[I](implicit w: Path => WriteLike[I, XmlWriter]): Path => Write[Option[I], XmlWriter] =
    optionW(Write.zero[I])

}

trait DefaultWritesWithPrimitiveTypes extends DefaultWrites {
  implicit val intW: Write[Int, String] = Write(_.toString)
  implicit val shortW: Write[Short, String] = Write(_.toString)
  implicit val booleanW: Write[Boolean, String] = Write(_.toString)
  implicit val longW: Write[Long, String] = Write(_.toString)
  implicit val floatW: Write[Float, String] = Write(_.toString)
  implicit val doubleW: Write[Double, String] = Write(_.toString)
  implicit val bigDecimalW: Write[BigDecimal, String] = Write(_.toString)
}

