package io.finch

import com.twitter.finagle.http.Message
import com.twitter.io.{Buf, Charsets}
import java.nio.CharBuffer
import java.nio.charset.{Charset, StandardCharsets}
import org.jboss.netty.buffer.ChannelBuffer

/**
 * This package contains an internal-use only type-classes and utilities that power Finch's API.
 *
 * It's not recommended to use any of the internal API directly, since it might change without any
 * deprecation cycles.
 */
package object internal {

  @inline private[this] final val someTrue: Option[Boolean] = Some(true)
  @inline private[this] final val someFalse: Option[Boolean] = Some(false)

  // adopted from Java's Long.parseLong
  // scalastyle:off return
  private[this] def parseLong(s: String, min: Long, max: Long): Option[Long] = {
    var negative = false
    var limit = -max
    var result = 0L

    var i = 0
    if (s.length > 0) {
      val firstChar = s.charAt(0)
      if (firstChar < '0') {
        if (firstChar == '-') {
          negative = true
          limit = min
        } else if (firstChar != '+') return None

        if (s.length == 1) return None

        i += 1
      }

      // skip zeros
      while (i < s.length && s.charAt(i) == '0') i += 1

      val mulMin = limit / 10L

      while (i < s.length) {
        val c = s.charAt(i)
        if ('0' <= c && c <= '9') {
          if (result < mulMin) return None
          result = result * 10L
          val digit = c - '0'
          if (result < limit + digit) return None
          result = result - digit
        } else return None

        i += 1
      }
    } else return None

    Some(if (negative) result else -result)
  }
  // scalastyle:on return

  /** Extract a byte array from a ChannelBuffer that is backed by an array.
    * Precondition: buf.hasArray == true
    *
    * @param buf The ChannelBuffer to extract the array from
    * @return An array of bytes
    */
  def extractBytesFromArrayBackedBuf(buf: ChannelBuffer): Array[Byte] = {
    val rawArray = buf.array
    val size = buf.readableBytes()
    if (rawArray.length == size) rawArray
    else {
      val dst = new Array[Byte](size)
      System.arraycopy(buf.array(), 0, dst, 0, size)
      dst
    }
  }

  /**
   * Enriches any string with fast `tooX` conversions.
   */
  implicit class TooFastString(val s: String) extends AnyVal {

    /**
     * Converts this string to the optional boolean value.
     */
    def tooBoolean: Option[Boolean] = s match {
      case "true" => someTrue
      case "false" => someFalse
      case _ => None
    }

    /**
     * Converts this string to the optional integer value. The maximum allowed length for a number
     * string is 32.
     */
    def tooInt: Option[Int] =
      if (s.length == 0 || s.length > 32) None
      else parseLong(s, Int.MinValue, Int.MaxValue).map(_.toInt)

    /**
     * Converts this string to the optional integer value. The maximum allowed length for a number
     * string is 32.
     */
    def tooLong: Option[Long] =
      if (s.length == 0 || s.length > 32) None
      else parseLong(s, Long.MinValue, Long.MaxValue)
  }

  // TODO: Move to twitter/util
  object BufText {
    def apply(s: String, cs: Charset): Buf =  {
      val enc = Charsets.encoder(cs)
      val cb = CharBuffer.wrap(s.toCharArray)
      Buf.ByteBuffer.Owned(enc.encode(cb))
    }

    def extract(buf: Buf, cs: Charset): String = {
      val dec = Charsets.decoder(cs)
      val bb = Buf.ByteBuffer.Owned.extract(buf).asReadOnlyBuffer
      dec.decode(bb).toString
    }
  }

  implicit class HttpMessage(val self: Message) extends AnyVal {
    // Returns message's charset or UTF-8 if it's not defined.
    def charsetOrUtf8: Charset = self.charset match {
      case Some(cs) => Charset.forName(cs)
      case None => StandardCharsets.UTF_8
    }
  }
}
