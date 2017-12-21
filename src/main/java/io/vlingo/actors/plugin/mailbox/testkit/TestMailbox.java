// Copyright © 2012-2017 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.actors.plugin.mailbox.testkit;

import io.vlingo.actors.Mailbox;
import io.vlingo.actors.Message;
import io.vlingo.actors.testkit.TestWorld;

public class TestMailbox implements Mailbox {
  public static final String Name = "testerMailbox";

  public TestMailbox() { }
  
  @Override
  public void run() {
    throw new UnsupportedOperationException("TesterMailbox does not support this operation.");
  }

  @Override
  public void close() {
  }

  @Override
  public boolean isDelivering() {
    throw new UnsupportedOperationException("TesterMailbox does not support this operation.");
  }

  @Override
  public boolean delivering(final boolean flag) {
    throw new UnsupportedOperationException("TesterMailbox does not support this operation.");
  }

  @Override
  public void send(final Message message) {
    try {
      TestWorld.track(message);
      message.deliver();
    } catch (Throwable t) {
      throw new RuntimeException(t.getMessage(), t);
    }
  }

  @Override
  public Message receive() {
    throw new UnsupportedOperationException("TesterMailbox does not support this operation.");
  }
}
