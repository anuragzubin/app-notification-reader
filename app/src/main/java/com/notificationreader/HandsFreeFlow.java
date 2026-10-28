package com.notificationreader;

final class HandsFreeFlow {
  enum Stage {
    COMMAND,
    OFFER,
    MESSAGE,
    CONFIRM
  }

  Stage stage = Stage.COMMAND;
  String token, name, message;
  private ReplyDraft draft = new ReplyDraft();

  void begin(String token, String name) {
    reset();
    this.token = token;
    this.name = name;
    stage = Stage.MESSAGE;
  }

  void offer(String token, String name) {
    reset();
    this.token = token;
    this.name = name;
    stage = Stage.OFFER;
  }

  boolean acceptOffer(String answer) {
    if (stage != Stage.OFFER || !VoiceCommand.yes(answer)) return false;
    stage = Stage.MESSAGE;
    return true;
  }

  boolean rerecord() {
    if (stage != Stage.CONFIRM) return false;
    message = null;
    draft = new ReplyDraft();
    stage = Stage.MESSAGE;
    return true;
  }

  boolean dictate(String words) {
    if (stage != Stage.MESSAGE || words.trim().isEmpty() || words.length() > 2000) return false;
    message = words.trim();
    draft.edit(message);
    stage = Stage.CONFIRM;
    return true;
  }

  boolean confirm(String command) {
    if (stage != Stage.CONFIRM || !(VoiceCommand.yes(command) || VoiceCommand.send(command)))
      return false;
    if (!draft.confirm(message) || !draft.beginSend()) return false;
    stage = Stage.COMMAND;
    return true;
  }

  void reset() {
    stage = Stage.COMMAND;
    token = null;
    name = null;
    message = null;
    draft = new ReplyDraft();
  }
}
