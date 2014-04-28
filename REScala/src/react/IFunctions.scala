package react

import react.events._
import react.log._
import scala.collection.mutable.ListBuffer
import scala.collection.LinearSeq

object IFunctions {

  /** folds events with a given fold function to create a Signal */
  def fold[T, A](e: Event[T], init: A)(f: (A, T) => A): Signal[A] = new FoldedSignal(e, init, f)

  /** Iterates a value on the occurrence of the event. */
  def iterate[A](e: Event[_], init: A)(f: A => A): Signal[A] = fold(e, init)((acc, _) => f(acc))

  /**
   * Counts the occurrences of the event. Starts from 0, when the event has never been
   *  fired yet. The argument of the event is simply discarded.
   */
  def count(e: Event[_]): Signal[Int] = fold(e, 0)((acc, _) => acc + 1)

  /**
   * Calls f on each occurrence of event e, setting the Signal to the generated value.
   *  The initial signal is obtained by f(init)
   */
  def set[F, G](e: Event[F], init: F)(f: F => G): Signal[G] = fold(e, f(init))((_, v) => f(v))

  /** calls factory on each occurrence of event e, resetting the Signal to a newly generated one */
  def reset[T, A](e: Event[T], init: T)(factory: (T) => Signal[A]): Signal[A] = {
    val ref: Signal[Signal[A]] = set(e, init)(factory)
    SignalSynt { s: SignalSynt[A] => ref(s)(s) } // cannot express without high order signals
  }

  /** returns a signal holding the latest value of the event. */
  def latest[T](e: Event[T], init: T): Signal[T] = IFunctions.fold(e, init)((_, v) => v)

  /** Holds the latest value of an event as an Option, None before the first event occured */
  def latestOption[T](e: Event[T]): Signal[Option[T]] = latest(e.map((x: T) => Some(x)), None)

  /** collects events resulting in a variable holding a list of all values. */
  def list[T](e: Event[T]): Signal[List[T]] = fold(e, List[T]())((acc, v) => v :: acc)

  /**
   * Returns a signal which holds the last n events in a list. At the beginning the
   *  list increases in size up to when n values are available
   */
  def last[T](e: Event[T], n: Int): Signal[LinearSeq[T]] =
    fold(e, new collection.mutable.Queue[T]) {
      (acc: collection.mutable.Queue[T], v: T) =>
        if (acc.length >= n) acc.dequeue
        //v +=: acc // (prepend)
        acc += v // (append)
        acc
    }

  /** Return a Signal that is updated only when e fires, and has the value of the signal s */
  def snapshot[V](e: Event[_], s: Signal[V]): Signal[V] = fold(e, s.getVal)((_, _) => s.getVal)

  /**
   * Switch to a signal once, on the occurrence of event e. Initially the
   *  return value is set to the original signal. When the event fires,
   *  the result is a constant signal whose value is the value of the event.
   */
  def switchTo[T](e: Event[T], original: Signal[T]): Signal[T] = {
    val latest = latestOption(e)
    StaticSignal(latest, original) {
      latest.getVal match {
        case None => original.getVal
        case Some(x) => x
      }
    }
  }

  /** Switch to a new Signal once, on the occurrence of event e. */
  def switchOnce[T](e: Event[_], original: Signal[T], newSignal: Signal[T]): Signal[T] = {
    val latest = latestOption(e)
    StaticSignal(latest, original, newSignal) {
      latest.getVal match {
        case None => original.getVal
        case Some(_) => newSignal.getVal
      }
    }
  }

  /** Switch back and forth between two signals on occurrence of event e */
  def toggle[T](e: Event[_], a: Signal[T], b: Signal[T]): Signal[T] = {
    val switched: Signal[Boolean] = iterate(e, false) { !_ }
    StaticSignal(switched, a, b) { if (switched.getVal) b.getVal else a.getVal }
  }

  /** Like latest, but delays the value of the resulting signal by n occurrences */
  def delay[T](e: Event[T], init: T, n: Int): Signal[T] = {
    val history: Signal[LinearSeq[T]] = last(e, n + 1)
    StaticSignal(history) {
      val h = history.getVal
      if (h.size <= n) init else h.head
    }
  }

  /** Delays this signal by n occurrences */
  def delay[T](signal: Signal[T], n: Int): Signal[T] = delay(signal.changed, signal.getVal, n)

  /** lifts a function A => B to work on reactives */
  def lift[A, B](f: A => B): (Signal[A] => Signal[B]) = (a => StaticSignal[B](a) { f(a.getVal) })

  /** Generates a signal from an event occurrence */
  trait Factory[E, A] {
    def apply(eventValue: E): (Signal[A], Factory[E, A])
  }

  /** Generates a signal from an initial signal and a factory for subsequent ones */
  def switch[T, A](e: Event[T])(init: Signal[A])(factory: Factory[T, A]): Signal[A] =
    new SwitchedSignal(e, init, factory)
  
  /** Unwraps an event wrapped in a signal */
  def unwrap[T](wrappedEvent: Signal[Event[T]]): Event[T] = 
    new WrappedEvent(wrappedEvent)
}
