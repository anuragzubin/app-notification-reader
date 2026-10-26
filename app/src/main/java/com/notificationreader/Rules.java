package com.notificationreader;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class Rules {
  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("rules", Context.MODE_PRIVATE);
  }

  static final class Rule {
    final String type, value, label, alias;
    final boolean sound, speech, readSender, readMessage;

    Rule(String t, String v, String l, String a) {
      this(t, v, l, a, true, true);
    }

    Rule(String t, String v, String l, String a, boolean sound, boolean speech) {
      this(t, v, l, a, sound, speech, speech);
    }

    Rule(
        String t,
        String v,
        String l,
        String a,
        boolean sound,
        boolean readSender,
        boolean readMessage) {
      this.sound = sound;
      this.readSender = readSender;
      this.readMessage = readMessage;
      this.speech = readSender || readMessage;
      type = t;
      value = v;
      label = l;
      alias = a;
    }
  }

  static List<Rule> load(Context c) {
    List<Rule> result = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        result.add(
            new Rule(
                o.getString("type"),
                o.getString("value"),
                o.getString("label"),
                o.optString("alias", ""),
                o.optBoolean("sound", true),
                o.optBoolean("read_sender", o.optBoolean("speech", true)),
                o.optBoolean(
                    "read_message",
                    o.optBoolean("speech", true) && prefs(c).getBoolean("preview", true))));
      }
    } catch (org.json.JSONException ignored) {
    }
    return result;
  }

  static void save(Context c, List<Rule> rules) {
    JSONArray a = new JSONArray();
    try {
      for (Rule r : rules) {
        JSONObject o = new JSONObject();
        o.put("type", r.type);
        o.put("value", r.value);
        o.put("label", r.label);
        o.put("alias", r.alias);
        o.put("sound", r.sound);
        o.put("speech", r.speech);
        o.put("read_sender", r.readSender);
        o.put("read_message", r.readMessage);
        a.put(o);
      }
    } catch (org.json.JSONException e) {
      throw new IllegalStateException(e);
    }
    prefs(c).edit().putString("list", a.toString()).apply();
  }

  static String normalize(String s) {
    return SenderMatcher.normalize(s);
  }

  static boolean matches(String rule, String sender) {
    return SenderMatcher.matches(rule, sender);
  }
}
