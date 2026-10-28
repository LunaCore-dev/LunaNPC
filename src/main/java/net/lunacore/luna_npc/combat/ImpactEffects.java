package net.lunacore.luna_npc.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.api.NpcImpactSettings;

// Executes an attack's impact on the world: carves a rough crater and/or showers the area with
// particles for a while. The crater edge is jittered per block so it reads as a blast mark, not
// a perfect sphere. Particle showers outlive the projectile that caused them, so they tick here
// on the server instead of on any entity.
public final class ImpactEffects {

    // The crater core is always removed; the outer quarter of the radius is jittered per block.
    private static final float CRATER_SOLID_FRACTION = 0.75F;
    private static final int PARTICLE_PULSE_INTERVAL_TICKS = 2;

    private record ParticleShower(ServerLevel level, Vec3 center, int radius,
                                  SimpleParticleType particle, int endTick) {
    }

    private static final List<ParticleShower> activeShowers = new ArrayList<>();

    private ImpactEffects() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ImpactEffects::tickShowers);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> activeShowers.clear());
    }

    // The impact sound plays at the impact spot; block destruction stays deliberately silent so
    // this one (plus the attack-time sound the controller plays) is all the combat makes.
    public static void trigger(ServerLevel level, Vec3 center, NpcImpactSettings impact, String soundId) {
        playSoundAt(level, center, soundId);
        if (impact.destroyBlocks()) {
            carveCrater(level, center, impact.radius());
        }
        if (impact.hasParticles()) {
            resolveParticle(impact.particleId()).ifPresent(particle ->
                    activeShowers.add(new ParticleShower(level, center, impact.radius(), particle,
                            level.getServer().getTickCount()
                                    + Mth.ceil(impact.particleSeconds() * 20.0F))));
        }
    }

    public static void playSoundAt(ServerLevel level, Vec3 pos, String soundId) {
        if (soundId == null || soundId.isBlank()) {
            return;
        }
        Identifier parsed = Identifier.tryParse(soundId);
        if (parsed == null) {
            return;
        }
        BuiltInRegistries.SOUND_EVENT.getOptional(parsed)
                .ifPresent(sound -> level.playSound(null, pos.x, pos.y, pos.z,
                        sound, SoundSource.NEUTRAL, 1.0F, 1.0F));
    }

    private static void carveCrater(ServerLevel level, Vec3 center, int radius) {
        RandomSource random = level.getRandom();
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            double distance = Math.sqrt(pos.distToCenterSqr(center.x, center.y, center.z));
            // Per-block jitter frays the crater edge into an irregular blast shape.
            float threshold = radius * (CRATER_SOLID_FRACTION
                    + random.nextFloat() * (1.0F - CRATER_SOLID_FRACTION));
            if (distance > threshold) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()
                    || state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            // removeBlock (not destroyBlock) so there is no vanilla break sound or break
            // particle — the impact sound and the configured impact particle stand alone. No
            // drops either: a blast crater of item entities would bury the effect and the server.
            level.removeBlock(pos, false);
        }
    }

    private static void tickShowers(MinecraftServer server) {
        if (activeShowers.isEmpty() || server.getTickCount() % PARTICLE_PULSE_INTERVAL_TICKS != 0) {
            return;
        }
        int now = server.getTickCount();
        Iterator<ParticleShower> iterator = activeShowers.iterator();
        while (iterator.hasNext()) {
            ParticleShower shower = iterator.next();
            if (now >= shower.endTick() || shower.level().getServer() != server) {
                iterator.remove();
                continue;
            }
            double spread = Math.max(0.5, shower.radius() * 0.45);
            int count = 2 + shower.radius() * 2;
            shower.level().sendParticles(shower.particle(),
                    shower.center().x, shower.center().y, shower.center().z,
                    count, spread, spread, spread, 0.0);
        }
    }

    private static java.util.Optional<SimpleParticleType> resolveParticle(String particleId) {
        Identifier parsed = Identifier.tryParse(particleId);
        if (parsed == null) {
            return java.util.Optional.empty();
        }
        return BuiltInRegistries.PARTICLE_TYPE.getOptional(parsed)
                .filter(type -> type instanceof SimpleParticleType)
                .map(type -> (SimpleParticleType) type);
    }
}
