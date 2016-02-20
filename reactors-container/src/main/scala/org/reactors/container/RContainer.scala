package org.reactors
package container






trait RContainer[@spec(Int, Long, Double) T] {
  self =>

  def inserts: Events[T]
  
  def removes: Events[T]

  def foreach: Events[T]

  def size: Events[Int]

  def count(p: T => Boolean): Events[Int] = new RContainer.Count(this, p)

  // def forall(p: T => Boolean): Events[Boolean]

  // def exists(p: T => Boolean): Events[Boolean]

  // def toAggregate(z: T)(op: (T, T) => T): Signal[T]

  // def toCommutativeAggregate(z: T)(op: (T, T) => T): Signal[T]

  // def toAbelianAggregate(z: T)(op: (T, T) => T)(inv: (T, T) => T): Signal[T]

  // def map[@spec(Int, Long, Double) S](f: T => S): RContainer[S]

  // def filter(p: T => Boolean): RContainer[T]

  // def collect[S <: AnyRef](pf: PartialFunction[T, S])(implicit e: T <:< AnyRef):
  //   RContainer[S]

  // def union(that: RContainer[T])(
  //   implicit count: RContainer.Union.Count[T], a: Arrayable[T]
  // ): RContainer[T]

  // def to[That <: RContainer[T]](implicit factory: RBuilder.Factory[T, That]): That

}


object RContainer {

  class Count[@spec(Int, Long, Double) T](
    val self: RContainer[T],
    val pred: T => Boolean
  ) extends Events[Int] {
    def onReaction(obs: Observer[Int]): Subscription = {
      var initial = 0
      self.foreach.onEvent(x => if (pred(x)) initial += 1).unsubscribe()
      val insertObs = new CountInsertObserver(obs, initial, pred)
      val removeObs = new CountRemoveObserver(obs, insertObs, pred)
      new Subscription.Composite(
        self.inserts.onReaction(insertObs),
        self.inserts.onReaction(removeObs)
      )
    }
  }

  class CountInsertObserver[@spec(Int, Long, Double) T](
    val target: Observer[Int],
    var count: Int,
    val pred: T => Boolean
  ) extends Observer[T] {
    var done = false
    def init(self: Observer[T]) {
      target.react(count)
    }
    init(this)
    def react(x: T) = if (!done) {
      if (pred(x)) {
        count += 1
        target.react(count)
      }
    }
    def except(t: Throwable) = if (!done) {
      target.except(t)
    }
    def unreact() = if (!done) {
      done = true
      target.unreact()
    }
  }

  class CountRemoveObserver[@spec(Int, Long, Double) T](
    val target: Observer[Int],
    val insertObs: CountInsertObserver[T],
    val pred: T => Boolean
  ) extends Observer[T] {
    def react(x: T) = if (!insertObs.done) {
      if (pred(x)) {
        insertObs.count -= 1
        target.react(insertObs.count)
      }
    }
    def except(t: Throwable) = insertObs.except(t)
    def unreact() = insertObs.unreact()
  }

}