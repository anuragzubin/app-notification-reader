package com.notificationreader;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class AppSpeechTest {
  private final Map<String, String> none = Collections.emptyMap();

  @Test
  public void recognizesAllThreeNewNamesAndProviderSpellings() {
    assertEquals("deepstash", AppSpeech.target("Open Deepstash", none));
    assertEquals("deepstash", AppSpeech.target("Open Deep Stash", none));
    assertEquals("duolingo", AppSpeech.target("Open Duolingo", none));
    assertEquals("duolingo", AppSpeech.target("Open Dual Lingo", none));
    assertEquals("traya", AppSpeech.target("Open Traya", none));
    assertEquals("traya", AppSpeech.target("Open Try A", none));
  }

  @Test
  public void acceptsWholePoliteRequestsAndJoinedCommandWords() {
    assertEquals("deepstash", AppSpeech.target("Please open the Deepstash app", none));
    assertEquals("duolingo", AppSpeech.target("Can you open Duolingo please", none));
    assertEquals("traya", AppSpeech.target("Launch Traya", none));
    assertEquals("duolingo", AppSpeech.target("openduolingo", none));
    assertNull(AppSpeech.target("Anita said open Duolingo", none));
    assertNull(AppSpeech.target("Opened Duolingo", none));
  }

  @Test
  public void validAlternativeOutranksAnUnrecognizedWakeCommand() {
    assertEquals(
        "Hey Priority open Deep stash",
        VoiceCommand.selectCommand(
            Arrays.asList("Hey Priority open deeps dash", "Hey Priority open Deep stash"),
            "Hey Priority",
            true,
            false));
    assertEquals(
        "Open Duolingo",
        VoiceCommand.selectCommand(
            Arrays.asList("Open do a ling go", "Open Duolingo"), "Hey Priority", false, true));
  }

  @Test
  public void cannotBypassRequiredWakeWithBetterAlternative() {
    String selected =
        VoiceCommand.selectCommand(
            Arrays.asList("Hey Priority open unknown", "Open Duolingo"),
            "Hey Priority",
            true,
            false);
    assertEquals("Hey Priority open unknown", selected);
  }

  @Test
  public void customAliasWorksButAmbiguousAliasCannotSelectAnApp() {
    Map<String, String> aliases = new HashMap<>();
    aliases.put("deepstash", "Reading");
    assertEquals("deepstash", AppSpeech.target("Open Reading", aliases));
    assertEquals("deepstash", AppSpeech.target("Open Deepstash", aliases));
    aliases.put("duolingo", "Reading");
    assertNull(AppSpeech.target("Open Reading", aliases));
    aliases.clear();
    aliases.put("traya", "gmail");
    assertNull(AppSpeech.target("Open Gmail", aliases));
  }

  @Test
  public void doesNotGuessOtherAppsOrChangeChatRecipients() {
    assertNull(AppSpeech.target("Open browser", none));
    assertEquals(
        "whatsapp chat with try a", AppSpeech.target("Open WhatsApp chat with Try A", none));
    assertEquals("try a", VoiceCommand.recipient("Send text to Try A"));
    assertEquals("open try a", VoiceCommand.normalize("Open Try A"));
  }
}
