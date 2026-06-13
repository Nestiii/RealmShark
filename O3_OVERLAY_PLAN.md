# O3 Phase Overlay — Corrected Build Plan

> Fork of RealmShark/Tomato | Windows runtime | Mac development | Java | Personal use
>
> **This is a corrected revision of the original "O3 Overlay Build Plan".** It was
> rewritten after verifying every assumption against the actual `tomato` branch
> source. Sections that the original got wrong are flagged with **⚠ CORRECTION**.
> The taunt-string data and overall architecture from the original are sound and
> are carried over; the *code APIs, build flow, Java level, and integration points*
> were fictional and have been replaced with what the repo actually exposes.

---

## 0. What changed vs. the original plan (read this first)

| Area | Original plan said | Reality in this repo | Fix |
|------|--------------------|----------------------|-----|
| Chat packet class | `ChatPacket` | `packets.incoming.TextPacket` | Use `TextPacket` |
| Reading fields | `packet.getName()`, `getText()`, `getNumStars()` | **public fields**: `p.name`, `p.text`, `p.numStars`, `p.recipient` | Use the fields directly |
| Event hook | `RealmShark.addChatListener(packet -> …)` | **No such API.** Central handler is `TomatoData.text(TextPacket p)` → `ChatGUI.updateChat(p)` | Hook inside `ChatGUI.updateChat()` |
| `PhaseAlert` type | Java `record` | Project is **`sourceCompatibility = 1.8`** (Java 8). Records are Java 16+ → **won't compile** | Plain immutable class (same accessor names) |
| Package | `tomato.logic` | No `logic` package exists. Real packages: `tomato.gui.*`, `tomato.backend.*`, `tomato.realmshark.*` | Put both classes in new `tomato.gui.overlay` |
| Debug menu item | Inline `JMenuItem` + lambda | `TomatoMenuBar implements ActionListener`, dispatches via `e.getSource() == item` chains | Match the existing field+dispatch idiom |
| Branch / build | Build `RealmShark.jar` then assemble Tomato in CI from two branches | `tomato` is a normal project that depends on a **prebuilt** `libs/RealmShark-vX.Y.Z.jar`, and `/libs` is **gitignored** | CI must build the backend jar and drop it in `libs/` |
| Backend version | unspecified | `tomato` pins `libs/RealmShark-v1.2.2.jar`; latest `realmshark` is **v1.2.3** → **mismatch** | **Use the latest realmshark (v1.2.3)** and re-pin the dependency (see §3) |

**Working branch:** `overlay` (created off `tomato`). This file lives on that branch.

---

## 1. Architecture (corrected)

```
[ROTMG Game on Windows]
    ↓ network packets
[Npcap] → packet capture (tomato.backend.TomatoPacketCapture)
    parses raw packets → typed Java packet objects (packets.incoming.*)
    ↓
[tomato.backend.data.TomatoData.text(TextPacket p)]   ← central chat entry point
    ↓ calls
[tomato.gui.chat.ChatGUI.updateChat(TextPacket p)]    ← existing sound-ping logic lives HERE
    ↓ ← YOUR HOOK goes right here, next to the Sound.*.play() calls
[O3PhaseDetector.detect(p.text)]   →   [O3PhaseOverlay.getInstance().showPhase(alert)]
  string-contains match on taunt        transparent always-on-top JWindow
  returns PhaseAlert (or null)          shows label + tip, auto-hides
```

**Sniffer lifecycle:** unchanged from the original — the overlay is passive. `updateChat`
only runs while packets flow, i.e. while the sniffer is running (File → Start Sniffer).
No extra toggle is required; an "Enable Overlay" checkbox is a stretch goal (§12).

---

## 2. TextPacket — the real fields

`packets.incoming.TextPacket` (from the backend jar) exposes **public fields**, not getters:

```java
public String name;        // sender. Oryx: "Oryx the Mad God" / "Oryx the Exalted God".
                           //   For players: their character name (may be "name,guild,...").
public int    objectId;    // sender object id
public short  numStars;    // sender stars; NPC/server messages are typically 0 / -1
public int    bubbleTime;
public String recipient;   // "*Guild*", "*Party*", a player name for PMs, or "" for local/say
public String text;        // the message body  ← match taunts against THIS
public String cleanText;
public boolean isSupporter;
public int    starBackground;
```

> ⚠ **CORRECTION:** the original plan called `getName()/getText()/getNumStars()`.
> Those methods do not exist. Use `p.name`, `p.text`, `p.numStars` directly.

**Note on `name` formatting:** `ChatGUI.updateChat` already splits player names with
`p.name.split(",")[0]`. Oryx/NPC names generally have no comma, so an exact/`contains`
match on `p.name` is fine. **Verify the exact Oryx sender string in-game before trusting
the sender filter** (see §5).

---

## 3. Dev/Test Cycle & CI (corrected)

You still can't run the sniffer on Mac (Npcap is Windows-only). Workflow is unchanged:
write on Mac → build a jar → run on Windows.

### Build tasks (verified)

Both branches use the `application` + `com.github.johnrengelman.shadow` v7.0.0 plugins.
The fat jar task is **`shadowJar`** and the output name is driven by the project version:

- `tomato` branch → `build/libs/Tomato-v1.9.2.jar` (mainClass `tomato.Tomato`)
- `realmshark` branch → `build/libs/RealmShark-v1.2.3.jar` (mainClass `realmshark.RealmShark`)

### ⚠ CORRECTION — use the latest realmshark (v1.2.3) and re-pin the dependency

`tomato/build.gradle` declares:

```gradle
implementation files("libs/RealmShark-v1.2.2.jar")
```

and `.gitignore` contains `/libs`. So:

1. The backend jar is **not in the repo** — every build (local or CI) must place it.
2. The filename is **pinned** to an old `v1.2.2`, but the latest `realmshark` branch is
   **v1.2.3**, so building the current backend produces `RealmShark-v1.2.3.jar`, which the
   old dependency line will **not** find → compile failure.

**Decision: build against the latest realmshark (v1.2.3) and re-pin the dependency.**
This is one small commit on the `overlay` branch — change the line in `tomato/build.gradle`:

```gradle
implementation files("libs/RealmShark-v1.2.3.jar")
```

Then every build (local + CI) copies the freshly built backend jar straight into `libs/`
with its natural name — no rename hack. If realmshark bumps again later, rebuild the
backend and re-pin to the new version in the same one-line change.

### GitHub Actions (`.github/workflows/build.yml`)

> ⚠ **CORRECTION:** this no longer does `git checkout tomato` mid-job. Two branches are
> built in two separate checkouts so each has its own clean tree. Two further gotchas found
> when the workflow first ran (and fixed below):
> - **The repo gitignores the whole `gradle/` directory**, so `gradle/wrapper/gradle-wrapper.jar`
>   is **not committed** and `./gradlew` fails on a clean CI checkout with
>   `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`. Fix: install Gradle via
>   `gradle/actions/setup-gradle` and call `gradle` directly instead of `./gradlew`.
> - **shadow plugin 7.0.0 requires Gradle 7.x** (Gradle 8 breaks it), so the Gradle version
>   is pinned to `7.6.4`.
>
> `java-version: '17'` is fine even though the code targets Java 8 —
> `targetCompatibility = 1.8` makes javac emit Java-8 bytecode.
>
> **Status: this exact workflow is committed and passing** (build ~58s, uploads `Tomato-jar`).

```yaml
name: Build Tomato

on:
  push:
    branches: [ overlay ]      # build our working branch
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      # gradle/ is gitignored → no committed wrapper jar. Install Gradle and use it directly.
      # shadow 7.0.0 needs Gradle 7.x.
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '7.6.4'

      # --- Build the RealmShark backend jar from the realmshark branch (latest, v1.2.3) ---
      - name: Checkout realmshark backend
        uses: actions/checkout@v4
        with:
          ref: realmshark
          path: backend
      - name: Build backend shadowJar
        working-directory: backend
        run: |
          gradle shadowJar --no-daemon
          mkdir -p "$GITHUB_WORKSPACE/jarout"
          cp build/libs/RealmShark-*.jar "$GITHUB_WORKSPACE/jarout/"

      # --- Build Tomato (overlay branch) against that jar ---
      - name: Checkout overlay
        uses: actions/checkout@v4
        with:
          ref: overlay
          path: tomato
      - name: Stage latest backend jar into libs/
        working-directory: tomato
        run: |
          mkdir -p libs
          # Copy the freshly built v1.2.3 backend; build.gradle is re-pinned to match (see §3)
          cp "$GITHUB_WORKSPACE"/jarout/RealmShark-*.jar libs/
      - name: Build Tomato shadowJar
        working-directory: tomato
        run: |
          gradle shadowJar --no-daemon

      - name: Upload Tomato jar
        uses: actions/upload-artifact@v4
        with:
          name: Tomato-jar
          path: tomato/build/libs/Tomato-*.jar
          retention-days: 7
```

> The one-time prerequisite for this to compile is the re-pin commit (`v1.2.2` → `v1.2.3`
> in `tomato/build.gradle`) described above.

### Local build on Mac (manual fallback)

```bash
# 1) backend jar (from realmshark branch — latest, v1.2.3 — separate clone or worktree)
./gradlew shadowJar                 # → build/libs/RealmShark-v1.2.3.jar
# 2) place it for tomato/overlay (natural name; build.gradle is pinned to v1.2.3)
mkdir -p libs && cp ../realmshark/build/libs/RealmShark-v1.2.3.jar libs/
# 3) build Tomato
./gradlew shadowJar                 # → build/libs/Tomato-v1.9.2.jar
```

---

## 4. New files to create (corrected paths)

> ⚠ **CORRECTION:** `tomato.logic` does not exist. Put both classes in a new
> self-contained package `tomato.gui.overlay` (the detector is small and overlay-specific;
> keeping them together avoids cross-package wiring). Alternative: detector in
> `tomato.backend`, overlay in `tomato.gui` — but a single new package is simplest.

```
src/main/java/tomato/gui/overlay/
    O3PhaseDetector.java   ← taunt matching, returns PhaseAlert (Java-8 safe)
    O3PhaseOverlay.java    ← transparent always-on-top JWindow
```

---

## 5. Chat filtering — system vs player (corrected)

Match the message body against the taunt map; gate on sender to avoid players spoofing
alerts in chat. Mirror what `ChatGUI.updateChat` already reads.

```java
String sender = p.name == null ? "" : p.name;
boolean isOryx = sender.contains("Oryx the Mad God")
              || sender.contains("Oryx the Exalted God");
// Server/realm announcements often have an empty or "#"-prefixed sender; CONFIRM in-game.
boolean isRealmEvent = sender.isEmpty() || sender.startsWith("#");
```

> **First in-game test:** before trusting the sender filter, enable
> **Overlay → Log Chat Senders (debug)** (implemented — see §9). It prints every incoming
> `[O3Overlay] name='…' text='…'` to stdout. Run it for a few minutes in the Realm and during
> an O3 run, confirm the exact Oryx sender strings and realm-event sender format, then tighten
> the filter in `ChatGUI.o3Overlay` if needed.

---

## 6. O3PhaseDetector.java (Java-8 safe)

> ⚠ **CORRECTION:** `record` replaced with a plain immutable class. Accessor method
> names (`label()`, `color()`, `displayMs()`, `danger()`, `tip()`) are kept identical, so
> `O3PhaseOverlay` needs no changes regardless of which form `PhaseAlert` takes.
>
> The full `TAUNT_MAP` taunt strings from the original plan are correct and should be
> carried over verbatim (HP transitions, special events, per-attack Exalted/base pairs,
> and the realm-event test entries). Only the type declarations changed.

```java
package tomato.gui.overlay;

import java.util.LinkedHashMap;
import java.util.Map;

public class O3PhaseDetector {

    public enum Danger { LOW, MEDIUM, HIGH, CRITICAL, OPPORTUNITY }

    /** Java-8 replacement for the original `record PhaseAlert(...)`. Same accessor names. */
    public static final class PhaseAlert {
        private final String label;
        private final String color;
        private final int displayMs;
        private final Danger danger;
        private final String tip;

        public PhaseAlert(String label, String color, int displayMs, Danger danger, String tip) {
            this.label = label;
            this.color = color;
            this.displayMs = displayMs;
            this.danger = danger;
            this.tip = tip;
        }

        public String label()   { return label; }
        public String color()   { return color; }
        public int    displayMs(){ return displayMs; }
        public Danger danger()  { return danger; }
        public String tip()     { return tip; }
    }

    // Order matters — Exalted (longer) strings must be inserted before base strings.
    private static final Map<String, PhaseAlert> TAUNT_MAP = new LinkedHashMap<>();

    static {
        // ── Carry over the FULL taunt map from the original plan, e.g.: ──
        TAUNT_MAP.put("My dominance extends throughout the universe",
            new PhaseAlert("PORTALS SPAWNED — Pet Stasis missiles added", "#AA88FF", 4000, Danger.MEDIUM,
                "Portals orbit Oryx — watch for inward/outward stasis shots"));
        // … (all HP transitions, special events, and Exalted-before-base per-attack pairs) …

        // ── Realm-event tests (fire without reaching O3) ──
        TAUNT_MAP.put("Cube God has appeared",
            new PhaseAlert("CUBE GOD spawned", "#8888FF", 3000, Danger.LOW, "[test event]"));
        TAUNT_MAP.put("A Skull Shrine has risen",
            new PhaseAlert("SKULL SHRINE spawned", "#FF8888", 3000, Danger.LOW, "[test event]"));
        TAUNT_MAP.put("Oryx has been summoned",
            new PhaseAlert("ORYX SUMMONED", "#FFD700", 3000, Danger.MEDIUM, "[test event]"));
    }

    public static PhaseAlert detect(String chatText) {
        if (chatText == null || chatText.trim().isEmpty()) return null;  // Java-8: no isBlank()
        for (Map.Entry<String, PhaseAlert> entry : TAUNT_MAP.entrySet()) {
            if (chatText.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }
}
```

> ⚠ **CORRECTION:** `String.isBlank()` is Java 11+. Use `trim().isEmpty()` on Java 8.

---

## 7. O3PhaseOverlay.java (unchanged in spirit, repackaged)

The original overlay code is fine on Java 8 (no records/`var`/`isBlank`). Only the
`package` line changes to `tomato.gui.overlay`, and the `import` of the detector becomes:

```java
package tomato.gui.overlay;

import tomato.gui.overlay.O3PhaseDetector.PhaseAlert;
// … rest of the original O3PhaseOverlay.java body is unchanged …
```

> Caveat worth checking on Windows: per-pixel transparency (`setBackground(new Color(0,0,0,0))`
> on a `JWindow`) requires the platform to support `PERPIXEL_TRANSLUCENT`. It works on
> typical Windows desktops; if it throws, fall back to a solid background or
> `setOpacity(...)`. Test on the actual Windows runtime.

---

## 8. Hook into Tomato (corrected — the important part)

> ⚠ **CORRECTION:** there is no `RealmShark.addChatListener`. The real, single place every
> chat message passes through is `ChatGUI.updateChat(TextPacket p)`
> (`src/main/java/tomato/gui/chat/ChatGUI.java`), which is also where the existing
> `Sound.*.play()` pings fire. Add the overlay call there.

Inside `ChatGUI.updateChat(TextPacket p)`, alongside the existing ping logic:

```java
// O3 phase overlay — passive, mirrors the sound-ping pattern.
{
    String sender = p.name == null ? "" : p.name;
    boolean isOryx = sender.contains("Oryx the Mad God")
                  || sender.contains("Oryx the Exalted God");
    boolean isRealmEvent = sender.isEmpty() || sender.startsWith("#"); // CONFIRM in-game
    if (isOryx || isRealmEvent) {
        O3PhaseDetector.PhaseAlert alert = O3PhaseDetector.detect(p.text);
        if (alert != null) {
            tomato.gui.overlay.O3PhaseOverlay.getInstance().showPhase(alert);
        }
    }
}
```

Add the import at the top of `ChatGUI.java`:
`import tomato.gui.overlay.O3PhaseDetector;`

> Alternative hook point: `TomatoData.text(TextPacket p)` (one level up, in
> `tomato.backend.data`). `updateChat` is preferred because it's already the chat/ping
> hub and keeps overlay logic next to the analogous sound pings.

---

## 9. Overlay menu (matches TomatoMenuBar idiom)

> ⚠ **CORRECTION:** `TomatoMenuBar implements ActionListener` and dispatches in
> `actionPerformed(ActionEvent e)` via `e.getSource() == item` chains — it does **not**
> use per-item lambdas. Follow that pattern.

**As implemented**, a dedicated top-level **Overlay** menu holds three items:
`Enable O3 Overlay` (checkbox, persisted `o3OverlayEnabled`, default ON), `Log Chat Senders
(debug)` (checkbox, persisted `o3LogChatSenders`), and `Test O3 Overlay`. The enable/log
checkboxes follow the same persist-and-set-static-flag pattern as the existing sound-ping
checkboxes (see `setOverlayCheckbox()`), driving `O3PhaseOverlay.enabled` /
`O3PhaseOverlay.logSenders`.

The test item specifically:

1. Declare a field with the other `JMenuItem`s (top of class):

```java
private JMenuItem testO3Overlay;
```

2. In the constructor, create it, register the listener, and add it to the Overlay menu:

```java
testO3Overlay = new JMenuItem("Test O3 Overlay");
testO3Overlay.addActionListener(this);
overlayMenu.add(testO3Overlay);
```

3. In `actionPerformed`, add a branch in the `else if` chain:

```java
} else if (e.getSource() == testO3Overlay) {
    final String[] tests = {
        "FALL BEFORE MY CELESTIAL STRENGTH!",
        "NO! This cannot be…",
        "Accept your fate!",
        "The ground quakes from my splendor!"
    };
    int[] delays = {0, 2500, 5000, 7500};
    for (int i = 0; i < tests.length; i++) {
        final String t = tests[i];
        javax.swing.Timer timer = new javax.swing.Timer(delays[i],
            ev -> tomato.gui.overlay.O3PhaseOverlay.debugTest(t));
        timer.setRepeats(false);
        timer.start();
    }
}
```

`O3PhaseOverlay.debugTest(String)` (from the original plan) stays as-is — it runs the
string through `O3PhaseDetector.detect` and shows the overlay if matched.

---

## 10. Test without an O3 run — realm events

Realm events come through the **same** `TextPacket` path as O3 taunts. **Confirmed from a live
capture:** they are NOT readable text — the `text` field is a localization key, and the sender
is `#Oryx the Mad God`:

```
{"k":"stringlist.Grand_Sphinx.new.0"}                          ← Grand Sphinx SPAWNED
{"k":"stringlist.Skull_Shrine.killed.2","t":{"KILLER":"X"}}    ← Skull Shrine KILLED
```

So `TAUNT_MAP` registers, for each of the 46 RealmEye "Encounters", the **spawn key**
`stringlist.<Name>.new` (name = display name, spaces → underscores). The `.killed` variant
can't match `.new`, so deaths never fire (no "defeat" filter needed — that earlier guess was
wrong; the real word is "killed"). Label is the readable encounter name. The "Test O3 Overlay"
menu item (§9) tests the overlay display alone, no game needed.

> ⚠ **Sender gate is fine** — events come from `#Oryx the Mad God`, which both starts with `#`
> and contains "Oryx the Mad God". The original failure was purely the text format (matching
> `"Grand Sphinx"` with a space against `Grand_Sphinx` in a JSON key).
>
> ⚠ Two encounter names with apostrophes (Bilgewater's Galleon, World's Oyster) may use a
> different internal token — verify those via the debug log.
>
> 🚩 **Likely follow-up for O3 itself:** since realm events use `stringlist.*` keys, the live
> **O3 boss taunts almost certainly do too** (e.g. some `stringlist.<...>.new`), which means
> the human-readable O3 taunt map in §6/§8 probably **won't match live in-game** — only the
> Test button (which feeds readable strings) works. This needs a real **O3 chat capture** to
> get the actual taunt keys, then the O3 entries get the same `stringlist` treatment.

---

## 11. Build & smoke-test checklist

1. `O3PhaseDetector.java` and `O3PhaseOverlay.java` under `src/main/java/tomato/gui/overlay/`.
2. Hook added in `ChatGUI.updateChat` (§8) + import.
3. Debug menu item wired in `TomatoMenuBar` (§9).
4. `tomato/build.gradle` re-pinned to `libs/RealmShark-v1.2.3.jar`, and that (latest) backend jar staged in `libs/` (§3).
5. `./gradlew shadowJar` → `build/libs/Tomato-v1.9.2.jar`.
6. On Windows: run the jar, open the menu, click **Test O3 Overlay** → overlay should
   flash through the four test taunts.
7. Start Sniffer, go to the Realm, trigger a realm event → overlay fires from live packets.
8. Log `p.name`/`p.text` once to confirm the real Oryx sender strings, then tighten §5/§8.

---

## 12. Stretch goals

**Implemented** (dedicated **Overlay** menu in `TomatoMenuBar`):
- ✅ **Enable O3 Overlay** checkbox — persisted as `o3OverlayEnabled` (defaults ON);
  toggles `O3PhaseOverlay.enabled`, checked in `ChatGUI.o3Overlay`.
- ✅ **Log Chat Senders (debug)** checkbox — persisted as `o3LogChatSenders`; toggles
  `O3PhaseOverlay.logSenders` to print every chat name/text for filter tuning (§5).
- ✅ **Test O3 Overlay** — fires the four sample taunts.
- ✅ **Reposition Overlay** — drag the overlay anywhere; persisted via `PropertiesManager`
  (`o3OverlayX` = horizontal **center**, `o3OverlayY` = top edge) and restored on next show, so
  the box stays horizontally centered on the chosen point (grows symmetrically) while keeping
  its vertical position. Toggling the menu item shows a draggable placeholder; untoggling
  saves and hides.

**Still open:**
- Per-phase custom `.wav` sounds (reuse the existing `tomato.realmshark.Sound` infra).
- Phase history: scrollable log of the last N alerts.
- Settings panel: opacity, font size, position.
