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

package org.digimead.digi.lib.jfx4swt

import com.sun.glass.ui.{ Application, Cursor, Pixels, Screen, Size, View, Window }
import com.sun.glass.ui.CommonDialogs.ExtensionFilter
import java.lang.ref.WeakReference
import java.nio.{ ByteBuffer, IntBuffer }
import java.util.TimerTask
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.digimead.digi.lib.jfx4swt.jfx.{ FXAdapter, FXHost, FXHost8, JFaceCanvas, JFaceCanvas8 }
import scala.language.implicitConversions

/**
 * JFX4SWT adapter application.
 */
class JFXApplication8 extends JFXApplication {
  def createCursor(t: Int): Cursor = ???
  def createCursor(x: Int, y: Int, pixels: Pixels): Cursor = ???
  def createHost(adapter: FXAdapter): FXHost = new FXHost8(ref.WeakReference(adapter))
  def createJFaceCanvas(host: FXHost): JFaceCanvas = new JFaceCanvas8(ref.WeakReference(host))
  def createPixels(width: Int, height: Int, data: ByteBuffer): Pixels = new jfx.Pixels(width, height, data)
  def createPixels(width: Int, height: Int, data: IntBuffer): Pixels = new jfx.Pixels(width, height, data)
  def createPixels(width: Int, height: Int, data: IntBuffer, scale: Float): Pixels = new jfx.Pixels(width, height, data, scale)
  def createRobot(): com.sun.glass.ui.Robot = ???
  def createTimer(runnable: Runnable) = new JFXApplication8.Timer(runnable)
  def createView(): View = new jfx.View()
  def createWindow(owner: Window, screen: Screen, styleMask: Int): Window = new jfx.Window(owner, screen, styleMask)
  def createWindow(parent: Long): Window = ???

  /*
   * Publish protected members.
   */
  override def finishTerminating() {}
  override def shouldUpdateWindow(): Boolean = ???
  def _enterNestedEventLoop(): AnyRef = ???
  def _getKeyCodeForChar(c: Char): Int = 0
  /**
   * Block the current thread and wait until the given  runnable finishes
   * running on the native event loop thread.
   */
  def _invokeAndWait(runnable: Runnable): Unit = {
    val completeLatch = new CountDownLatch(1)
    JFX.offer(new Runnable {
      def run {
        runnable.run()
        completeLatch.countDown()
      }
    })
    completeLatch.await()
  }
  /**
   * Schedule the given runnable to run on the native event loop thread
   * some time in the future, and return immediately.
   */
  def _invokeLater(runnable: Runnable) = JFX.offer(runnable)
  def _leaveNestedEventLoop(retValue: AnyRef) { ??? }
  def _postOnEventQueue(runnable: Runnable): Unit = JFX.offer(runnable)
  def _supportsTransparentWindows(): Boolean = false
  def _supportsUnifiedWindows(): Boolean = false
  def runLoop(launchable: Runnable) {
    JFX.offer(new Runnable {
      def run {
        val field = classOf[com.sun.glass.ui.Application].getDeclaredField("eventThread")
        if (!field.isAccessible())
          field.setAccessible(true)
        if (field.get(null) == null)
          field.set(null, Thread.currentThread())
        if (!Application.isEventThread()) {
          val field = classOf[com.sun.glass.ui.Application].getDeclaredField("eventThread")
          if (!field.isAccessible())
            field.setAccessible(true)
          throw new IllegalStateException(s"Unexpected event thread ${field.get(null)} vs current ${Thread.currentThread()}")
        }
        JFX.offer(new Runnable {
          def run = launchable.run()
        })
      }
    })
  }
  def staticCommonDialogs_showFileChooser(a1: Window, a2: String, a3: String, a4: Int, a5: Boolean, a6: Array[ExtensionFilter]) = null
  def staticCommonDialogs_showFileChooser(a1: Window, a2: String, a3: String, a4: String, a5: Int, a6: Boolean, a7: Array[ExtensionFilter]) = null
  def staticCommonDialogs_showFileChooser(a1: Window, a2: String, a3: String, a4: String, a5: Int, a6: Boolean, a7: Array[ExtensionFilter], a8: Int) = null
  def staticCommonDialogs_showFolderChooser(a1: Window, a2: String, a3: String) = null
  def staticCursor_getBestSize(width: Int, height: Int): Size = ???
  def staticCursor_setVisible(visible: Boolean) = ???
  def staticPixels_getNativeFormat(): Int = Pixels.Format.BYTE_BGRA_PRE
  /** Get deepest screen, but returns virtual. */
  def staticScreen_getDeepestScreen() = JFXApplication.virtualScreen
  /** Get main screen, but returns virtual. */
  def staticScreen_getMainScreen() = JFXApplication.virtualScreen
  /** Get screen for location, but returns virtual. */
  def staticScreen_getScreenForLocation(x: Int, f: Int) = JFXApplication.virtualScreen
  /** Get screen for pointer, but returns virtual. */
  def staticScreen_getScreenForPtr(nativePtr: Long) = JFXApplication.virtualScreen
  /** Get all available screens. Actually there is single virtual screen. */
  def staticScreen_getScreens() = Array(JFXApplication.virtualScreen)
  def staticScreen_getVideoRefreshPeriod(): Double = 0.0
  def staticTimer_getMaxPeriod(): Int = 1000000
  def staticTimer_getMinPeriod(): Int = 0
  def staticView_getMultiClickMaxX(): Int = ???
  def staticView_getMultiClickMaxY(): Int = ???
  def staticView_getMultiClickTime(): Long = ???
}

object JFXApplication8 {
  /** Single application timer. */
  private lazy val timer: java.util.Timer = new java.util.Timer(true)

  class Timer(runnable: Runnable) extends com.sun.glass.ui.Timer(runnable) {
    private val task = new AtomicReference[java.util.TimerTask]()

    protected def _start(runnable: Runnable, period: Int) = {
      val newTask = new Task(runnable, new WeakReference(this))
      val previousTask = this.task.getAndSet(newTask)
      if (previousTask != null)
        previousTask.cancel()
      timer.schedule(newTask, 0, period)
      1 // need something non-zero to denote success.
    }
    protected def _start(runnable: Runnable) = throw new RuntimeException("vsync timer not supported");
    protected def _stop(timer: Long) {
      val previousTask = this.task.getAndSet(null)
      if (previousTask != null)
        previousTask.cancel()
    }
    class Task(runnable: Runnable, timer: WeakReference[Timer]) extends TimerTask {
      def run {
        runnable.run()
        val container = timer.get
        if (container != null)
          container.task.compareAndSet(this, null)
      }
    }
  }
}
