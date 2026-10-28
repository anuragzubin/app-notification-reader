package com.notificationreader;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class AppCatalogTest {
  @Test
  public void containsExactlyTheRequestedAppsWithStablePreferenceIds() {
    Set<String> ids = new HashSet<>();
    for (AppCatalog.App app : AppCatalog.ALL) ids.add(app.id);
    assertEquals(
        new HashSet<>(
            Arrays.asList("whatsapp", "gmail", "spotify", "traya", "deepstash", "duolingo")),
        ids);
    assertEquals("com.whatsapp", AppCatalog.find("whatsapp").defaultPackage);
    assertNull(AppCatalog.find("browser"));
  }

  @Test
  public void speechVariantsForNewAppsResolveThroughAllowlist() {
    assertNotNull(AppCatalog.find(VoiceCommand.openTarget("Open Deep stash")));
    assertNotNull(AppCatalog.find(VoiceCommand.openTarget("Open Duo lingo")));
    assertNotNull(AppCatalog.find(VoiceCommand.openTarget("Open Traya")));
  }
}
