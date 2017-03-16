package io.reactors
package remote



import io.reactors.common.BloomMap
import io.reactors.common.Cell
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Arrays
import java.util.Comparator
import scala.collection._
import scala.reflect.ClassTag



object RuntimeMarshaler {
  private val booleanClass = classOf[Boolean]
  private val byteClass = classOf[Byte]
  private val shortClass = classOf[Short]
  private val charClass = classOf[Char]
  private val intClass = classOf[Int]
  private val floatClass = classOf[Float]
  private val longClass = classOf[Long]
  private val doubleClass = classOf[Double]
  private val fieldCache = new BloomMap[Class[_], Array[Field]]
  private val fieldComparator = new Comparator[Field] {
    def compare(x: Field, y: Field): Int = x.toString.compareTo(y.toString)
  }
  private val isMarshalableField: Field => Boolean = f => {
    !Modifier.isTransient(f.getModifiers)
  }

  private def classNameTerminatorTag: Byte = 1

  private def objectReferenceTag: Byte = 2

  private def nullTag: Byte = 3

  private def arrayTag: Byte = 4

  private def computeFieldsOf(klazz: Class[_]): Array[Field] = {
    val fields = mutable.ArrayBuffer[Field]()
    var ancestor = klazz
    while (ancestor != null) {
      fields ++= ancestor.getDeclaredFields.filter(isMarshalableField)
      ancestor = ancestor.getSuperclass
    }
    fields.toArray
  }

  private def fieldsOf(klazz: Class[_]): Array[Field] = {
    var fields = fieldCache.get(klazz)
    if (fields == null) {
      fields = computeFieldsOf(klazz)
      Arrays.sort(fields, fieldComparator)
      fieldCache.put(klazz, fields)
    }
    fields
  }

  def marshalAs[T](
    klazz: Class[_], obj: T, inputData: Data, alreadyRecordedReference: Boolean
  ): Data = {
    if (obj == null) {
      var data = inputData
      if (data.remainingWriteSize < 1) data = data.flush(1)
      data(data.endPos) = nullTag
      data.endPos += 1
      data
    } else {
      val context = Reactor.marshalContext
      if (!alreadyRecordedReference)
        context.written.put(obj.asInstanceOf[AnyRef], context.createFreshReference())
      val data = internalMarshalAs(klazz, obj, inputData, context)
      context.resetMarshal()
      data
    }
  }

  private def internalMarshalAs[T](
    klazz: Class[_], obj: T, inputData: Data, context: Reactor.MarshalContext
  ): Data = {
    assert(obj != null)
    var data = inputData
    val fields = fieldsOf(klazz)
    var i = 0
    while (i < fields.length) {
      val field = fields(i)
      field.setAccessible(true)
      val tpe = field.getType
      def storeInt(v: Int): Unit = {
        if (data.remainingWriteSize < 4) data = data.flush(4)
        val pos = data.endPos
        data(pos + 0) = ((v & 0x000000ff) >>> 0).toByte
        data(pos + 1) = ((v & 0x0000ff00) >>> 8).toByte
        data(pos + 2) = ((v & 0x00ff0000) >>> 16).toByte
        data(pos + 3) = ((v & 0xff000000) >>> 24).toByte
        data.endPos += 4
      }
      def storeLong(v: Long): Unit = {
        if (data.remainingWriteSize < 8) data = data.flush(8)
        val pos = data.endPos
        data(pos + 0) = ((v & 0x00000000000000ffL) >>> 0).toByte
        data(pos + 1) = ((v & 0x000000000000ff00L) >>> 8).toByte
        data(pos + 2) = ((v & 0x0000000000ff0000L) >>> 16).toByte
        data(pos + 3) = ((v & 0x00000000ff000000L) >>> 24).toByte
        data(pos + 4) = ((v & 0x000000ff00000000L) >>> 32).toByte
        data(pos + 5) = ((v & 0x0000ff0000000000L) >>> 40).toByte
        data(pos + 6) = ((v & 0x00ff000000000000L) >>> 48).toByte
        data(pos + 7) = ((v & 0xff00000000000000L) >>> 56).toByte
        data.endPos += 8
      }
      def storeDouble(v: Double): Unit = {
        val bits = java.lang.Double.doubleToRawLongBits(v)
        if (data.remainingWriteSize < 8) data = data.flush(8)
        val pos = data.endPos
        data(pos + 0) = ((bits & 0x00000000000000ffL) >>> 0).toByte
        data(pos + 1) = ((bits & 0x000000000000ff00L) >>> 8).toByte
        data(pos + 2) = ((bits & 0x0000000000ff0000L) >>> 16).toByte
        data(pos + 3) = ((bits & 0x00000000ff000000L) >>> 24).toByte
        data(pos + 4) = ((bits & 0x000000ff00000000L) >>> 32).toByte
        data(pos + 5) = ((bits & 0x0000ff0000000000L) >>> 40).toByte
        data(pos + 6) = ((bits & 0x00ff000000000000L) >>> 48).toByte
        data(pos + 7) = ((bits & 0xff00000000000000L) >>> 56).toByte
        data.endPos += 8
      }
      def storeFloat(v: Float): Unit = {
        val bits = java.lang.Float.floatToRawIntBits(v)
        if (data.remainingWriteSize < 4) data = data.flush(4)
        val pos = data.endPos
        data(pos + 0) = ((bits & 0x000000ff) >>> 0).toByte
        data(pos + 1) = ((bits & 0x0000ff00) >>> 8).toByte
        data(pos + 2) = ((bits & 0x00ff0000) >>> 16).toByte
        data(pos + 3) = ((bits & 0xff000000) >>> 24).toByte
        data.endPos += 4
      }
      def storeByte(v: Byte): Unit = {
        if (data.remainingWriteSize < 1) data = data.flush(1)
        val pos = data.endPos
        data(pos + 0) = v
        data.endPos += 1
      }
      def storeBoolean(v: Boolean): Unit = {
        if (data.remainingWriteSize < 1) data = data.flush(1)
        val pos = data.endPos
        data(pos) = if (v) 1 else 0
        data.endPos += 1
      }
      def storeChar(v: Char): Unit = {
        if (data.remainingWriteSize < 2) data = data.flush(2)
        val pos = data.endPos
        data(pos + 0) = ((v & 0x000000ff) >>> 0).toByte
        data(pos + 1) = ((v & 0x0000ff00) >>> 8).toByte
        data.endPos += 2
      }
      def storeShort(v: Short): Unit = {
        if (data.remainingWriteSize < 2) data = data.flush(2)
        val pos = data.endPos
        data(pos + 0) = ((v & 0x000000ff) >>> 0).toByte
        data(pos + 1) = ((v & 0x0000ff00) >>> 8).toByte
        data.endPos += 2
      }
      if (tpe.isPrimitive) {
        tpe match {
          case RuntimeMarshaler.this.intClass =>
            val v = field.getInt(obj)
            storeInt(v)
          case RuntimeMarshaler.this.longClass =>
            val v = field.getLong(obj)
            storeLong(v)
          case RuntimeMarshaler.this.doubleClass =>
            val v = field.getDouble(obj)
            storeDouble(v)
          case RuntimeMarshaler.this.floatClass =>
            val v = field.getFloat(obj)
            storeFloat(v)
          case RuntimeMarshaler.this.byteClass =>
            val v = field.getByte(obj)
            storeByte(v)
          case RuntimeMarshaler.this.booleanClass =>
            val v = field.getBoolean(obj)
            storeBoolean(v)
          case RuntimeMarshaler.this.charClass =>
            val v = field.getChar(obj)
            storeChar(v)
          case RuntimeMarshaler.this.shortClass =>
            val v = field.getShort(obj)
            storeShort(v)
        }
      } else if (tpe.isArray) {
        sys.error("Array marshaling is currently not supported.")
        val pos = data.endPos
        val array = field.get(obj)
        if (array == null) {
          if (data.remainingWriteSize < 1) data = data.flush(1)
          data(data.endPos) = nullTag
          data.endPos += 1
        } else {
          storeInt(java.lang.reflect.Array.getLength(array))
        }
      } else {
        val value = field.get(obj)
        if (value == null) {
          if (data.remainingWriteSize < 1) data = data.flush(1)
          data(data.endPos) = nullTag
          data.endPos += 1
        } else {
          val ref = context.written.get(value)
          if (ref != context.written.nil) {
            if (data.remainingWriteSize < 5) data = data.flush(5)
            val pos = data.endPos
            data(pos + 0) = objectReferenceTag
            data(pos + 1) = ((ref & 0x000000ff) >>> 0).toByte
            data(pos + 2) = ((ref & 0x0000ff00) >>> 8).toByte
            data(pos + 3) = ((ref & 0x00ff0000) >>> 16).toByte
            data(pos + 4) = ((ref & 0xff000000) >>> 24).toByte
            data.endPos += 5
          } else {
            val marshalType = !Modifier.isFinal(field.getType.getModifiers)
            val freshRef = context.createFreshReference()
            context.written.put(value, freshRef)
            data = internalMarshal(value, data, marshalType, context)
          }
        }
      }
      i += 1
    }
    data
  }

  def marshal[T](obj: T, inputData: Data, marshalType: Boolean = true): Data = {
    val context = Reactor.marshalContext
    if (obj == null) {
      var data = inputData
      if (data.remainingWriteSize < 1) data = data.flush(1)
      data(data.endPos) = nullTag
      data.endPos += 1
      data
    } else {
      context.written.put(obj.asInstanceOf[AnyRef], context.createFreshReference())
      val data = internalMarshal(obj, inputData, marshalType, context)
      context.resetMarshal()
      data
    }
  }

  def optionallyMarshalType(
    klazz: Class[_], inputData: Data, marshalType: Boolean
  ): Data = {
    var data = inputData
    if (marshalType) {
      val name = klazz.getName
      val typeLength = name.length + 1
      if (typeLength > data.remainingWriteSize) data = data.flush(typeLength)
      val pos = data.endPos
      var i = 0
      while (i < name.length) {
        data(pos + i) = name.charAt(i).toByte
        i += 1
      }
      data(pos + i) = classNameTerminatorTag
      data.endPos += typeLength
    } else {
      if (data.remainingWriteSize < 1) data = data.flush(-1)
      data(data.endPos) = classNameTerminatorTag
      data.endPos += 1
    }
    data
  }

  private def internalMarshal[T](
    obj: T, inputData: Data, marshalType: Boolean, context: Reactor.MarshalContext
  ): Data = {
    var data = inputData
    val klazz = obj.getClass
    data = optionallyMarshalType(klazz, data, marshalType)
    internalMarshalAs(klazz, obj, data, context)
  }

  def unmarshal[T: ClassTag](
    inputData: Cell[Data], unmarshalType: Boolean = true
  ): T = {
    val context = Reactor.marshalContext
    val klazz = implicitly[ClassTag[T]].runtimeClass
    val obj = internalUnmarshal[T](klazz, inputData, unmarshalType, context)
    context.resetUnmarshal()
    obj
  }

  private def internalUnmarshal[T](
    assumedKlazz: Class[_], inputData: Cell[Data], unmarshalType: Boolean,
    context: Reactor.MarshalContext
  ): T = {
    var data = inputData()
    var klazz = assumedKlazz
    if (data.remainingReadSize < 1) data = data.fetch()
    val initialByte = data(data.startPos)
    if (initialByte == objectReferenceTag) {
      data.startPos += 1
      var i = 0
      var ref = 0
      while (i < 4) {
        if (data.remainingReadSize == 0) data = data.fetch()
        val b = data(data.startPos)
        ref |= (b.toInt & 0xff) << (8 * i)
        data.startPos += 1
        i += 1
      }
      val obj = context.seen(ref)
      inputData := data
      obj.asInstanceOf[T]
    } else if (initialByte == nullTag) {
      data.startPos += 1
      inputData := data
      null.asInstanceOf[T]
    } else {
      if (unmarshalType) {
        val stringBuffer = context.stringBuffer
        var last = initialByte
        if (last == classNameTerminatorTag)
          sys.error("Data does not contain a type signature.")
        var i = data.startPos
        var until = data.startPos + data.remainingReadSize
        stringBuffer.setLength(0)
        while (last != classNameTerminatorTag) {
          last = data(i)
          if (last != classNameTerminatorTag) stringBuffer.append(last.toChar)
          i += 1
          if (i == until) {
            data.startPos += i - data.startPos
            if (last != classNameTerminatorTag) {
              data = data.fetch()
              i = data.startPos
              until = data.startPos + data.remainingReadSize
            }
          }
        }
        data.startPos += i - data.startPos
        val klazzName = stringBuffer.toString
        klazz = Class.forName(klazzName)
      } else {
        assert(initialByte == classNameTerminatorTag)
        data.startPos += 1
      }
      val obj = Platform.unsafe.allocateInstance(klazz)
      context.seen += obj
      inputData := data
      internalUnmarshalAs(klazz, obj, inputData, context)
      obj.asInstanceOf[T]
    }
  }

  private def internalUnmarshalAs[T](
    klazz: Class[_], obj: T, inputData: Cell[Data], context: Reactor.MarshalContext
  ): Unit = {
    var data = inputData()
    val fields = fieldsOf(klazz)
    var i = 0
    while (i < fields.length) {
      val field = fields(i)
      field.setAccessible(true)
      val tpe = field.getType
      if (tpe.isPrimitive) {
        tpe match {
          case RuntimeMarshaler.this.intClass =>
            if (data.remainingReadSize >= 4) {
              val pos = data.startPos
              val b0 = (data(pos + 0).toInt << 0) & 0x000000ff
              val b1 = (data(pos + 1).toInt << 8) & 0x0000ff00
              val b2 = (data(pos + 2).toInt << 16) & 0x00ff0000
              val b3 = (data(pos + 3).toInt << 24) & 0xff000000
              field.setInt(obj, b3 | b2 | b1 | b0)
              data.startPos = pos + 4
            } else {
              var i = 0
              var x = 0
              while (i < 4) {
                if (data.remainingReadSize == 0) data = data.fetch()
                val b = data(data.startPos)
                x |= (b.toInt & 0xff) << (8 * i)
                data.startPos += 1
                i += 1
              }
              field.setInt(obj, x)
            }
          case RuntimeMarshaler.this.longClass =>
            if (data.remainingReadSize >= 8) {
              val pos = data.startPos
              val b0 = (data(pos + 0).toLong << 0) & 0x00000000000000ffL
              val b1 = (data(pos + 1).toLong << 8) & 0x000000000000ff00L
              val b2 = (data(pos + 2).toLong << 16) & 0x0000000000ff0000L
              val b3 = (data(pos + 3).toLong << 24) & 0x00000000ff000000L
              val b4 = (data(pos + 4).toLong << 32) & 0x000000ff00000000L
              val b5 = (data(pos + 5).toLong << 40) & 0x0000ff0000000000L
              val b6 = (data(pos + 6).toLong << 48) & 0x00ff000000000000L
              val b7 = (data(pos + 7).toLong << 56) & 0xff00000000000000L
              field.setLong(obj, b7 | b6 | b5 | b4 | b3 | b2 | b1 | b0)
              data.startPos = pos + 8
            } else {
              var i = 0
              var x = 0L
              while (i < 8) {
                if (data.remainingReadSize == 0) data = data.fetch()
                val b = data(data.startPos)
                x |= (b.toLong & 0xff) << (8 * i)
                data.startPos += 1
                i += 1
              }
              field.setLong(obj, x)
            }
          case RuntimeMarshaler.this.doubleClass =>
            if (data.remainingReadSize >= 8) {
              val pos = data.startPos
              val b0 = (data(pos + 0).toLong << 0) & 0x00000000000000ffL
              val b1 = (data(pos + 1).toLong << 8) & 0x000000000000ff00L
              val b2 = (data(pos + 2).toLong << 16) & 0x0000000000ff0000L
              val b3 = (data(pos + 3).toLong << 24) & 0x00000000ff000000L
              val b4 = (data(pos + 4).toLong << 32) & 0x000000ff00000000L
              val b5 = (data(pos + 5).toLong << 40) & 0x0000ff0000000000L
              val b6 = (data(pos + 6).toLong << 48) & 0x00ff000000000000L
              val b7 = (data(pos + 7).toLong << 56) & 0xff00000000000000L
              val bits = b7 | b6 | b5 | b4 | b3 | b2 | b1 | b0
              val x = java.lang.Double.longBitsToDouble(bits)
              field.setDouble(obj, x)
              data.startPos = pos + 8
            } else {
              var i = 0
              var bits = 0L
              while (i < 8) {
                if (data.remainingReadSize == 0) data = data.fetch()
                val b = data(data.startPos)
                bits |= (b.toLong & 0xff) << (8 * i)
                data.startPos += 1
                i += 1
              }
              val x = java.lang.Double.longBitsToDouble(bits)
              field.setDouble(obj, x)
            }
          case RuntimeMarshaler.this.floatClass =>
            if (data.remainingReadSize >= 4) {
              val pos = data.startPos
              val b0 = (data(pos + 0).toInt << 0) & 0x000000ff
              val b1 = (data(pos + 1).toInt << 8) & 0x0000ff00
              val b2 = (data(pos + 2).toInt << 16) & 0x00ff0000
              val b3 = (data(pos + 3).toInt << 24) & 0xff000000
              val bits = b3 | b2 | b1 | b0
              val x = java.lang.Float.intBitsToFloat(bits)
              field.setFloat(obj, x)
              data.startPos = pos + 4
            } else {
              var i = 0
              var bits = 0
              while (i < 4) {
                if (data.remainingReadSize == 0) data = data.fetch()
                val b = data(data.startPos)
                bits |= (b.toInt & 0xff) << (8 * i)
                data.startPos += 1
                i += 1
              }
              val x = java.lang.Float.intBitsToFloat(bits)
              field.setFloat(obj, x)
            }
          case RuntimeMarshaler.this.byteClass =>
            if (data.remainingReadSize < 1) data = data.fetch()
            val pos = data.startPos
            val b = data(pos)
            field.setByte(obj, b)
            data.startPos = pos + 1
          case RuntimeMarshaler.this.booleanClass =>
            if (data.remainingReadSize < 1) data = data.fetch()
            val pos = data.startPos
            val b = data(pos)
            field.setBoolean(obj, if (b != 0) true else false)
            data.startPos = pos + 1
          case RuntimeMarshaler.this.charClass =>
            if (data.remainingReadSize >= 2) {
              val pos = data.startPos
              val b0 = (data(pos + 0).toChar << 0) & 0x000000ff
              val b1 = (data(pos + 1).toChar << 8) & 0x0000ff00
              field.setChar(obj, (b1 | b0).toChar)
              data.startPos = pos + 2
            } else {
              var i = 0
              var x = 0
              while (i < 2) {
                if (data.remainingReadSize == 0) data = data.fetch()
                val b = data(data.startPos)
                x |= (b.toInt & 0xff) << (8 * i)
                data.startPos += 1
                i += 1
              }
              field.setChar(obj, x.toChar)
            }
          case RuntimeMarshaler.this.shortClass =>
            if (data.remainingReadSize >= 2) {
              val pos = data.startPos
              val b0 = (data(pos + 0).toShort << 0) & 0x000000ff
              val b1 = (data(pos + 1).toShort << 8) & 0x0000ff00
              field.setShort(obj, (b1 | b0).toShort)
              data.startPos = pos + 2
            } else {
              var i = 0
              var x = 0
              while (i < 2) {
                if (data.remainingReadSize == 0) data = data.fetch()
                val b = data(data.startPos)
                x |= (b.toInt & 0xff) << (8 * i)
                data.startPos += 1
                i += 1
              }
              field.setShort(obj, x.toShort)
            }
        }
      } else if (tpe.isArray) {
        sys.error("Array marshaling is currently not supported.")
      } else {
        val unmarshalType = !Modifier.isFinal(tpe.getModifiers)
        inputData := data
        val x = internalUnmarshal[AnyRef](tpe, inputData, unmarshalType, context)
        data = inputData()
        field.set(obj, x)
      }
      i += 1
    }
    inputData := data
  }
}
