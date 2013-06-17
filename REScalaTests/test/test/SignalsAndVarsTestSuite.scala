package test


//These 3 are for JUnitRunner
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar


import react._
import react.DepHolder
import react.Var
import react.Handler

class SignalsAndVarsTestSuite extends AssertionsForJUnit with MockitoSugar {
  
  
  var v1: Var[Int] = _
  var v2: Var[Int] = _
  var v3: Var[Int] = _
  var s1: Signal[Int] = _
  var s2: Signal[Int] = _
  var s3: Signal[Int] = _
  


  @Before def initialize() {
        
  }

  @Test def handlerIsCalledWhenChangeOccurs() =  {
    
    var test = 0
    v1 = Var(1)
    v2 = Var(2)
    
    s1 = StaticSignal(List(v1,v2)){ v1.getValue + v2.getValue }
    s1 += Handler{ test += 1 }
    
    assert(s1.getVal == 3)
    assert(test == 0)
    
    v2.setVal(3)
    assert(s1.getVal == 4)
    assert(test == 1)
    
    v2.setVal(3)
    assert(s1.getVal == 4)
    assert(test == 1)

  }



  
}











