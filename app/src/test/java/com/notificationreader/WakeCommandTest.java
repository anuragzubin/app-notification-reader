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
  public void speechProviderAppSpellingsResolveToAllowedTargets() {
    assertEquals("gmail", VoiceCommand.openTarget("Open G mail"));
    assertEquals("gmail", VoiceCommand.openTarget("Open Google Mail"));
    assertEquals("whatsapp", VoiceCommand.openTarget("Open What's App"));
    assertEquals(
        "whatsapp chat with anita", VoiceCommand.openTarget("Open Whats App chat with Anita"));
    assertEquals("spotify", VoiceCommand.openTarget("Open Spot ify"));
  }

  @Test
  public void usesWakeMatchingAlternativeWithoutAcceptingAnUnwokenCommand() {
    assertEquals(
        "Hey Priority open Gmail",
        VoiceCommand.selectCommand(
            java.util.Arrays.asList("a priority open Gmail", "Hey Priority open Gmail"),
            "Hey Priority",
            true,
            false));
    String chosen =
        VoiceCommand.selectCommand(
            java.util.Arrays.asList("other speech", "open WhatsApp"), "Hey Priority", true, false);
    assertNull(VoiceCommand.afterWake(chosen, "Hey Priority"));
    assertNull(VoiceCommand.openTarget(chosen));
  }

  @Test
  public void alternativeCommandsAreAllowedWithinAcknowledgedWakeWindow() {
    assertEquals(
        "Open Gmail",
        VoiceCommand.selectCommand(
            java.util.Arrays.asList("opin Gmail", "Open Gmail"), "Hey Priority", true, true));
    assertEquals(
        "Open Spotify",
        VoiceCommand.selectCommand(
            java.util.Arrays.asList("opin Spotify", "Open Spotify"), "Hey Priority", false, false));
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
    assertEquals("gmail", VoiceCommand.openTarget("Please open Gmail"));
    assertNull(VoiceCommand.openTarget("Opened Gmail"));
  }
}
