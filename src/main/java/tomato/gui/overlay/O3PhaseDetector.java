package tomato.gui.overlay;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Matches O3 (Oryx 3) chat taunts to a {@link PhaseAlert} describing the phase/attack.
 *
 * <p>Pure logic, no UI. {@link #detect(String)} runs a chat message body against an
 * ordered map of taunt substrings and returns the first match (or {@code null}).
 *
 * <p>Note: the project targets Java 8, so {@code PhaseAlert} is a plain immutable class
 * rather than a record. Accessor names mirror record style ({@code label()}, {@code color()},
 * ...) so callers read the same either way.
 */
public class O3PhaseDetector {

    public enum Danger { LOW, MEDIUM, HIGH, CRITICAL, OPPORTUNITY }

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

        public String label()    { return label; }
        public String color()    { return color; }
        public int    displayMs(){ return displayMs; }
        public Danger danger()   { return danger; }
        public String tip()      { return tip; }
    }

    // Order matters — Exalted (longer) strings must be inserted before base strings,
    // because detect() returns the first contains()-match.
    private static final Map<String, PhaseAlert> TAUNT_MAP = new LinkedHashMap<>();

    static {
        // ── HP Threshold Transitions ──────────────────────────────────────
        TAUNT_MAP.put("My dominance extends throughout the universe",
            new PhaseAlert("PORTALS SPAWNED — Pet Stasis missiles added", "#AA88FF", 4000, Danger.MEDIUM,
                "Portals orbit Oryx — watch for inward/outward stasis shots"));

        TAUNT_MAP.put("Dance, you helpless creatures",
            new PhaseAlert("DANCE PHASE — Rotating bomb rings", "#FF8800", 4000, Danger.HIGH,
                "70% HP — artifact rings rotate clockwise, stay in the gaps"));

        TAUNT_MAP.put("I will show you why my power is utterly beyond question",
            new PhaseAlert("⚡ EXALTED — All attacks heavily buffed", "#FFD700", 5000, Danger.CRITICAL,
                "55% HP — golden attacks, longer phase chains, more bullets everywhere"));

        TAUNT_MAP.put("Kneel to your ruler! My strength is without equal",
            new PhaseAlert("⚠ CELESTIAL INCOMING", "#FF4444", 5000, Danger.CRITICAL,
                "Moving to center — prepare to rotate counterclockwise"));

        TAUNT_MAP.put("FALL BEFORE MY CELESTIAL STRENGTH",
            new PhaseAlert("☆ CELESTIAL ACTIVE — Rotate counterclockwise!", "#FF2222", 8000, Danger.CRITICAL,
                "Gaps in portal spiral — he's vulnerable the whole time — ends in stagger"));

        TAUNT_MAP.put("The heavens are mine to command",
            new PhaseAlert("HEAVEN BEAMS — Constant holy beam rain", "#FFAAAA", 4000, Danger.HIGH,
                "15% HP — red circles = beam landing spots, keep moving"));

        // ── Special Events ────────────────────────────────────────────────
        TAUNT_MAP.put("NO! This cannot be",
            new PhaseAlert("✦ STAGGER — DPS NOW!", "#00FF88", 5000, Danger.OPPORTUNITY,
                "5 sec armor broken — stop at ~5% max HP to avoid early recovery"));

        TAUNT_MAP.put("You are unfit to speak in my presence",
            new PhaseAlert("⚡ COUNTER — SILENCE 30s INCOMING", "#FFFF00", 6000, Danger.CRITICAL,
                "Everyone silenced — he launches random attack without taunt, back off"));

        // ── Per-Attack Taunts (Exalted first, then base) ──────────────────

        TAUNT_MAP.put("Foul insects! Your armor will crumple before me",
            new PhaseAlert("CHASE 1 [Exalted] — 3-shot slash bursts + bombs", "#FF6600", 3500, Danger.HIGH,
                "Keep moving — don't over-DPS, guard triggers counter"));
        TAUNT_MAP.put("Your armor will crumple before me",
            new PhaseAlert("CHASE 1 — 3-shot slash bursts + bomb volleys", "#FFA500", 3000, Danger.MEDIUM,
                "He chases nearest player — keep running"));

        TAUNT_MAP.put("Belligerent creatures! There is nowhere to run",
            new PhaseAlert("CHASE 2 [Exalted] — Shotgun blasts + arrowheads (boomerang)", "#FF6600", 3500, Danger.HIGH,
                "Run perpendicular — arrowheads boomerang in Exalted"));
        TAUNT_MAP.put("There is nowhere to run",
            new PhaseAlert("CHASE 2 — Rapid shotgun blasts + arrowhead rings", "#FFA500", 3000, Danger.MEDIUM,
                "Dodge perpendicular, he lunges occasionally"));

        TAUNT_MAP.put("Defiant scoundrels! Accept your fate",
            new PhaseAlert("CHASE 3 [Exalted] — Spear barrage + Quieting orbs ★STAGGER OK", "#FF6600", 3500, Danger.HIGH,
                "Staggerable — DPS hard, Quiet orbs silence abilities"));
        TAUNT_MAP.put("Accept your fate",
            new PhaseAlert("CHASE 3 — Spear barrage + Quieting orbs ★STAGGER OK", "#FFA500", 3000, Danger.MEDIUM,
                "Staggerable — DPS hard to trigger stagger"));

        TAUNT_MAP.put("Lowly pigs! Fleeing is futile",
            new PhaseAlert("CHASE 4 [Exalted] — Armor-pierce crescent lines", "#FF6600", 3500, Danger.HIGH,
                "Purple tornado, armor-piercing — don't over-DPS"));
        TAUNT_MAP.put("Fleeing is futile",
            new PhaseAlert("CHASE 4 — Armor-piercing crescent lines", "#FFA500", 3000, Danger.MEDIUM,
                "Circles while chasing — keep moving"));

        TAUNT_MAP.put("Miserable mutts! My shield is enough to smash you to pieces",
            new PhaseAlert("SHIELD CHASE [Exalted] — Stunning charge ★STAGGER OK", "#FF6600", 3500, Danger.HIGH,
                "Staggerable — he pivots sharply, sidestep the cone"));
        TAUNT_MAP.put("My shield is enough to smash you to pieces",
            new PhaseAlert("SHIELD CHASE — Stunning bullet cone ★STAGGER OK", "#FFA500", 3000, Danger.MEDIUM,
                "Staggerable — dodge sideways, avoid the cone"));

        TAUNT_MAP.put("Meaningless whelps! You cannot escape from my control",
            new PhaseAlert("FIREBALL CHASE [Exalted] — Weak fireballs + slash chase", "#FF6600", 3500, Danger.HIGH,
                "Wide fireball spread — weave through, don't over-DPS"));
        TAUNT_MAP.put("You cannot escape from my control",
            new PhaseAlert("FIREBALL CHASE — Lingering Weak fireballs then chases", "#FFA500", 3000, Danger.MEDIUM,
                "Fireballs linger — weave through them"));

        TAUNT_MAP.put("Mindless brutes! Melt before my fury",
            new PhaseAlert("EXPLOSIVE CHARGE [Exalted] — Dazing lunge ★STAGGER OK", "#FF6600", 3500, Danger.HIGH,
                "Staggerable — get distance, DPS during pauses"));
        TAUNT_MAP.put("Melt before my fury",
            new PhaseAlert("EXPLOSIVE CHARGE — Dazing AoE lunges ★STAGGER OK", "#FFA500", 3000, Danger.MEDIUM,
                "Staggerable — dodge the lunges, DPS during pauses"));

        TAUNT_MAP.put("Disgusting creatures! Stand back in cowardice",
            new PhaseAlert("BARRAGE [Exalted] — Dense spiral tornado (stationary)", "#FF6600", 3500, Danger.HIGH,
                "Spirals rotate opposite directions — find the gaps"));
        TAUNT_MAP.put("Stand back in cowardice",
            new PhaseAlert("BARRAGE — Overlapping spiral waves (stationary)", "#FFA500", 3000, Danger.MEDIUM,
                "He stays in place — find spiral gaps"));

        TAUNT_MAP.put("Worthless scullions! There is nowhere to hide",
            new PhaseAlert("INNER ROTATION [Exalted] — Moving spiral counterclockwise", "#FF6600", 3500, Danger.HIGH,
                "Circles near center — move opposite direction"));
        TAUNT_MAP.put("There is nowhere to hide",
            new PhaseAlert("INNER ROTATION — Counterclockwise spiral near center", "#FFA500", 3000, Danger.MEDIUM,
                "Move opposite to his rotation to find gaps"));

        TAUNT_MAP.put("Abominable knaves! I will strike you down",
            new PhaseAlert("OUTER ROTATION [Exalted] — Edge circles, Exposing orbs ★STAGGER OK", "#FF6600", 3500, Danger.HIGH,
                "Staggerable — stay near center, dodge inward shotguns"));
        TAUNT_MAP.put("I will strike you down",
            new PhaseAlert("OUTER ROTATION — Outer edge circles, Exposing spinners ★STAGGER OK", "#FFA500", 3000, Danger.MEDIUM,
                "Staggerable — stay near center, dodge inward shotguns"));

        TAUNT_MAP.put("Wretched slime! Every slash shall slice through your pathetic defenses",
            new PhaseAlert("SLASHES [Exalted] — Stationary slash waves (diagonal added)", "#FF6600", 3500, Danger.HIGH,
                "Additional diagonal waves in Exalted — find the gaps and weave"));
        TAUNT_MAP.put("Every slash shall slice through your pathetic defenses",
            new PhaseAlert("SLASHES — Stationary slash waves (gaps exist)", "#FFA500", 3000, Danger.MEDIUM,
                "He stays still — find the gaps and weave through"));

        TAUNT_MAP.put("Rebellious wretches! Gaze at my power",
            new PhaseAlert("GAZE [Exalted] — Widening shotgun, 2 directions ★STAGGER OK", "#FF6600", 3500, Danger.HIGH,
                "Staggerable — move sideways, shotguns widen and split"));
        TAUNT_MAP.put("Gaze at my power",
            new PhaseAlert("GAZE — Widening shotgun aimed at you ★STAGGER OK", "#FFA500", 3000, Danger.MEDIUM,
                "Staggerable — move sideways as shotguns widen"));

        TAUNT_MAP.put("Incompetent fools! Panic and scream",
            new PhaseAlert("BOMB ARTIFACTS [Exalted] — Cross-pattern AoE bombs ★STAGGER OK", "#FF6600", 3500, Danger.HIGH,
                "Staggerable — twice the artifacts, stay between cross patterns"));
        TAUNT_MAP.put("Panic and scream",
            new PhaseAlert("BOMB ARTIFACTS — Cross-pattern AoE bombs ★STAGGER OK", "#FFA500", 3000, Danger.MEDIUM,
                "Staggerable — stay between the cross patterns"));

        TAUNT_MAP.put("Deplorable slugs! The ground quakes from my splendor",
            new PhaseAlert("BOMB RAIN [Exalted] — 4 colored bomb waves ★STAGGER OK", "#FF6600", 3500, Danger.HIGH,
                "Staggerable — more bombs, faster — each color has a safespot"));
        TAUNT_MAP.put("The ground quakes from my splendor",
            new PhaseAlert("BOMB RAIN — 4 colored sick bomb waves ★STAGGER OK", "#FFA500", 3000, Danger.MEDIUM,
                "Staggerable — each color (red/blue/yellow/green) has a safespot"));

        TAUNT_MAP.put("Loathsome worms! The cosmos shall rain down my wrath",
            new PhaseAlert("METEOR SHOWER [Exalted] — Red circle telegraphed meteors", "#FF6600", 3500, Danger.HIGH,
                "Red circles = landing spots — stay mobile"));
        TAUNT_MAP.put("The cosmos shall rain down my wrath",
            new PhaseAlert("METEOR SHOWER — Red circle telegraphed meteors", "#FFA500", 3000, Danger.MEDIUM,
                "Avoid red circles — guard triggers if over-DPS'd"));

        // ── Realm Event Tests (for pipeline testing without reaching O3) ───
        TAUNT_MAP.put("Cube God has appeared",
            new PhaseAlert("CUBE GOD spawned", "#8888FF", 3000, Danger.LOW, "[test event]"));
        TAUNT_MAP.put("A Skull Shrine has risen",
            new PhaseAlert("SKULL SHRINE spawned", "#FF8888", 3000, Danger.LOW, "[test event]"));
        TAUNT_MAP.put("Oryx has been summoned",
            new PhaseAlert("ORYX SUMMONED", "#FFD700", 3000, Danger.MEDIUM, "[test event]"));
    }

    public static PhaseAlert detect(String chatText) {
        if (chatText == null || chatText.trim().isEmpty()) return null;
        for (Map.Entry<String, PhaseAlert> entry : TAUNT_MAP.entrySet()) {
            if (chatText.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }
}
