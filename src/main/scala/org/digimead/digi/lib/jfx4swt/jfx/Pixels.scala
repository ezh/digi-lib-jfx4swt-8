/**
 * JFX4SWT-8 - Java 8 JavaFX library adapter for SWT framework.
 *
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.digimead.digi.lib.jfx4swt.jfx

import com.sun.glass.ui.{ Cursor, Pixels ⇒ JFXPixels, Screen, View ⇒ JFXView, Window ⇒ JFXWindow }
import java.nio.IntBuffer
import java.nio.ByteBuffer

class Pixels(width: Int, height: Int, data: IntBuffer, scale: Float) extends JFXPixels(width, height, data, scale) {
  def this(width: Int, height: Int, data: ByteBuffer) = this(width, height, null, 1)
  def this(width: Int, height: Int, data: IntBuffer) = this(width, height, data, 1)

  protected def _attachByte(x$1: Long, x$2: Int, x$3: Int, x$4: java.nio.ByteBuffer, x$5: Array[Byte], x$6: Int) {}
  protected def _attachInt(x$1: Long, x$2: Int, x$3: Int, x$4: java.nio.IntBuffer, x$5: Array[Int], x$6: Int) {}
  protected def _fillDirectByteBuffer(bb: java.nio.ByteBuffer) {}
}
