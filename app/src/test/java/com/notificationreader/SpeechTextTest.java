package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class SpeechTextTest {
  @Test
  public void senderOnlyDoesNotLeakMessage() {
    assertEquals(
        "WhatsApp message from Alice",
        SpeechText.compose(true, false, "WhatsApp message", "Alice", "Secret"));
  }

  @Test
  public void messageOnlyDoesNotReadSender() {
    assertEquals("Hello", SpeechText.compose(false, true, "Email", "Alice", "Hello"));
  }

  @Test
  public void bothControlsOnReadsBoth() {
    assertEquals(
        "Email from Alice. Hello", SpeechText.compose(true, true, "Email", "Alice", "Hello"));
  }

  @Test
  public void bothControlsOffIsSilent() {
    assertEquals("", SpeechText.compose(false, false, "Email", "Alice", "Hello"));
  }
}
