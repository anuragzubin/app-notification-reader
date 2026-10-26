package com.notificationreader;

import android.content.Context;
import java.util.List;
import org.json.*;

final class MissedStore {
  static final String KEY = "missed_readouts";

  static synchronized List<MissedReadouts.Entry> load(Context context) {
    MissedReadouts history = read(context);
    save(context, history); // Purge expired previews from private storage on access.
    return history.recent(System.currentTimeMillis());
  }

  static synchronized void add(Context context, MissedReadouts.Entry entry) {
    MissedReadouts history = read(context);
    history.add(entry, System.currentTimeMillis());
    save(context, history);
  }

  static synchronized void remove(Context context, String id) {
    MissedReadouts history = read(context);
    history.remove(id);
    save(context, history);
  }

  static synchronized void clear(Context context) {
    Rules.prefs(context).edit().putString(KEY, "[]").apply();
  }

  private static MissedReadouts read(Context context) {
    MissedReadouts history = new MissedReadouts();
    try {
      JSONArray list = new JSONArray(Rules.prefs(context).getString(KEY, "[]"));
      for (int i = 0; i < list.length(); i++) {
        JSONObject e = list.getJSONObject(i);
        history.add(
            new MissedReadouts.Entry(
                e.getString("id"),
                e.getString("stream"),
                e.getString("type"),
                e.getString("value"),
                e.getString("body"),
                e.getLong("time"),
                e.optBoolean("reaction", false)),
            System.currentTimeMillis());
      }
    } catch (JSONException ignored) {
    }
    return history;
  }

  private static void save(Context context, MissedReadouts history) {
    JSONArray list = new JSONArray();
    try {
      for (MissedReadouts.Entry e : history.recent(System.currentTimeMillis())) {
        JSONObject item = new JSONObject();
        item.put("id", e.id);
        item.put("stream", e.stream);
        item.put("type", e.type);
        item.put("value", e.value);
        item.put("body", e.body);
        item.put("time", e.time);
        item.put("reaction", e.reaction);
        list.put(item);
      }
    } catch (JSONException ex) {
      throw new IllegalStateException(ex);
    }
    Rules.prefs(context).edit().putString(KEY, list.toString()).apply();
  }
}
