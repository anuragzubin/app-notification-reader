package com.notificationreader;

/** Stable IDs retain existing opening preferences across updates. */
final class AppCatalog {
  static final class App {
    final String id, label, defaultPackage;

    App(String id, String label, String defaultPackage) {
      this.id = id;
      this.label = label;
      this.defaultPackage = defaultPackage;
    }
  }

  static final App[] ALL = {
    new App("whatsapp", "WhatsApp", "com.whatsapp"),
    new App("gmail", "Gmail", "com.google.android.gm"),
    new App("spotify", "Spotify", "com.spotify.music"),
    new App("traya", "Traya", ""),
    new App("deepstash", "Deepstash", "com.deepstash"),
    new App("duolingo", "Duolingo", "com.duolingo")
  };

  static App find(String id) {
    for (App app : ALL) if (app.id.equals(id)) return app;
    return null;
  }

  static final String SPOKEN_LIST = "WhatsApp, Gmail, Spotify, Traya, Deepstash, or Duolingo";
}
