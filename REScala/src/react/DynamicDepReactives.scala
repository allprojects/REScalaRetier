package react

import scala.collection.mutable.ListBuffer
import react.events.Event
import react.events.ChangedEventNode



/* A node that has nodes that depend on it */
//trait FixedDepHolder extends Reactive {
//  val fixedDependents = new ListBuffer[Dependent]
//  def addFixedDependent(dep: Dependent) = fixedDependents += dep    
//  def removeFixedDependent(dep: Dependent) = fixedDependents -= dep
  // def notifyDependents(change: Any): Unit = dependents.map(_.dependsOnchanged(change,this)) 
//}




class VarSynt[T](initval: T) extends DepHolder {
  private[this] var value: T = initval
  def setVal(newval: T): Unit = {
    value = newval // .asInstanceOf[T] // to make it covariant ?
    
    TS.nextRound  // Testing
    timestamps += TS.newTs // testing

    notifyDependents(value)
    ReactiveEngine.startEvaluation
  }  
  def getVal = value
  
  def apply(s: SignalSynt[_]) = {
    s.reactivesDependsOnCurrent += this 
    getVal
  }
  
  /* Testing */
  val timestamps = ListBuffer[Stamp]()
}
object VarSynt {
  def apply[T](initval: T) = new VarSynt(initval)
}





/**
 * A time changing value
 */
class SignalSynt[+T](reactivesDependsOnUpperBound: List[DepHolder])(expr: SignalSynt[T] => T)
  extends Dependent with DepHolder {

  val timestamps = ListBuffer[Stamp]() // Testing

  reactivesDependsOnUpperBound.map(r => { // For glitch freedom
    if (r.level >= level) level = r.level + 1   
  })

  /* Initial evaluation */
  val reactivesDependsOnCurrent = ListBuffer[DepHolder]()
  private[this] var currentValue = reEvaluate()
  def getVal = currentValue

  def triggerReevaluation() = reEvaluate

  def reEvaluate(): T = {

    /* Collect dependencies during the evaluation */
    reactivesDependsOnCurrent.map(_.removeDependent(this)) // remove me from the dependencies of the vars I depend on ! 
    reactivesDependsOnCurrent.clear
    timestamps += TS.newTs // Testing
    val tmp = expr(this)  // Evaluation
    dependOn ++= reactivesDependsOnCurrent
    reactivesDependsOnCurrent.map(_.addDependent(this))

    /* Notify dependents only of the value chenged */
    if (tmp != currentValue) {
      currentValue = tmp
      timestamps += TS.newTs // Testing
      notifyDependents(currentValue)
    } else {
      timestamps += TS.newTs // Testing
    }
    tmp
  }
  override def dependsOnchanged(change: Any, dep: DepHolder) = {
    ReactiveEngine.addToEvalQueue(this)
  }
  
  def apply(s: SignalSynt[_]) = {
    s.reactivesDependsOnCurrent += this 
    getVal
  }

  /* To add handlers */
  def +=(handler: Dependent) {
    handler.level = level + 1 // For glitch freedom 
    addDependent(handler)
  }
  def -=(handler: Dependent) = removeDependent(handler)
  
  
  def change[U >: T]: Event[(U, U)] = new ChangedEventNode[(U, U)](this)

}

/**
 * A syntactic signal
 */
object SignalSynt {
  
  def apply[T](reactivesDependsOn: List[DepHolder])(expr: SignalSynt[T]=>T) =
    new SignalSynt(reactivesDependsOn)(expr)
  
  type DH = DepHolder
  
  def apply[T]()(expr: SignalSynt[T]=>T): SignalSynt[T] = apply(List())(expr)
  def apply[T](r1: DH)(expr: SignalSynt[T]=>T): SignalSynt[T] = apply(List(r1))(expr)
  def apply[T](r1: DH,r2: DH)(expr: SignalSynt[T]=>T): SignalSynt[T] = apply(List(r1,r2))(expr)
  def apply[T](r1: DH,r2: DH,r3: DH)(expr: SignalSynt[T]=>T): SignalSynt[T] = apply(List(r1,r2,r3))(expr)
  def apply[T](r1: DH,r2: DH,r3: DH,r4: DH)(expr: SignalSynt[T]=>T): SignalSynt[T] = apply(List(r1,r2,r3,r4))(expr)
  def apply[T](r1: DH,r2: DH,r3: DH,r4: DH,r5: DH)(expr: SignalSynt[T]=>T): SignalSynt[T] = apply(List(r1,r2,r3,r4,r5))(expr)
}



