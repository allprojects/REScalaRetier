package react.events

import scala.collection.mutable.ListBuffer
import scala.reflect.runtime.universe._
import react._


trait Event[+T] extends DepHolder {

  def +=(react: T => Unit)

  def -=(react: T => Unit)

  /**
   * Events disjunction.
   */
  def ||[S >: T, U <: S](other: Event[U]) = new EventNodeOr[S](this, other)

  /**
   * Event filtered with a predicate
   */
  def &&[U >: T](pred: U => Boolean) = new EventNodeFilter[U](this, pred)

  /**
   * Event filtered with a boolean variable
   */
  def &&[U >: T](pred: =>Boolean) = new EventNodeFilter[U](this, _ => pred)
  
  /**
   * Event is triggered except if the other one is triggered
   */
  def \[U >: T](other: Event[U]) = new EventNodeExcept[U](this, other)

  /**
   * Events conjunction
   */
  //def and[U, V, S >: T](other: Event[U], merge: (S, U) => V) = new EventNodeAnd[S, U, V](this, other, merge)

  /*
  * Event conjunction with a merge method creating a tuple of both event parameters
  */
  //def &&[U, S >: T](other: Event[U]) = new EventNodeAnd[S, U, (S, U)](this, other, (p1: S, p2: U) => (p1, p2))

  /**
   * Transform the event parameter
   */
  def map[U, S >: T](mapping: S => U) = new EventNodeMap[S, U](this, mapping)

  /**
   * Drop the event parameter; equivalent to map((_: Any) => ())
   */
  def dropParam[S >: T] = new EventNodeMap[S, Unit](this, (_: Any) => ())

  
  
  
  /** The latest parameter value of this event occurrence */
  //import annotation.unchecked.uncheckedVariance
  //lazy val latest : Signal[Option[T @uncheckedVariance]] = Signal.latestOption(this)

  // def hold: Signal[T] =
  def fold[A](init: A)(fold: (A, T) => A): Signal[A] = IFunctions.fold(this, init)(fold)
  def iterate[A](init: A)(f: A => A): Signal[A] = IFunctions.iterate(this, init)(f)
  //def set[A](init: A)(f: (T=>A)): Signal[A] = Signal.set(this,init)(f)
  //def set[A,T](init: A)(f: (T=>A)): Signal[A] = Signal.set(this,init)(f)

  def latest[S >: T](init: S): Signal[S] = IFunctions.latest(this, init)
  //def reset[S >: T, A](init : S)(f : (S) => Signal[A]) : Signal[A] = Signal.reset(this, init)(f)
  
  def last[S >: T](n: Int): Signal[List[S]] = IFunctions.last[S](this, n)
  def list[S >: T](): Signal[List[S]] = IFunctions.list[S](this)
  
  def toggle[A](a: Signal[A], b: Signal[A]): Signal[A] = IFunctions.toggle(this, a, b)
  def snapshot[A](s: Signal[A]): Signal[A] = IFunctions.snapshot(this, s)
  
  def delay[S >: T](init: S, n: Int): Signal[S] = IFunctions.delay(this, init, n)
  // TODO: make another delay that returns an event
  
}



/**
 * Wrapper for an anonymous function 
 */
class EventHandler[T] (fun: T=>Unit) extends Dependent {
    val f = fun
    var storedVal: T = _
    override def dependsOnchanged(change: Any, dep: DepHolder) {
      storedVal = change.asInstanceOf[T]  // ?? 
      ReactiveEngine.addToEvalQueue(this)
    }  
    def triggerReevaluation = fun(storedVal)
    override def equals(other: Any) = other match {
      case other: EventHandler[T] => fun.equals(other.f)
      case _ => false
    }
}
object EventHandler{
	def apply[T] (fun: T=>Unit) = new EventHandler(fun)
}


/*
 *  Base class for events.
 */
abstract class EventNode[T] extends Event[T]  with DepHolder {

  def +=(react: T => Unit) {
    val handler = EventHandler(react)
    handler.level = level + 1 // For glitch freedom 
    addDependent(handler)
  }
  // TODO: won't work, because of the handler wrapper
  def -=(react: T => Unit) {
    val handler = EventHandler(react)
    removeDependent(handler)
  }
}


/*
 * An implementation of an imperative event
 */
class ImperativeEvent[T] extends EventNode[T] {
  
  /* Trigger the event */
  def apply(v: T): Unit = {   
    TS.nextRound 
    timestamps += TS.newTs
    notifyDependents(v)
    ReactiveEngine.startEvaluation
  }

  /* Testing */
  val timestamps = ListBuffer[Stamp]()
  override def toString = getClass.getName
}



/*
 * Used to model the change event of a signal. Keeps the last value
 */
class ChangedEventNode[T](d: DepHolder) extends EventNode[T] with Dependent {

  level = d.level + 1 // Static, for glitch freedom  
  d.addDependent(this) // To be notified in the future
  dependOn += d
  
  var storedVal: (Any,Any) = (null, null)
  
  def triggerReevaluation() {
    timestamps += TS.newTs // Testing
    notifyDependents(storedVal)
  }
  
  override def dependsOnchanged(change: Any,dep: DepHolder) = {
    storedVal = (storedVal._2,change)
    ReactiveEngine.addToEvalQueue(this)
  }
  
  /* Testing */
  val timestamps = ListBuffer[Stamp]()
  override def toString = "(" + " InnerNode" + d + ")"
}


// TODO: never used
/*
 * An event automatically triggered by the framework.
 */
class InnerEventNode[T](d: DepHolder) extends EventNode[T] with Dependent {

  level = d.level + 1 // For glitch freedom  
  d.addDependent(this) // To be notified in the future
  dependOn += d
  
  var storedVal: Any = _
  
  def triggerReevaluation() {
    timestamps += TS.newTs // Testing
    notifyDependents(storedVal)
  }
  
  override def dependsOnchanged(change: Any,dep: DepHolder) = {
    storedVal = change
    ReactiveEngine.addToEvalQueue(this)
  }
  
  /* Testing */
  val timestamps = ListBuffer[Stamp]()
  override def toString = "(" + " InnerNode" + d + ")"
}



/*
 * Implementation of event disjunction
 */
class EventNodeOr[T](ev1: Event[_ <: T], ev2: Event[_ <: T]) extends EventNode[T] with Dependent {

  /*
   * The event is wxecuted once and only once even if both sources fire in the
   * same propagation cycle because the engine queue removes duplicates.
   */
  
  level = (ev1.level max ev2.level) + 1 // For glitch freedom  
  ev1.addDependent(this) // To be notified in the future
  ev2.addDependent(this)
  dependOn ++= List(ev1,ev2)
  
  var storedVal: Any = _
  
  def triggerReevaluation() {
    timestamps += TS.newTs // Testing
    notifyDependents(storedVal)
  }
  
  override def dependsOnchanged(change: Any,dep: DepHolder) = {
    storedVal = change
    ReactiveEngine.addToEvalQueue(this)
  }
  
  /* Testing */
  val timestamps = ListBuffer[Stamp]()
  override def toString = "(" + ev1 + " || " + ev2 + ")"
}


/*
 * Implements filtering event by a predicate
 */
class EventNodeFilter[T](ev: Event[T], f: T => Boolean) extends EventNode[T] with Dependent {

  level = ev.level + 1   // For glitch freedom  
  ev.addDependent(this) // To be notified in the future
  dependOn += ev
  
  var storedVal: T = _
  
  def triggerReevaluation() {
    timestamps += TS.newTs // Testing
    if(f(storedVal)) notifyDependents(storedVal)
  }
  
  override def dependsOnchanged(change: Any,dep: DepHolder) = {
    storedVal = change.asInstanceOf[T]
    ReactiveEngine.addToEvalQueue(this)
  }
  
  /* Testing */
  val timestamps = ListBuffer[Stamp]()
  override def toString = "(" + ev + " && <predicate>)"
}


/*
 * Implements transformation of event parameter
 */
class EventNodeMap[T, U](ev: Event[T], f: T => U) 
  extends EventNode[U] with Dependent {

  level = ev.level + 1   // For glitch freedom  
  ev.addDependent(this) // To be notified in the future
  dependOn += ev
  
  var storedVal: T = _
  
  def triggerReevaluation() {
    timestamps += TS.newTs // Testing
    notifyDependents(f(storedVal))
  }
  
  override def dependsOnchanged(change: Any,dep: DepHolder) = {
    storedVal = change.asInstanceOf[T]
    ReactiveEngine.addToEvalQueue(this)
  }
  
  /* Testing */
  val timestamps = ListBuffer[Stamp]()
  override def toString = "(" + ev + " && <predicate>)"
}


/*
 * Implementation of event except
 */
class EventNodeExcept[T](accepted: Event[T], except: Event[T]) 
  extends EventNode[T] with Dependent {

  // The id of the last received event
  var lastTSAccepted = Stamp(-1,-1) // No round yet
  var lastTSExcept= Stamp(-1,-1)

  level = (accepted.level max except.level) + 1 // For glitch freedom  
  accepted.addDependent(this) // To be notified in the future
  except.addDependent(this)
  dependOn ++= List(accepted,except)
  
  var storedVal: Any = _
  
  def triggerReevaluation() {
    timestamps += TS.newTs // Testing
    // Fire only if accepted is the one that fired and except did'fire
    if (lastTSAccepted.roundNum > lastTSExcept.roundNum ) notifyDependents(storedVal)
  }
  
  override def dependsOnchanged(change: Any, dep: DepHolder) = {
    if (dep == accepted) {
      lastTSAccepted = TS.getCurrentTs
      storedVal = change
      ReactiveEngine.addToEvalQueue(this)
    }
    if (dep == except) lastTSExcept = TS.getCurrentTs
  }
  
  /* Testing */
  val timestamps = ListBuffer[Stamp]()
  override def toString = "(" + accepted + " \\ " + except + ")"
}


