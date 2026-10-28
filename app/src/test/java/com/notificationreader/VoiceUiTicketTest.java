package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class VoiceUiTicketTest {
  @Test
  public void resultCanBeConsumedOnlyOnce() {
    VoiceUiTicket ticket = new VoiceUiTicket();
    ticket.issue("one", 1000);
    assertTrue(ticket.consume("one", 2000));
    assertFalse(ticket.consume("one", 2001));
  }

  @Test
  public void expiredCommandCannotExecuteBeforeWatchdogTicks() {
    VoiceUiTicket ticket = new VoiceUiTicket();
    ticket.issue("one", 1000);
    assertTrue(ticket.valid("one", 90999));
    assertFalse(ticket.consume("one", 91000));
  }

  @Test
  public void cancellationAndNewDialogInvalidateOldResults() {
    VoiceUiTicket ticket = new VoiceUiTicket();
    ticket.issue("one", 1000);
    ticket.clear();
    assertFalse(ticket.consume("one", 2000));
    ticket.issue("two", 3000);
    assertFalse(ticket.consume("one", 4000));
    assertTrue(ticket.consume("two", 4000));
  }
}
