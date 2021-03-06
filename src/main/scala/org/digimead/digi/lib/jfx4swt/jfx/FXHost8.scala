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

import com.sun.javafx.embed.{ EmbeddedSceneInterface, EmbeddedStageInterface }
import com.sun.javafx.tk.{ TKDragGestureListener, TKDragSourceListener, TKDropTargetListener, TKScene, TKStage }
import java.lang.reflect.InvocationTargetException
import java.nio.IntBuffer
import java.util.concurrent.locks.ReentrantLock
import javafx.application.Platform
import javafx.scene.Scene
import org.digimead.digi.lib.Disposable
import org.digimead.digi.lib.jfx4swt.JFX
import org.eclipse.swt.SWT
import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.ref.WeakReference

/**
 * HostInterface implementation that connects scene, stage and adapter together.
 *
 * IMPORTANT JavaFX have HUGE performance loss with embedded content.
 * Those funny code monkeys from Oracle corporation redraw ALL content every time. :-/ Shit? Shit! Shit!!!
 * It is better to create few small contents than one huge.
 *
 * val adapter = new MyFXAdapter
 * val host = new FXHost(adapter)
 * val stage = new FXEmbedded(host)
 * stage.open(scene)
 */
class FXHost8(adapter: WeakReference[FXAdapter]) extends FXHost(adapter) {
  private[this] final var dataToConvert = 0
  private[this] final var destinationPointer = 0
  private[this] final var height = SWT.DEFAULT
  private[this] final var rawPixelsBuf: IntBuffer = null
  private[this] final var rawPixelsBufArray: Array[Int] = null
  @volatile private[this] final var scene: EmbeddedSceneInterface = null
  private[this] final var sourcePointer = 0
  @volatile private[this] final var stage: EmbeddedStageInterface = null
  private[this] final var width = SWT.DEFAULT
  private[this] final var pipeBuf: Array[Int] = null
  private[this] final val pipeLock = new ReentrantLock()
  @volatile private[this] var disposed = false
  @volatile protected[this] final var userScene: Scene = null
  // Render thread returns scene with old size rendered on frame with new size!!! ???
  // Somewhere inside Quantum few neighbor operators/locks are not ordered.
  // It is a case of ~1-2ms or less
  @volatile private[this] final var oneMoreFramePlease = true
  private[this] var wantRepaint: Future[_] = null
  private[this] implicit val ec = JFX.ec

  def dispose() {
    disposed = true
    JFX.assertEventThread(true)
    if (Platform.isFxApplicationThread()) {
      pipeLock.lock()
      try {
        if (stage != null)
          disposeEmbeddedStage()
        if (scene != null)
          disposeEmbeddedScene()
        Option(rawPixelsBuf).foreach(_.clear())
        rawPixelsBuf = null
        rawPixelsBufArray = null
        pipeBuf = null
        height = SWT.DEFAULT
        width = SWT.DEFAULT
        userScene = null
      } finally pipeLock.unlock()
    } else {
      JFX.exec { dispose }
    }
    adapter.get.foreach(_.dispose())
  }
  def embeddedScene = Option(scene)
  def embeddedStage = Option(stage)
  def grabFocus(): Boolean = requestFocus()
  def repaint(): Unit = for {
    adapter ← adapter.get if (scene != null && !disposed)
  } if (pipeLock.tryLock()) {
    // We want empty frame to draw.
    val toDraw = adapter.frameEmpty.getAndSet(null)
    val toDrawWidth = width
    val toDrawHeight = height
    try {
      if (!oneMoreFramePlease) {
        if (toDraw != null && toDrawWidth > 0 && toDrawHeight > 0) {
          if (scene.getPixels(rawPixelsBuf, toDrawWidth, toDrawHeight)) {
            System.arraycopy(rawPixelsBufArray, 0, pipeBuf, 0, rawPixelsBufArray.length)
            // IMHO This is the best of the worst
            // It is not block event thread while large frame processing ~-20ms
            // Future allow reduce process time to 1-5ms vs 15-25ms

            // IMPORTANT
            // There may be 'dispose' between 'repaint' and 'Future'
            Future { // Memory overhead is minimal.
              // 2. Convert pixelsBuf to imageData and save it to empty frame.
              pipeLock.lock()
              if (pipeBuf != null) try {
                destinationPointer = 0
                sourcePointer = 0
                for (y ← 0 until toDrawHeight) {
                  for (x ← 0 until toDrawWidth) {
                    dataToConvert = pipeBuf(sourcePointer)
                    sourcePointer += 1
                    toDraw(destinationPointer) = (dataToConvert & 0xFF).asInstanceOf[Byte] //dst:blue
                    destinationPointer += 1
                    toDraw(destinationPointer) = ((dataToConvert >> 8) & 0xFF).asInstanceOf[Byte] //dst:green
                    destinationPointer += 1
                    toDraw(destinationPointer) = ((dataToConvert >> 16) & 0xFF).asInstanceOf[Byte] //dst:green
                    destinationPointer += 1
                    toDraw(destinationPointer) = 0 //alpha
                    destinationPointer += 1
                  }
                }
                if (adapter.frameFull.compareAndSet(null, toDraw)) {
                  // Full frame are ready for new chunk
                  adapter.redraw()
                } else {
                  // Fail. Put an empty frame back.
                  adapter.frameEmpty.set(toDraw)
                }
              } catch {
                case e: ArrayIndexOutOfBoundsException ⇒
                  adapter.frameEmpty.set(toDraw)
                  sceneNeedsRepaint
                case e: Throwable ⇒
                  adapter.frameEmpty.set(toDraw)
                  FXHost.log.error("FXHost pipe. " + e.getMessage(), e)
              }
              pipeLock.unlock()
            }
          } else {
            // Fail. Put an empty frame back.
            adapter.frameEmpty.set(toDraw)
          }
        } else {
          if (toDraw != null)
            // Ok. But put an empty frame back.
            adapter.frameEmpty.set(toDraw)
          if (wantRepaint == null) wantRepaint = Future {
            // toDraw == null, so adapter.frameEmpty.set(toDraw) isn't required
            for (i ← 1 to 1000 if adapter.frameEmpty.get == null) // 5 msec
              Thread.sleep(5) // 200 FPS max
            JFX.execAsync {
              wantRepaint = null
              repaint()
              sceneNeedsRepaint
            }
          }
        }
      } else {
        oneMoreFramePlease = false
        if (toDraw != null)
          adapter.frameEmpty.set(toDraw)
        JFX.execAsync { // Required
          if (scene != null && stage != null) {
            if (toDrawWidth > 0 && toDrawHeight > 0) {
              scene.setSize(toDrawWidth, toDrawHeight)
              stage.setSize(toDrawWidth, toDrawHeight)
            }
            sceneNeedsRepaint
          }
        }
      }
    } catch {
      case e: Throwable ⇒
        if (toDraw != null)
          adapter.frameEmpty.set(toDraw)
        FXHost.log.error("FXHost pipe. " + e.getMessage(), e)
    } finally pipeLock.unlock()
  }
  def requestFocus(): Boolean = if (disposed) false else adapter.get.map(_.requestFocus()).getOrElse(false)
  def sceneNeedsRepaint() = embeddedScene.foreach { _.asInstanceOf[{ def entireSceneNeedsRepaint() }].entireSceneNeedsRepaint }
  def setCursor(cursorFrame: com.sun.javafx.cursor.CursorFrame) {}
  def setEmbeddedScene(scene: EmbeddedSceneInterface) {
    if (scene == null && disposed)
      return
    pipeLock.lock()
    try {
      if (this.scene != null)
        disposeEmbeddedScene()
      if (scene != null)
        // King regards to Empire_Phoenix from hub.jmonkeyengine.org
        // 8_u60 and later fix
        try {
          val scaler = scene.getClass().getMethod("setPixelScaleFactor", java.lang.Float.TYPE)
          scaler.setAccessible(true)
          scaler.invoke(scene, 1f: java.lang.Float)
        } catch {
          case e: IllegalAccessException ⇒
          case e: IllegalArgumentException ⇒
          case e: InvocationTargetException ⇒
          case e: NoSuchMethodException ⇒
          case e: SecurityException ⇒
        }
      dataToConvert = 0
      destinationPointer = 0
      rawPixelsBuf = null
      rawPixelsBufArray = null
      sourcePointer = 0
      this.scene = scene
      pipeBuf = null
      oneMoreFramePlease = true
    } finally pipeLock.unlock()
  }
  def setEmbeddedStage(stage: EmbeddedStageInterface) {
    if (stage == null && disposed)
      return
    if (stage != null && width != SWT.DEFAULT && height != SWT.DEFAULT)
      stage.setSize(width, height)
    if (this.stage != null)
      disposeEmbeddedStage()
    this.stage = stage
  }
  def setEnabled(enabled: Boolean) = if (!disposed) adapter.get.map(_.setEnabled(enabled))
  /** Set host preferred size. */
  def setPreferredSize(x: Int, y: Int): Unit = for {
    adapter ← adapter.get
  } {
    JFX.assertEventThread()
    if ((width == x && height == y) || x == SWT.DEFAULT || y == SWT.DEFAULT || disposed)
      return
    pipeLock.lock()
    try {
      width = x
      height = y
      val scanline = width * 4
      adapter.frameOne.set(new Array[Byte](scanline * height))
      adapter.frameTwo.set(new Array[Byte](scanline * height))
      adapter.frameEmpty.set(adapter.frameOne.get())
      rawPixelsBuf = IntBuffer.allocate(width * height)
      rawPixelsBufArray = rawPixelsBuf.array()
      pipeBuf = new Array(rawPixelsBufArray.length)
      if (stage != null)
        stage.setSize(width, height)
      if (scene != null)
        scene.setSize(width, height)
      oneMoreFramePlease = true
    } finally pipeLock.unlock()
    adapter.onHostResize(width, height)
  }
  /** Assign user scene to host which is required for calculating preferred size. */
  def setScene(scene: Scene) {
    JFX.assertEventThread()
    userScene = scene
    if (width == SWT.DEFAULT || height == SWT.DEFAULT)
      setPreferredSize(width, height)
  }
  def traverseFocusOut(forward: Boolean): Boolean = if (disposed) false else adapter.get.map(_.traverseFocusOut(forward)).getOrElse(false)
  def ungrabFocus(): Unit = {}

  /** Dispose embedded scene. */
  protected def disposeEmbeddedScene() {
    val scene = this.scene
    this.scene = null
    scene match {
      case scene: TKScene ⇒
        scene.setFillPaint(null)
        scene.setCamera(null)
        scene.setDragStartListener(null)
        scene.asInstanceOf[{ def setTKDragGestureListener(l: TKDragGestureListener) }].setTKDragGestureListener(null)
        scene.asInstanceOf[{ def setTKDragSourceListener(l: TKDragSourceListener) }].setTKDragSourceListener(null)
        scene.asInstanceOf[{ def setTKDropTargetListener(l: TKDropTargetListener) }].setTKDropTargetListener(null)
        scene.setTKSceneListener(null)
        scene.setTKScenePaintListener(null)
        scene.markDirty()
        scene.asInstanceOf[{ def sceneChanged() }].sceneChanged()
    }
  }
  /** Dispose embedded stage. */
  protected def disposeEmbeddedStage() {
    val stage = this.stage
    this.stage = null
    this.userScene = null
    stage match {
      case stage: TKStage ⇒
        stage.close()
        stage.setTKStageListener(null)
    }
  }
}
