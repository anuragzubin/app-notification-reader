package com.notificationreader;

import android.content.Context;
import android.os.SystemClock;

final class AudioSessions {
  private static final AudioLeaseState state = new AudioLeaseState();

  static synchronized void hold(Context c, String owner) {
    state.hold(owner, SystemClock.elapsedRealtime());
    signal(c);
  }

  static synchronized void release(Context c, String owner) {
    state.release(owner);
    signal(c);
  }

  static synchronized boolean busy() {
    return state.busy(SystemClock.elapsedRealtime());
  }

  static synchronized boolean busyExcept(String owner) {
    return state.busyExcept(owner, SystemClock.elapsedRealtime());
  }

  static synchronized void clear(Context c) {
    state.clear();
    signal(c);
  }

  private static void signal(Context c) {
    Rules.prefs(c).edit().putLong("audio_session_revision", System.nanoTime()).apply();
  }
}
