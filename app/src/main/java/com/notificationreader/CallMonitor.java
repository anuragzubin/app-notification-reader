package com.notificationreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.*;
import java.util.function.Consumer;

/** Monitors both SIMs; audio mode also covers many VoIP calls (best effort). */
final class CallMonitor {
  private final Context context;
  private final Handler handler;
  private final Consumer<Boolean> changed;
  private final Map<TelephonyManager, PhoneStateListener> listeners = new HashMap<>();
  private final Map<TelephonyManager, Integer> states = new HashMap<>();
  private boolean blocked;
  private final Runnable poll =
      new Runnable() {
        @Override
        public void run() {
          update();
          handler.postDelayed(this, 500);
        }
      };

  CallMonitor(Context context, Handler handler, Consumer<Boolean> changed) {
    this.context = context;
    this.handler = handler;
    this.changed = changed;
  }

  @SuppressLint("MissingPermission") // Explicit runtime permission check plus revocation handling.
  void start() {
    close();
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
        == PackageManager.PERMISSION_GRANTED) {
      TelephonyManager base = context.getSystemService(TelephonyManager.class);
      if (base != null) {
        List<TelephonyManager> managers = new ArrayList<>();
        try {
          SubscriptionManager subscriptions = context.getSystemService(SubscriptionManager.class);
          List<SubscriptionInfo> sims =
              subscriptions == null ? null : subscriptions.getActiveSubscriptionInfoList();
          if (sims != null)
            for (SubscriptionInfo sim : sims)
              managers.add(base.createForSubscriptionId(sim.getSubscriptionId()));
        } catch (SecurityException ignored) {
        }
        if (managers.isEmpty()) managers.add(base);
        for (TelephonyManager manager : managers) {
          PhoneStateListener listener =
              new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String unusedNumber) {
                  states.put(manager, state);
                  update();
                }
              };
          try {
            states.put(manager, manager.getCallState());
            manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
            listeners.put(manager, listener);
          } catch (SecurityException | UnsupportedOperationException ignored) {
          }
        }
      }
    }
    poll.run();
  }

  void update() {
    AudioManager audio = context.getSystemService(AudioManager.class);
    int mode = audio == null ? AudioManager.MODE_NORMAL : audio.getMode();
    boolean now =
        mode == AudioManager.MODE_RINGTONE
            || mode == AudioManager.MODE_IN_CALL
            || mode == AudioManager.MODE_IN_COMMUNICATION;
    for (int state : states.values()) now |= state != TelephonyManager.CALL_STATE_IDLE;
    if (now != blocked) {
      blocked = now;
      changed.accept(now);
    }
  }

  boolean isBlocked() {
    update();
    return blocked;
  }

  void close() {
    handler.removeCallbacks(poll);
    for (Map.Entry<TelephonyManager, PhoneStateListener> e : listeners.entrySet()) {
      try {
        e.getKey().listen(e.getValue(), PhoneStateListener.LISTEN_NONE);
      } catch (SecurityException ignored) {
      }
    }
    listeners.clear();
    states.clear();
  }
}
