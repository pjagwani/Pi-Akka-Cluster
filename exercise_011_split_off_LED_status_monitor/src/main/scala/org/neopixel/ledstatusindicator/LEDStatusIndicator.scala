/**
  * Copyright © 2018 Lightbend, Inc
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *
  * NO COMMERCIAL SUPPORT OR ANY OTHER FORM OF SUPPORT IS OFFERED ON
  * THIS SOFTWARE BY LIGHTBEND, Inc.
  *
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.neopixel.ledstatusindicator

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated, Timers}
import org.neopixel.Adafruit_NeoPixel

import scala.concurrent.duration.FiniteDuration

object LEDStatusIndicator {

  case object PollNode
  case class LedUpdate(ledUpdates: List[Long])

  def props(pollingInterval: FiniteDuration,
            clusterNodeAddress: InetSocketAddress,
            ledStatusIndicatorAddress: InetSocketAddress): Props =
    Props(new LEDStatusIndicator(pollingInterval, clusterNodeAddress, ledStatusIndicatorAddress))
}

class LEDStatusIndicator(pollingInterval: FiniteDuration,
                         clusterNodeAddress: InetSocketAddress,
                         ledStatusIndicatorAddress: InetSocketAddress) extends Actor with ActorLogging with Timers {
  import LEDStatusIndicator._
  import NeoPixelSettings._

  private val thisHost = context.system.settings.config.getString("akka.remote.netty.tcp.hostname")
  log.debug(s"Starting LED Status indicator on $thisHost")

  var pendingPolls = 0

  override def receive: Receive = uninitialised()

  def uninitialised(): Receive = akka.actor.Actor.emptyBehavior

  def showInitialising(strip: Adafruit_NeoPixel.type, showerStart: ActorRef, requestChannel: ActorRef): Receive = {
    case Terminated(`showerStart`) =>
      timers.startPeriodicTimer("node-poller", PollNode, pollingInterval)
      context.become(running(strip, requestChannel))
  }

  def running(pixel: Adafruit_NeoPixel.type, requestChannel: ActorRef): Receive = {
    case PollNode if pendingPolls >= 3 =>
      pixel.setPixelColor(0, LED_Red)
      pixel.show()
//      log.debug(s"No response for cluster status tracker")
      requestChannel ! "sendStatusUpdate"

    case PollNode =>
      pixel.setPixelColor(0, LED_Green)
      pixel.show()
      pendingPolls += 1
      requestChannel ! "sendStatusUpdate"

    case LedUpdate(ledUpdates) =>
//      log.debug(s"Cluster status tracker is responsive")
      pendingPolls = 0
      ledUpdates.zipWithIndex.foreach {
        case (color, ledIndex) =>
          pixel.setPixelColor(ledIndex, color)
      }
      pixel.setPixelColor(0, LED_Green)
      pixel.show()
  }

  override def preStart(): Unit = {
    val strip = initialiseLedStrip
    val showerStart = createStartUpIndicator(strip)
    val requestChannel = createRequestChannel()
    val responseChannel = createResponseChannel()
    context.become(showInitialising(strip, showerStart, requestChannel))
    super.preStart()
  }

  protected def createStartUpIndicator(strip: Adafruit_NeoPixel.type): ActorRef = {
    val startUpIndicator = context.actorOf(LEDStatusIndicatorShowStart.props(strip, NeoPixelSettings.LED_COUNT),"start-up-indicator")
    context.watch(startUpIndicator)
  }

  protected def createRequestChannel(): ActorRef = {
    context.actorOf(StatusRequestChannel.props(clusterNodeAddress), "status-request-channel")
  }

  protected def createResponseChannel(): ActorRef = {
    context.actorOf(StatusResponseChannel.props(ledStatusIndicatorAddress), "status-response-channel")
  }

  protected def initialiseLedStrip: Adafruit_NeoPixel.type = {
    val strip = Adafruit_NeoPixel(LED_COUNT, LED_PIN, LED_FREQ_HZ, LED_DMA, LED_INVERT, LED_BRIGHTNESS, LED_CHANNEL, LED_STRIP)
    strip.begin()
    resetAllLeds(strip)
    strip.setBrightness(1)
    strip
  }
}
