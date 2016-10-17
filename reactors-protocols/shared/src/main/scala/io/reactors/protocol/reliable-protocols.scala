package io.reactors
package protocol






trait ReliableProtocols {
  type Reliable[T] = (Channel[T], Subscription)

  object Reliable {
    type Server[T] = io.reactors.protocol.TwoWay.Server[T, Int]

    type Req[T] = io.reactors.protocol.TwoWay.Req[T, Int]

    type TwoWay[I, O] = (Channel[I], Events[O], Subscription)

    object TwoWay {
      type Server[I, O] =
        io.reactors.protocol.Server[Reliable.Server[I], Reliable.Server[O]]

      type Req[I, O] =
        io.reactors.protocol.Server.Req[Reliable.Server[I], Reliable.Server[O]]
    }
  }

  implicit class ReliableChannelBuilderOps(val builder: ChannelBuilder) {
    def reliable[@specialized(Int, Long, Double) T]: Connector[Reliable.Req[T]] = {
      builder.open[Reliable.Req[T]]
    }
  }

  implicit class ReliableConnectorOps[@spec(Int, Long, Double) T](
    val connector: Connector[Reliable.Req[T]]
  ) {
    def rely(window: Int)(implicit a: Arrayable[T]): Connector[Reliable.Req[T]] = {
      connector.twoWayServe { outIn =>
        ???
      }
      connector
    }
  }

  implicit class ReliableTwoWayChannelBuilderOps(val builder: ChannelBuilder) {
    def reliableTwoWayServer[I, O]: Connector[Reliable.TwoWay.Req[I, O]] = {
      ???
    }
  }

  implicit class ReliableTwoWayConnectorOps[
    @spec(Int, Long, Double) I,
    @spec(Int, Long, Double) O
  ](val connector: Connector[Reliable.TwoWay.Req[I, O]]) {
    def rely(window: Int, f: Reliable.TwoWay[O, I] => Unit): Reliable.TwoWay[I, O] = {
      ???
    }
  }
}
