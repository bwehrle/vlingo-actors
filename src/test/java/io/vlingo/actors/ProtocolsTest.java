// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.actors;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.vlingo.actors.testkit.TestActor;

public class ProtocolsTest extends ActorsTest {

  @Test
  public void testTwoProtocols() {
    final Protocols protocols =
            testWorld.actorFor(
                    Definition.has(TwoProtocolsActor.class, Definition.NoParameters),
                    new Class<?>[] { P1.class, P2.class });
    
    final Protocols.Two<TestActor<P1>, TestActor<P2>> two = Protocols.two(protocols);
    
    two.p1().actor().do1();
    assertEquals(1, TwoProtocolsActor.instance.do1Count);
    
    two.p2().actor().do2();
    two.p2().actor().do2();
    assertEquals(2, TwoProtocolsActor.instance.do2Count);
  }

  @Test
  public void testThreeProtocols() {
    final Protocols protocols =
            testWorld.actorFor(
                    Definition.has(ThreeProtocolsActor.class, Definition.NoParameters),
                    new Class<?>[] { P1.class, P2.class, P3.class });
    
    final Protocols.Three<TestActor<P1>, TestActor<P2>, TestActor<P3>> three = Protocols.three(protocols);
    
    three.p1().actor().do1();
    assertEquals(1, ThreeProtocolsActor.instance.do1Count);
    
    three.p2().actor().do2();
    three.p2().actor().do2();
    assertEquals(2, ThreeProtocolsActor.instance.do2Count);
    
    three.p3().actor().do3();
    three.p3().actor().do3();
    three.p3().actor().do3();
    assertEquals(3, ThreeProtocolsActor.instance.do3Count);
  }

  @Test
  public void testFourProtocols() {
    final Protocols protocols =
            testWorld.actorFor(
                    Definition.has(FourProtocolsActor.class, Definition.NoParameters),
                    new Class<?>[] { P1.class, P2.class, P3.class, P4.class });
    
    final Protocols.Four<TestActor<P1>, TestActor<P2>, TestActor<P3>, TestActor<P4>> four = Protocols.four(protocols);
    
    four.p1().actor().do1();
    assertEquals(1, FourProtocolsActor.instance.do1Count);
    
    four.p2().actor().do2();
    four.p2().actor().do2();
    assertEquals(2, FourProtocolsActor.instance.do2Count);
    
    four.p3().actor().do3();
    four.p3().actor().do3();
    four.p3().actor().do3();
    assertEquals(3, FourProtocolsActor.instance.do3Count);
    
    four.p4().actor().do4();
    four.p4().actor().do4();
    four.p4().actor().do4();
    four.p4().actor().do4();
    assertEquals(4, FourProtocolsActor.instance.do4Count);
  }

  @Test
  public void testFiveProtocols() {
    final Protocols protocols =
            testWorld.actorFor(
                    Definition.has(FiveProtocolsActor.class, Definition.NoParameters),
                    new Class<?>[] { P1.class, P2.class, P3.class, P4.class, P5.class });
    
    final Protocols.Five<TestActor<P1>, TestActor<P2>, TestActor<P3>, TestActor<P4>, TestActor<P5>> five = Protocols.five(protocols);
    
    five.p1().actor().do1();
    assertEquals(1, FiveProtocolsActor.instance.do1Count);
    
    five.p2().actor().do2();
    five.p2().actor().do2();
    assertEquals(2, FiveProtocolsActor.instance.do2Count);
    
    five.p3().actor().do3();
    five.p3().actor().do3();
    five.p3().actor().do3();
    assertEquals(3, FiveProtocolsActor.instance.do3Count);
    
    five.p4().actor().do4();
    five.p4().actor().do4();
    five.p4().actor().do4();
    five.p4().actor().do4();
    assertEquals(4, FiveProtocolsActor.instance.do4Count);
    
    five.p5().actor().do5();
    five.p5().actor().do5();
    five.p5().actor().do5();
    five.p5().actor().do5();
    five.p5().actor().do5();
    assertEquals(5, FiveProtocolsActor.instance.do5Count);
  }

  public static interface P1 {
    void do1();
  }

  public static interface P2 {
    void do2();
  }

  public static interface P3 {
    void do3();
  }

  public static interface P4 {
    void do4();
  }

  public static interface P5 {
    void do5();
  }

  public static class TwoProtocolsActor extends Actor implements P1, P2 {
    public static TwoProtocolsActor instance;
    
    public int do1Count;
    public int do2Count;
    
    public TwoProtocolsActor() {
      instance = this;
    }
    
    @Override
    public void do1() {
      ++do1Count;
    }

    @Override
    public void do2() {
      ++do2Count;
    }
  }

  public static class ThreeProtocolsActor extends Actor implements P1, P2, P3 {
    public static ThreeProtocolsActor instance;
    
    public int do1Count;
    public int do2Count;
    public int do3Count;
    
    public ThreeProtocolsActor() {
      instance = this;
    }
    
    @Override
    public void do1() {
      ++do1Count;
    }

    @Override
    public void do2() {
      ++do2Count;
    }

    @Override
    public void do3() {
      ++do3Count;
    }
  }

  public static class FourProtocolsActor extends Actor implements P1, P2, P3, P4 {
    public static FourProtocolsActor instance;
    
    public int do1Count;
    public int do2Count;
    public int do3Count;
    public int do4Count;
    
    public FourProtocolsActor() {
      instance = this;
    }
    
    @Override
    public void do1() {
      ++do1Count;
    }

    @Override
    public void do2() {
      ++do2Count;
    }

    @Override
    public void do3() {
      ++do3Count;
    }

    @Override
    public void do4() {
      ++do4Count;
    }
  }

  public static class FiveProtocolsActor extends Actor implements P1, P2, P3, P4, P5 {
    public static FiveProtocolsActor instance;
    
    public int do1Count;
    public int do2Count;
    public int do3Count;
    public int do4Count;
    public int do5Count;
    
    public FiveProtocolsActor() {
      instance = this;
    }
    
    @Override
    public void do1() {
      ++do1Count;
    }

    @Override
    public void do2() {
      ++do2Count;
    }

    @Override
    public void do3() {
      ++do3Count;
    }

    @Override
    public void do4() {
      ++do4Count;
    }

    @Override
    public void do5() {
      ++do5Count;
    }
  }
}
