package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class WakeCommandTest {
  @Test
  public void ignoresUnrelatedSpeechAndEmbeddedWakePhrase() {
    assertNull(VoiceCommand.afterWake("Please tell Hey Priority to open Gmail", "Hey Priority"));
    assertNull(VoiceCommand.afterWake("Hey Priorityman open Gmail", "Hey Priority"));
    assertNull(VoiceCommand.afterWake("open Gmail", "Hey Priority"));
    assertNull(VoiceCommand.afterWake("open Gmail", ""));
  }

  @Test
  public void supportsStandalonePhraseAndCombinedCommand() {
    assertEquals("", VoiceCommand.afterWake("Hey, Priority!", "Hey Priority"));
    assertEquals(
        "open whatsapp chat with anita",
        VoiceCommand.afterWake("HEY PRIORITY, Open WhatsApp chat with Anita!", "Hey Priority"));
    assertEquals(
        "open spotify", VoiceCommand.afterWake("Hello reader open Spotify", "Hello Reader"));
  }

  @Test
  public void phoneLinksRequireExplicitInternationalNumber() {
    assertEquals("919876543210", VoiceCommand.internationalPhone("+91 98765-43210"));
    assertNull(VoiceCommand.internationalPhone("Anita"));
    assertNull(VoiceCommand.internationalPhone("9876543210"));
    assertNull(VoiceCommand.internationalPhone("+91 123?text=bad"));
    assertNull(VoiceCommand.internationalPhone("+012345678"));
  }

  @Test
  public void openCommandDoesNotConsumeDictationOrPartialWords() {
    assertEquals("gmail", VoiceCommand.openTarget("Open Gmail"));
    assertNull(VoiceCommand.openTarget("Please open Gmail"));
    assertNull(VoiceCommand.openTarget("Opened Gmail"));
  }
}
