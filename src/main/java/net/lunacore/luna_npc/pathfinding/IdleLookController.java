package net.lunacore.luna_npc.pathfinding;

import net.minecraft.util.Mth;
import net.lunacore.luna_npc.entity.NpcEntity;

// Gives a standing-still NPC a touch of life: it holds a facing for a while, then turns to a new random one
// in a short eased snap, and holds again. The turn drives the same yaw fields navigation uses, so it renders
// like any other turn. Server-side only; the moment navigation, combat, farming or a player interaction
// takes the NPC over, that system reclaims the yaw and this idle drift resets.
public class IdleLookController {

    // A turn is a short, eased snap (ease-in-out) to the next facing — not a slow constant sweep, which
    // reads as robotic. The span scales with how far it turns so every turn feels equally brisk, capped so
    // even a big swing stays under half a second.
    private static final float DEGREES_PER_TICK = 16.0F;
    private static final int MIN_TURN_TICKS = 4;
    private static final int MAX_TURN_TICKS = 9;
    // The widest a single idle turn swings from the current facing.
    private static final float MAX_TURN_DEGREES = 120.0F;
    private static final int MIN_HOLD_TICKS = 40;
    private static final int MAX_HOLD_TICKS = 170;

    private final NpcEntity entity;
    private float startYaw;
    private float targetYaw;
    private int turnTicks;
    private int turnDuration;
    private int holdTicks;
    private boolean turning;

    public IdleLookController(NpcEntity entity) {
        this.entity = entity;
    }

    public void reset() {
        turning = false;
        holdTicks = 0;
    }

    public void tick() {
        if (!turning) {
            if (holdTicks > 0) {
                holdTicks--;
                return;
            }
            startYaw = Mth.wrapDegrees(entity.getYRot());
            float offset = (entity.getRandom().nextFloat() * 2.0F - 1.0F) * MAX_TURN_DEGREES;
            targetYaw = Mth.wrapDegrees(startYaw + offset);
            turnDuration = Mth.clamp(Math.round(Math.abs(offset) / DEGREES_PER_TICK), MIN_TURN_TICKS, MAX_TURN_TICKS);
            turnTicks = 0;
            turning = true;
        }
        turnTicks++;
        float progress = Math.min(1.0F, (float) turnTicks / turnDuration);
        // Smoothstep: ease in and out so the turn accelerates off the hold and settles into the next one.
        float eased = progress * progress * (3.0F - 2.0F * progress);
        float yaw = Mth.rotLerp(eased, startYaw, targetYaw);
        entity.setYRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
        if (turnTicks >= turnDuration) {
            turning = false;
            holdTicks = MIN_HOLD_TICKS + entity.getRandom().nextInt(MAX_HOLD_TICKS - MIN_HOLD_TICKS + 1);
        }
    }
}
