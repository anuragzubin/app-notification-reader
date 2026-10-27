# Priority Voice for Android

[Download Priority Voice v1.8 APK](https://github.com/anuragzubin/app-notification-reader/raw/refs/heads/main/releases/PriorityVoice-v1.8.apk)

Install over the previous version to retain your saved senders and settings. The signed APK is stored in `releases/`, with its checksum in `releases/SHA256SUMS`. This is a personal-testing debug build.

An Android notification listener that plays the system notification tone and speaks the sender and message preview for your selected WhatsApp and email senders. Designed for a Galaxy A54 running Android 16; minimum Android 11. No account or paid AI service is needed.

## Install on your phone

1. Build or download `app-debug.apk`, transfer it to the phone, open it, and allow installation from that source if Android asks. This is a debug build for personal testing, not a Play Store release.
2. Open **Priority Voice**, tap **Connect notifications** on Home (or open Settings → Notification access), and grant Priority Voice access. Android can restrict this for sideloaded apps: if the switch is blocked, go to Settings → Apps → Priority Voice → the three-dot menu → **Allow restricted settings**, if your phone offers it. Return to notification access. Menu names vary with One UI.
3. Add a WhatsApp number with its country code (for example, `+91…`) or the **exact name WhatsApp displays**. Saved contacts commonly expose a name rather than a number. Add separate rules when both forms are needed.
4. Add your Gmail sender's email address. If Gmail only displays a name, optionally enter that exact name in **Gmail sender name fallback**. This is explicit opt-in: names are not unique and do not verify the email address. Without that fallback, a hidden address does not match.
5. Use each sender’s **Read sender name** and **Read message aloud** switches to select exactly what is spoken. Use **Try sound & voice** to check volume and the installed speech engine.
6. Send a *new* WhatsApp message and Gmail email from allowed senders while those apps are in the background. Confirm alerts, then send from unlisted senders and confirm silence from Priority Voice. Open chats may suppress notifications entirely.

Enable message previews in WhatsApp and Gmail's notification settings. Android restrictions, hidden content, notification grouping, disabled notifications, or Gmail's sender formatting can prevent a match. This app cannot access messages that those apps do not post as notifications. For delayed alerts, remove Priority Voice from Samsung's sleeping/deep sleeping apps under Battery → Background usage limits. Silent/vibrate modes and Do Not Disturb suppress priority alerts. The original apps may also play their own notification sounds; adjust their sounds if you want only this app's additional alerts, keeping their notifications enabled.

## Privacy and behavior

- Android notification access is broad. The listener only handles WhatsApp, WhatsApp Business, Gmail, Samsung Email, Outlook, FairEmail, Thunderbird, K-9, and the legacy Android Email package.
- Rules are stored in private app storage with backup disabled. From version 1.5, call-deferred message previews are saved in private app storage for missed playback (up to 100 entries, available for 24 hours). Expired entries are purged on the next access. A bounded duplicate history stores SHA-256 hashes and timestamps in private preferences, not notification text. It survives listener and app restarts.
- This app declares no Internet permission. Version 1.3 requests Phone state permission solely to defer readouts during calls. Speech text is sent to your installed Android text-to-speech engine, which has its own privacy/network behavior. Choose an offline voice in Android's text-to-speech settings if desired.
- Matching uses sender fields, never message-body mentions. Names match exactly, ignoring case and outside whitespace. Phone punctuation is ignored, but the country code must match. Email rules match whole addresses in sender headers.
- Group summaries are ignored. For WhatsApp MessagingStyle notifications, only the latest message's sender is checked, including group participant messages when that sender is available. Existing notifications at listener connection are seeded without alerting. Duplicate content identities are retained across restarts (up to 4,096 recent messages), with per-sender timestamp tracking to reject older updates. Rapid or bundled messages may not each produce a separate alert.
- Preview speech is capped at 400 characters. This version uses the default notification sound and default installed speech language/voice. No Gmail API, mailbox login, contacts permission, or phone-number identity lookup is used.

## Build and checks

Use Android Studio or JDK 17+ (full JDK, including `javac`), Android SDK platform 35, and build-tools 35.0.0. Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir=/path/to/sdk`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Unit tests cover sender-name exactness, phone formatting, and email boundary handling. Lint and compilation do not prove phone notification delivery. Complete the device checks above before relying on alerts. A release build needs your own signing configuration; do not publish the debug signing key.

## Cloud workspace

The prepared environment uses `/workspace/jdk`, `/workspace/android-sdk`, `/workspace/.android`, and `/workspace/.gradle`. Build here with:

```sh
JAVA_HOME=/workspace/jdk ANDROID_USER_HOME=/workspace/.android GRADLE_USER_HOME=/workspace/.gradle \
  ./gradlew -Duser.home=/workspace assembleDebug testDebugUnitTest lintDebug
```

No backend or credentials are required. Future cloud tasks should use the existing isolated checkout; no Git worktree is necessary.

## Version 1.1 — reaction speech control

Enable **Mute spoken WhatsApp reactions (keep sound)** to suppress both the spoken sender and preview for matched WhatsApp reaction notifications. Their sound remains enabled. The setting defaults off to preserve existing behavior, is saved across restarts, and applies to WhatsApp Business too. It does not change normal messages or email speech, including sender-only mode.

WhatsApp does not supply a documented reaction flag. Detection recognizes English notification text such as “Reacted ❤️ to your message” and “Reacted with 👍 to: …”. Other languages or changed WhatsApp wording may still speak; a regular message with exactly that reaction wording can also be classified as a reaction.

Install version 1.1 over version 1.0 to keep your sender list and settings. On your phone, enable this switch and compare a new reaction, a normal message, and an email from allowed senders. Reactions should sound without speech; the other two should still speak. Turn the switch off to restore reaction speech.

## Version 1.2 — stop repeated alerts and set each sender's alert modes

Each saved sender now has independent **Notification sound** and **Read sender and message aloud** switches. Both default on for existing and new entries. Turning either off does not affect the other; turning both off silences Priority Voice for that sender. These switches do not alter WhatsApp/Gmail's own notification sounds. Global enable, preview, reaction mute, and silent/DND settings still apply.

The listener no longer keys duplicate detection on Android notification IDs or expires it after five minutes. It uses persistent hashed message identities and sender timestamp tracking, selects the highest-timestamp WhatsApp message, and seeds already-active notifications silently on reconnect. Email reading prefers the current notification text over expanded text that can include older mail. Notification timestamps distinguish new emails with identical subjects; if the mail app omits timestamps and exposes identical text, those emails cannot be distinguished and a repeated preview may be suppressed. Changed or missing notification content from third-party apps remains a platform limitation.

Speech waiting to play is limited to the latest message per sender. A newer message from the same sender replaces their waiting speech and interrupts their older active speech; messages from other senders retain their place. Existing sender lists and settings are preserved on installation over 1.1.

Device regression check: leave Alice's message undismissed, receive Bob's, and verify only Bob's new message is read. Repeat after five minutes and after reconnecting notification access. Send two new identical WhatsApp messages (different message timestamps) and verify each can alert. Try sound-only, speech-only, both, and neither for separate WhatsApp and Gmail rules. Phone validation is still required; automated tests cover duplicate history and speech queue behavior rather than WhatsApp/Gmail delivery.

## Version 1.3 — independent name/message speech and call-aware readout

Each saved WhatsApp or email sender now has three switches: **Notification sound**, **Read sender name**, and **Read message aloud**. Name-only, message-only, both, or neither are supported. The previous global preview switch has been replaced by these per-sender controls. Existing v1.2 voice/preview preferences migrate into the two new switches; sender lists, sound preferences, and reaction muting remain intact.

After upgrading, open Priority Voice and tap **Allow Phone permission to pause during calls**. Grant Android's **Phone** permission (`READ_PHONE_STATE`). It is used only to monitor ringing/active/idle state, including registered SIM subscriptions; the app does not request call logs, record calls, or read/store phone numbers. Permission status appears at the top of the app. Without it, audio-mode monitoring provides only a fallback and cannot reliably detect every incoming call. Granting permission also refreshes the running listener.

When a call rings or becomes active, notification speech and the app's current notification tone stop. The interrupted notification is requeued and restarts from the beginning after the call ends; new notifications wait too. Only the latest waiting notification per sender is retained. Sound is not replayed after the call; deferred speech uses the current sender-name/message switches. Silent mode, DND, global disable, and reaction mute still apply. Dual-SIM callbacks are combined so an idle event from one SIM does not resume speech while the other remains busy. Audio-mode monitoring also detects many VoIP calls, but coverage depends on the calling app. Deferred messages are held in memory; force-stopping the app or Android killing its process can discard them.

Phone checks still needed: try all four name/message switch combinations, receive a call mid-readout, send messages during ringing and conversation, then hang up and verify the latest deferred readouts play. Test a rejected/missed call, an outgoing call, and both SIMs if used. Verify no speech during either overlapping call. Automated tests exercise pause/requeue, late TTS completions, per-sender replacement, and the four speech combinations; they do not simulate Samsung's actual telephony callbacks.

## Version 1.4 — a calmer interface

A new sage-and-cream palette, automatic system dark mode, rounded cards, comfortable spacing, and subtle page fades. The layout adapts to system bars, keyboard insets, and larger text. Page animation respects Android's animation-duration setting. Text colors were checked for readable contrast on the corresponding card backgrounds.

- **Home:** priority-alert master switch, connection status, sender count, and a sound/voice test.
- **Senders:** searchable sender cards with independent sound, sender-name, and message readout switches. Remove actions now have their own clearly labeled confirmation. The add-sender form uses labeled fields and shows email fallback options only for email rules.
- **Settings:** notification and Phone permissions, reaction muting, voice/language settings, privacy information, and Samsung setup tips.

Install over v1.3 to keep your existing rules, alert preferences, duplicate history, and permission grants. Notification matching, call-aware pauses, and speech processing are unchanged in this visual update. Compilation, the existing logic tests, and lint can be run in the cloud; actual light/dark rendering, keyboard behavior, large-font layout, and TalkBack navigation need checking on an Android device.

## Version 1.5 — play missed call-deferred notifications

The **Home → Play missed readouts** button plays saved notifications that were interrupted by a phone call or arrived during a call. The card shows the saved count. Clearing notifications from Android's notification shade does not clear these saved items. **Clear saved readouts** deletes the saved previews and cancels their pending playback.

The automatic post-call behavior still keeps the latest waiting notification per sender. The missed list separately retains **each notification event the listener captured**, so the button can play older deferred items from the same sender too. Already-successful readouts are removed; interrupted or failed readouts remain available. Your current per-sender name/message switches, reaction mute, global enable, silent mode, and DND still apply. Removed or voice-disabled senders are skipped. Pressing Play during a call schedules playback after the call rather than interrupting it.

Previews are stored locally, capped at 400 characters each, 100 items, and a 24-hour replay window; expired records are deleted on next access. App backup remains disabled. The saved list survives notification clearance and app/service restarts. This changes the earlier in-memory-only design: the UI discloses saved previews and provides deletion. No new permissions or network access are used.

This cannot recover notifications already cleared before version 1.5 was installed or notifications Android never delivered to the listener. Bundled WhatsApp notifications may expose only the latest message as a notification event; this is not a full chat or mailbox archive.

Device test: start a call, receive multiple messages from one allowed sender and another allowed sender, and clear Android notifications while still on the call. End the call and let the latest automatic readouts finish. Open Home and press Play missed readouts to hear remaining saved items. Confirm the count drops only after successful speech. Interrupt playback with another call and retry; clear the saved list and verify playback stops. Verify saved items remain after restarting the app and that per-sender voice switches still apply. Actual Android notification/telephony delivery requires phone validation.

## Version 1.6 — daily quiet hours

Open **Settings → Quiet hours**, choose a start time and stop time, and tap **Apply & enable**. The daily schedule remains saved and repeats every day until **Scheduled quiet hours** is switched off. Turning the switch off preserves the applied times; switching it back on reuses them. Picker edits are drafts until Apply is tapped. Equal start and stop times are rejected. The default suggested interval is 22:00–07:00, and the feature is off until enabled.

Start is inclusive, stop is exclusive. Overnight intervals work (for example, 22:00–07:00). The schedule uses the phone's current local clock and timezone, including daylight-saving changes. While the notification listener is running, it checks the boundary every second, and every attempt to play sound or speech checks the schedule again. No exact-alarm permission is required. Schedule settings survive app/service restarts; the listener must have notification access as before.

Quiet hours mute **Priority Voice's own notification sound and readouts**, including missed-readout playback and test voice. They cannot change WhatsApp/Gmail's own notification sounds or remove those apps' notifications. Those remain controlled by their Android notification settings.

At the quiet start, live speech and queued live readouts are canceled. Notifications arriving inside the quiet interval are tracked as seen but not queued, avoiding a burst of old messages at the stop time. Call-deferred previews already in saved missed history remain available for manual playback outside quiet hours. Call resumption and replay requests cannot override quiet hours. A scheduled stop time ends this daily quiet interval automatically; the schedule switch stays enabled for the next day.

Phone checks: apply a short daytime interval spanning the next few minutes, verify ongoing speech stops at its start, and send matching WhatsApp/Gmail alerts during the interval. Verify no app sound or readout, then verify a new alert works after the stop time. Repeat with an overnight interval, toggle the switch off/on, and restart the app to confirm saved times persist. Verify missed-readout and test buttons respect quiet hours. Unit tests cover interval boundaries and cancellation of queued speech; device timing still needs verification.

## Version 1.7 — all-contact master switch

**Alerts for all contacts** is available on Home and above the Senders list. Both views use the same saved master preference. Switching off immediately stops current app speech and sound, cancels queued/delayed live readouts and pending manual replay requests, and suppresses new Priority Voice alerts for every saved WhatsApp/email sender. Your individual sender controls, quiet-hour times, and saved missed-readout history remain intact. Switching on enables new alerts according to each sender's controls and quiet hours, without replaying notifications that arrived while off. The choice persists after restarting the app. It controls Priority Voice, not WhatsApp/Gmail's own notification settings.

Device check: switch off mid-readout, confirm speech and sound stop, receive matching messages from multiple senders while off, then switch on. Only new eligible notifications should sound/read; the earlier suppressed messages must not replay. Navigate between Home and Senders to confirm the same saved switch state, and restart the app to verify persistence.

## Version 1.8 — optional WhatsApp replies by voice

On **Senders**, enable **Reply by voice** for each desired WhatsApp sender. It defaults off for existing and new contacts and is not available for email rules. Other sender preferences are preserved when this switch changes. Android 13+ asks for notification permission so Priority Voice can show a silent reply prompt after an eligible readout finishes.

1. Receive a new **direct** WhatsApp / WhatsApp Business message from that sender and let its readout finish.
2. Tap Priority Voice's **Reply to [sender]** notification. Alternatively, open Senders and use **Reply to latest readout** on that contact's card.
3. Check the recipient, WhatsApp app, and source message preview. Tap **Dictate reply**. The phone's speech-recognition activity transcribes the reply; if unavailable, type it into the editable draft.
4. The app reads the draft back when its speech engine is ready and sound is allowed. Edit any transcription errors or use **Read draft aloud**.
5. Tap **Review & send**, then **Send** in the explicit confirmation dialog. Canceling, dictating, editing, or playing a draft never sends anything.

This sends **text transcribed from speech**, not a WhatsApp audio voice note. It uses WhatsApp's notification `RemoteInput` Reply action. A successful handoff is reported as “Reply handed to WhatsApp”; WhatsApp controls actual delivery. The app never launches the microphone automatically in the background or bypasses the lock screen. Dictation uses the installed recognition provider, which may process audio online despite the offline preference; Priority Voice itself still has no Internet permission and does not store recordings.

For recipient accuracy, replies require an explicit non-group notification flag, a semantic Reply action with free-form text, and a PendingIntent created by the originating WhatsApp package. Group chats and notifications with ambiguous group metadata are not supported. Changing/removing the WhatsApp notification invalidates its reply capability. Reply sessions expire after 15 minutes, are held only in memory, and do not survive the app process restarting. Old saved missed-readout previews do not recreate a cleared notification's Reply action. No post-readout reply prompt appears unless the original live notification still offers the required action.

Current contact opt-in, global enable, quiet hours, phone lock, and call state are checked before dictation and confirmation. Outgoing confirmation is foreground and requires an unlocked phone. Readouts pause while composing/dictating; they resume afterward according to existing sender and schedule rules. If a call begins, draft readback stops and active dictation is canceled where the recognition provider supports it. Final send is blocked during calls. Turning the per-contact switch off invalidates that contact's pending reply prompts. Android notification permission affects prompts; the in-app reply button remains usable after a qualifying readout when the prompt permission was denied.

Tests cover exact-draft confirmation, editing invalidation, empty drafts, duplicate send attempts, and confirmation after retry. No actual WhatsApp reply has been sent from the cloud environment. Phone validation is required: enable one contact, receive a direct message, let it finish, dictate a harmless test reply, edit it, cancel once, then explicitly confirm Send to a consenting test recipient. Verify disabled contacts and groups do not offer replies, changes/clearance expire actions, and calls/quiet hours prevent recording or sending. Verify separate sound/name/message switches still work after changing reply opt-in.
