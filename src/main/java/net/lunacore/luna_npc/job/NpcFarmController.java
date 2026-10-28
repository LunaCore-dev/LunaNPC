package net.lunacore.luna_npc.job;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.api.NpcFarmSubType;
import net.lunacore.luna_npc.api.NpcJobType;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.pathfinding.PathWorldView;

// Autonomous farming for a FARMING-job NPC: it walks its home radius harvesting mature crops (crops,
// nether wart, stem fruit) on a timed break/replant cadence, banks each harvest's drops straight into an
// internal buffer, and empties that buffer into a chest it finds (and claims) by radius scan. Ticked from
// NpcEntity between combat and wander; combat and scripted moves take priority and pause it. The buffer
// survives chunk unload in entity NBT.
public final class NpcFarmController {

    private enum State { IDLE, WALK_TO_CROP, HARVESTING, WALK_TO_CHEST, DEPOSIT }

    private enum HarvestPhase { BEFORE_BREAK, BEFORE_REPLANT, BEFORE_NEXT }

    // A farmland cell (with any growing crop on it) the farmer recently tended, kept just long enough to
    // tell a later accidental trample of that exact spot apart from ordinary open ground.
    private static final class TendedCell {
        private BlockState farmlandState;
        private BlockState cropState;
        private int ttl;

        private TendedCell(BlockState farmlandState, BlockState cropState, int ttl) {
            this.farmlandState = farmlandState;
            this.cropState = cropState;
            this.ttl = ttl;
        }
    }

    private static final String BUFFER_KEY = "luna_farm_buffer";
    private static final int BUFFER_SIZE = 27;
    private static final int SCAN_INTERVAL = 40;
    private static final int TICKS_PER_SECOND = 20;
    // Bounds a barren scan so one pass can never stall the tick; the spiral is centre-out, so nearer
    // crops and chests are always found first and the cap only clips the far edge of a huge radius.
    private static final int SCAN_BLOCK_BUDGET = 20_000;
    private static final int MAX_FALL = 3;
    private static final int MIN_FARM_RADIUS = 16;
    private static final int VERTICAL_SCAN = 3;
    private static final int WALK_TIMEOUT_TICKS = 200;
    private static final double HARVEST_REACH_SQ = 1.0;
    private static final double DEPOSIT_REACH_SQ = 6.25;
    private static final int MAX_BLACKLIST = 64;
    private static final int REPAIR_FOOTPRINT_RADIUS = 1;
    private static final int TENDED_MEMORY_TICKS = 100;
    private static final int MAX_TENDED_CELLS = 256;
    private static final int BREAK_DELAY_TICKS = 10;
    private static final int REPLANT_DELAY_TICKS = 20;
    private static final int NEXT_CROP_DELAY_TICKS = 10;
    // A just-harvested position is locked out of re-selection this long, so a block that fails to clear
    // (or is restored by anything) can never be re-harvested into a duplication loop.
    private static final int HARVEST_COOLDOWN_TICKS = 200;

    private final NpcEntity entity;
    private final SimpleContainer buffer = new SimpleContainer(BUFFER_SIZE);
    private final Set<Long> unreachableChests = new HashSet<>();
    private final Set<Long> unreachableCrops = new HashSet<>();
    private final Map<Long, TendedCell> tendedCells = new HashMap<>();
    private final Map<Long, Integer> harvestCooldowns = new HashMap<>();
    private State state = State.IDLE;
    private HarvestPhase harvestPhase;
    private int phaseTicks;
    private BlockState pendingReplant;
    private int scanCooldown;
    private int walkTimeout;
    private int ticksSinceProduce;
    private BlockPos targetCrop;
    private GlobalPos claimedChest;
    private BlockPos chestApproach;
    // The timed deposit at the chest: a brief pause, open, a random hold, close, a short pause, then
    // leave — driven by depositPhase + depositTimer, with lidOpen tracking whether we opened the lid so
    // an interrupt (combat, a broken chest) can always close it again.
    private static final int DEPOSIT_PRE_OPEN_TICKS = 10;
    private static final int DEPOSIT_HOLD_MIN_TICKS = 40;
    private static final int DEPOSIT_HOLD_MAX_TICKS = 120;
    private static final int DEPOSIT_POST_CLOSE_MIN_TICKS = 6;
    private static final int DEPOSIT_POST_CLOSE_MAX_TICKS = 12;
    private int depositPhase;
    private int depositTimer;
    private boolean lidOpen;

    public NpcFarmController(NpcEntity entity) {
        this.entity = entity;
    }

    // True while the farmer owns its movement, so the wander controller stays out of the way.
    public boolean isWorking() {
        return state != State.IDLE;
    }

    public void tick() {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!canFarm()) {
            if (state != State.IDLE || claimedChest != null) {
                reset(level);
            }
            return;
        }
        ticksSinceProduce = buffer.isEmpty() ? 0 : ticksSinceProduce + 1;
        tickCooldowns();
        repairTrampledField(level);
        switch (state) {
            case IDLE -> tickIdle(level);
            case WALK_TO_CROP -> tickWalkToCrop(level);
            case HARVESTING -> tickHarvesting(level);
            case WALK_TO_CHEST -> tickWalkToChest(level);
            case DEPOSIT -> tickDeposit(level);
        }
    }

    private boolean canFarm() {
        return entity.jobActive()
                && entity.jobSettings().jobType() == NpcJobType.FARMING
                && !entity.combatController().isFighting()
                && !entity.combatController().isFleeing()
                && !(entity.navigation().isNavigating() && entity.navigation().isScripted())
                && activeSubType() != null;
    }

    private void reset(ServerLevel level) {
        // A preempt (combat, flee) between break and replant still replants, so no seed is ever lost.
        if (pendingReplant != null && targetCrop != null && level.getBlockState(targetCrop).isAir()) {
            level.setBlockAndUpdate(targetCrop, pendingReplant);
        }
        pendingReplant = null;
        releaseChest(level);
        targetCrop = null;
        ticksSinceProduce = 0;
        state = State.IDLE;
        scanCooldown = SCAN_INTERVAL;
    }

    private void tickIdle(ServerLevel level) {
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }
        BlockPos crop = scanForCrop(level);
        boolean depositDue = bufferFull()
                || (crop == null && !buffer.isEmpty() && ticksSinceProduce >= idleDepositTicks());
        if (depositDue && acquireChest(level)) {
            state = State.WALK_TO_CHEST;
            return;
        }
        if (crop != null) {
            targetCrop = crop;
            walkTimeout = WALK_TIMEOUT_TICKS;
            state = State.WALK_TO_CROP;
            entity.navigation().moveTo(Vec3.atBottomCenterOf(crop), false, false);
            return;
        }
        scanCooldown = SCAN_INTERVAL;
    }

    private void tickWalkToCrop(ServerLevel level) {
        BlockPos crop = targetCrop;
        if (crop == null || !isHarvestable(level, crop)) {
            failCrop(crop);
            return;
        }
        if (entity.blockPosition().equals(crop)
                || entity.position().distanceToSqr(Vec3.atBottomCenterOf(crop)) <= HARVEST_REACH_SQ) {
            entity.navigation().stop();
            faceCrop(crop);
            state = State.HARVESTING;
            harvestPhase = HarvestPhase.BEFORE_BREAK;
            phaseTicks = jittered(BREAK_DELAY_TICKS);
            return;
        }
        if (!entity.navigation().isNavigating() || --walkTimeout <= 0) {
            blacklist(unreachableCrops, crop);
            entity.navigation().stop();
            failCrop(crop);
        }
    }

    private void tickHarvesting(ServerLevel level) {
        BlockPos crop = targetCrop;
        if (crop == null) {
            state = State.IDLE;
            scanCooldown = 0;
            return;
        }
        faceCrop(crop);
        if (--phaseTicks > 0) {
            return;
        }
        switch (harvestPhase) {
            case BEFORE_BREAK -> {
                if (!isHarvestable(level, crop)) {
                    targetCrop = null;
                    state = State.IDLE;
                    scanCooldown = 0;
                    return;
                }
                destroyAndBank(level, crop);
                harvestPhase = HarvestPhase.BEFORE_REPLANT;
                phaseTicks = jittered(REPLANT_DELAY_TICKS);
            }
            case BEFORE_REPLANT -> {
                if (pendingReplant != null && level.getBlockState(crop).isAir()) {
                    level.setBlockAndUpdate(crop, pendingReplant);
                }
                pendingReplant = null;
                harvestPhase = HarvestPhase.BEFORE_NEXT;
                phaseTicks = jittered(NEXT_CROP_DELAY_TICKS);
            }
            case BEFORE_NEXT -> {
                ticksSinceProduce = 0;
                unreachableCrops.clear();
                targetCrop = null;
                state = State.IDLE;
                scanCooldown = 0;
            }
        }
    }

    // Base +/-25%, so the cadence never looks machine-timed.
    private int jittered(int baseTicks) {
        int span = baseTicks / 4;
        return baseTicks - span + entity.getRandom().nextInt(2 * span + 1);
    }

    private void tickCooldowns() {
        var it = harvestCooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private void faceCrop(BlockPos crop) {
        double dx = crop.getX() + 0.5 - entity.getX();
        double dz = crop.getZ() + 0.5 - entity.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        entity.setYRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
    }

    private void failCrop(BlockPos crop) {
        if (crop != null) {
            blacklist(unreachableCrops, crop);
        }
        targetCrop = null;
        state = State.IDLE;
        scanCooldown = SCAN_INTERVAL;
    }

    private void tickWalkToChest(ServerLevel level) {
        if (claimedChest == null || chestApproach == null || !isDepositable(level, claimedChest.pos())) {
            releaseChest(level);
            state = State.IDLE;
            scanCooldown = SCAN_INTERVAL;
            return;
        }
        if (entity.position().distanceToSqr(Vec3.atCenterOf(claimedChest.pos())) <= DEPOSIT_REACH_SQ) {
            state = State.DEPOSIT;
            depositPhase = 0;
            depositTimer = DEPOSIT_PRE_OPEN_TICKS;
            entity.navigation().stop();
            return;
        }
        if (!entity.navigation().isNavigating() || --walkTimeout <= 0) {
            blacklist(unreachableChests, claimedChest.pos());
            entity.navigation().stop();
            releaseChest(level);
            state = State.IDLE;
            scanCooldown = SCAN_INTERVAL;
        }
    }

    private void tickDeposit(ServerLevel level) {
        if (claimedChest == null
                || !(level.getBlockEntity(claimedChest.pos()) instanceof ChestBlockEntity chest)
                || ChestBlock.isChestBlockedAt(level, claimedChest.pos())) {
            finishDeposit(level);
            return;
        }
        // Stay planted at the chest through the whole open/hold/close sequence.
        entity.navigation().stop();
        if (depositTimer > 0) {
            depositTimer--;
            return;
        }
        switch (depositPhase) {
            case 0 -> {
                openLid(level);
                depositInto(chest);
                depositPhase = 1;
                depositTimer = randomTicks(DEPOSIT_HOLD_MIN_TICKS, DEPOSIT_HOLD_MAX_TICKS);
            }
            case 1 -> {
                closeLid(level);
                depositPhase = 2;
                depositTimer = randomTicks(DEPOSIT_POST_CLOSE_MIN_TICKS, DEPOSIT_POST_CLOSE_MAX_TICKS);
            }
            default -> finishDeposit(level);
        }
    }

    private void depositInto(ChestBlockEntity chest) {
        for (int slot = 0; slot < buffer.getContainerSize(); slot++) {
            ItemStack stack = buffer.getItem(slot);
            if (!stack.isEmpty()) {
                buffer.setItem(slot, HopperBlockEntity.addItem(null, chest, stack, null));
            }
        }
    }

    private void finishDeposit(ServerLevel level) {
        releaseChest(level);
        unreachableChests.clear();
        state = State.IDLE;
        scanCooldown = 0;
    }

    private int randomTicks(int min, int max) {
        return min + entity.getRandom().nextInt(max - min + 1);
    }

    private void openLid(ServerLevel level) {
        if (!lidOpen && claimedChest != null) {
            setChestLid(level, claimedChest.pos(), true);
            lidOpen = true;
        }
    }

    // Closes the lid if we opened it; called at every exit from the deposit (finish, release, an
    // interrupting reset) so the chest never stays stuck open.
    private void closeLid(ServerLevel level) {
        if (lidOpen && claimedChest != null) {
            setChestLid(level, claimedChest.pos(), false);
            lidOpen = false;
        }
    }

    private static void setChestLid(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return;
        }
        level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
        level.playSound(null, pos, open ? net.minecraft.sounds.SoundEvents.CHEST_OPEN
                        : net.minecraft.sounds.SoundEvents.CHEST_CLOSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    private void destroyAndBank(ServerLevel level, BlockPos pos) {
        BlockState cropState = level.getBlockState(pos);
        // The drops are computed once and banked straight into the buffer — destroyBlock(drop=true)
        // plus a ground pickup pass double-counted the yield and littered the field.
        List<ItemStack> drops = Block.getDrops(cropState, level, pos, null);
        level.destroyBlock(pos, false, entity);
        if (!level.getBlockState(pos).isAir()) {
            level.removeBlock(pos, false);
        }
        harvestCooldowns.put(pos.asLong(), HARVEST_COOLDOWN_TICKS);
        pendingReplant = replantState(cropState.getBlock());
        // Exotic modded crops whose seed isn't a matching BlockItem replant "for free" — a deterministic
        // farmer beats one that stalls hunting for a seed it can't identify.
        if (pendingReplant != null && !consumeSeed(drops, cropState.getBlock())) {
            consumeSeedFromBuffer(cropState.getBlock());
        }
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            ItemStack leftover = buffer.addItem(drop);
            if (!leftover.isEmpty()) {
                Block.popResource(level, pos, leftover);
            }
        }
    }

    public static boolean isFarmableBlock(Block block) {
        return block instanceof CropBlock || block instanceof NetherWartBlock || isStemFruit(block);
    }

    // Stem fruit has no shared vanilla superclass — melon is a plain Block — so the fruit blocks are
    // named here; the stem regrows them, so they get no replant.
    private static boolean isStemFruit(Block block) {
        return block instanceof PumpkinBlock || block == Blocks.MELON;
    }

    private static boolean isMatureFarmable(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        return isStemFruit(block);
    }

    private static BlockState replantState(Block block) {
        if (block instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }
        if (block instanceof NetherWartBlock wart) {
            return wart.defaultBlockState();
        }
        return null;
    }

    private static boolean consumeSeed(List<ItemStack> drops, Block crop) {
        for (ItemStack drop : drops) {
            if (isSeedOf(drop, crop)) {
                drop.shrink(1);
                return true;
            }
        }
        return false;
    }

    private void consumeSeedFromBuffer(Block crop) {
        for (int slot = 0; slot < buffer.getContainerSize(); slot++) {
            ItemStack stack = buffer.getItem(slot);
            if (isSeedOf(stack, crop)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    buffer.setItem(slot, ItemStack.EMPTY);
                }
                return;
            }
        }
    }

    private static boolean isSeedOf(ItemStack stack, Block crop) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && item.getBlock() == crop;
    }

    private BlockPos scanForCrop(ServerLevel level) {
        BlockPos anchor = BlockPos.containing(entity.homeAnchor());
        int radius = cropRadius();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int budget = SCAN_BLOCK_BUDGET;
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = -VERTICAL_SCAN; dy <= VERTICAL_SCAN; dy++) {
                        if (--budget < 0) {
                            return null;
                        }
                        cursor.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                        if (!unreachableCrops.contains(cursor.asLong()) && isHarvestable(level, cursor)) {
                            return cursor.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isHarvestable(ServerLevel level, BlockPos pos) {
        if (harvestCooldowns.containsKey(pos.asLong())) {
            return false;
        }
        BlockState cropState = level.getBlockState(pos);
        if (!isMatureFarmable(cropState)) {
            return false;
        }
        List<String> whitelist = activeSubType().cropBlockIds();
        if (whitelist.isEmpty()) {
            return true;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(cropState.getBlock());
        return whitelist.contains(id.toString());
    }

    private boolean acquireChest(ServerLevel level) {
        BlockPos anchor = BlockPos.containing(entity.homeAnchor());
        int radius = chestRadius();
        FarmChestClaims claims = FarmChestClaims.get(level.getServer());
        PathWorldView world = new PathWorldView(level, entity.movementSettings().avoidedBlocks(), MAX_FALL);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int budget = SCAN_BLOCK_BUDGET;
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = -VERTICAL_SCAN; dy <= VERTICAL_SCAN; dy++) {
                        if (--budget < 0) {
                            return false;
                        }
                        cursor.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                        if (tryClaimChestAt(level, claims, world, cursor)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean tryClaimChestAt(ServerLevel level, FarmChestClaims claims, PathWorldView world,
                                    BlockPos.MutableBlockPos cursor) {
        if (unreachableChests.contains(cursor.asLong()) || !isDepositable(level, cursor)) {
            return false;
        }
        BlockPos chestPos = cursor.immutable();
        GlobalPos globalChest = GlobalPos.of(level.dimension(), chestPos);
        if (!claims.isClaimable(globalChest, entity.getUUID())) {
            return false;
        }
        BlockPos approach = findApproach(world, chestPos);
        if (approach == null) {
            return false;
        }
        claims.claim(globalChest, entity.getUUID());
        claimedChest = globalChest;
        chestApproach = approach;
        walkTimeout = WALK_TIMEOUT_TICKS;
        entity.navigation().moveTo(Vec3.atBottomCenterOf(approach), false, false);
        return true;
    }

    private boolean isDepositable(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChestBlockEntity chest
                && !ChestBlock.isChestBlockedAt(level, pos)
                && hasRoom(chest);
    }

    private boolean hasRoom(ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack inChest = chest.getItem(slot);
            if (inChest.isEmpty()) {
                return true;
            }
            for (int mine = 0; mine < buffer.getContainerSize(); mine++) {
                ItemStack stack = buffer.getItem(mine);
                if (!stack.isEmpty() && inChest.getCount() < inChest.getMaxStackSize()
                        && ItemStack.isSameItemSameComponents(inChest, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    // A standable cell next to the chest the farmer can walk onto to reach it; the actual reachability
    // is proven by the walk itself (a give-up blacklists the chest), this only rejects buried/walled ones.
    private BlockPos findApproach(PathWorldView world, BlockPos chest) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = chest.relative(dir);
            for (int dy = 1; dy >= -1; dy--) {
                if (world.isStandable(side.getX(), side.getY() + dy, side.getZ())) {
                    return new BlockPos(side.getX(), side.getY() + dy, side.getZ());
                }
            }
        }
        return null;
    }

    private void releaseChest(ServerLevel level) {
        closeLid(level);
        if (claimedChest != null) {
            FarmChestClaims.get(level.getServer()).release(claimedChest, entity.getUUID());
            claimedChest = null;
            chestApproach = null;
        }
    }

    private NpcFarmSubType activeSubType() {
        List<NpcFarmSubType> subTypes = entity.jobSettings().farmSubTypes();
        int index = entity.rolledJobSubType();
        return index >= 0 && index < subTypes.size() ? subTypes.get(index) : null;
    }

    private int cropRadius() {
        int wander = Math.max(MIN_FARM_RADIUS, entity.movementSettings().wander().radius());
        return Math.min(NpcValidation.MAX_WANDER_RADIUS, Math.min(activeSubType().radius(), wander));
    }

    private int chestRadius() {
        return Math.min(NpcValidation.MAX_FARM_CHEST_RADIUS, activeSubType().chestRadius());
    }

    private boolean bufferFull() {
        return !buffer.isEmpty() && totalPieces() >= capacity();
    }

    private int totalPieces() {
        int total = 0;
        for (int slot = 0; slot < buffer.getContainerSize(); slot++) {
            total += buffer.getItem(slot).getCount();
        }
        return total;
    }

    private int capacity() {
        return activeSubType().capacity();
    }

    private int idleDepositTicks() {
        return activeSubType().idleDepositSeconds() * TICKS_PER_SECOND;
    }

    // A recorded mature crop is never rebuilt (the maturity check below), so the deliberate gap between
    // break and replant is left alone; any tended farmland now trampled to dirt (or a growing crop that
    // vanished with it) was lost to a stray jump and rebuilt.
    private void repairTrampledField(ServerLevel level) {
        recordTendedCells(level);
        var cells = tendedCells.entrySet().iterator();
        while (cells.hasNext()) {
            Map.Entry<Long, TendedCell> entry = cells.next();
            TendedCell cell = entry.getValue();
            if (rebuildIfLost(level, BlockPos.of(entry.getKey()), cell) || --cell.ttl <= 0) {
                cells.remove();
            }
        }
    }

    private void recordTendedCells(ServerLevel level) {
        BlockPos feet = entity.blockPosition();
        for (int dx = -REPAIR_FOOTPRINT_RADIUS; dx <= REPAIR_FOOTPRINT_RADIUS; dx++) {
            for (int dz = -REPAIR_FOOTPRINT_RADIUS; dz <= REPAIR_FOOTPRINT_RADIUS; dz++) {
                BlockPos farmland = new BlockPos(feet.getX() + dx, feet.getY() - 1, feet.getZ() + dz);
                BlockState farmlandState = level.getBlockState(farmland);
                if (!(farmlandState.getBlock() instanceof FarmlandBlock)) {
                    continue;
                }
                BlockState above = level.getBlockState(farmland.above());
                remember(farmland, farmlandState, above.getBlock() instanceof CropBlock ? above : null);
            }
        }
    }

    private void remember(BlockPos farmland, BlockState farmlandState, BlockState cropState) {
        TendedCell cell = tendedCells.get(farmland.asLong());
        if (cell == null) {
            if (tendedCells.size() >= MAX_TENDED_CELLS) {
                tendedCells.clear();
            }
            tendedCells.put(farmland.asLong(), new TendedCell(farmlandState, cropState, TENDED_MEMORY_TICKS));
            return;
        }
        cell.farmlandState = farmlandState;
        cell.cropState = cropState;
        cell.ttl = TENDED_MEMORY_TICKS;
    }

    private boolean rebuildIfLost(ServerLevel level, BlockPos farmland, TendedCell cell) {
        boolean farmlandLost = level.getBlockState(farmland).is(Blocks.DIRT);
        if (farmlandLost) {
            level.setBlockAndUpdate(farmland, cell.farmlandState);
        }
        boolean cropLost = cell.cropState != null
                && cell.cropState.getBlock() instanceof CropBlock crop && !crop.isMaxAge(cell.cropState)
                && level.getBlockState(farmland.above()).isAir();
        if (cropLost) {
            level.setBlockAndUpdate(farmland.above(), cell.cropState);
        }
        return farmlandLost || cropLost;
    }

    private static void blacklist(Set<Long> set, BlockPos pos) {
        if (set.size() >= MAX_BLACKLIST) {
            set.clear();
        }
        set.add(pos.asLong());
    }

    public void releaseClaims(ServerLevel level) {
        FarmChestClaims.get(level.getServer()).releaseAll(entity.getUUID());
        claimedChest = null;
        chestApproach = null;
    }

    // Copies, so API consumers can't mutate the live buffer.
    public List<ItemStack> bufferSnapshot() {
        List<ItemStack> items = new java.util.ArrayList<>();
        for (int slot = 0; slot < buffer.getContainerSize(); slot++) {
            ItemStack stack = buffer.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        return List.copyOf(items);
    }

    public void saveBuffer(ValueOutput output) {
        List<ItemStack> items = new java.util.ArrayList<>();
        for (int slot = 0; slot < buffer.getContainerSize(); slot++) {
            ItemStack stack = buffer.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        output.store(BUFFER_KEY, ItemStack.CODEC.listOf(), items);
    }

    public void loadBuffer(ValueInput input) {
        buffer.clearContent();
        input.read(BUFFER_KEY, ItemStack.CODEC.listOf())
                .ifPresent(items -> items.forEach(buffer::addItem));
    }
}
