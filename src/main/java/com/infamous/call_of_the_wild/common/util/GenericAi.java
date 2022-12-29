package com.infamous.call_of_the_wild.common.util;

import com.google.common.collect.ImmutableList;
import com.infamous.call_of_the_wild.common.registry.COTWMemoryModuleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class GenericAi {

    public static List<LivingEntity> getNearbyAdults(LivingEntity ageableMob) {
        return ageableMob.getBrain().getMemory(COTWMemoryModuleTypes.NEAREST_ADULTS.get()).orElse(ImmutableList.of());
    }

    public static List<LivingEntity> getNearbyVisibleAdults(LivingEntity ageableMob) {
        return ageableMob.getBrain().getMemory(COTWMemoryModuleTypes.NEAREST_VISIBLE_ADULTS.get()).orElse(ImmutableList.of());
    }

    public static List<LivingEntity> getNearbyAllies(LivingEntity ageableMob) {
        return ageableMob.getBrain().getMemory(COTWMemoryModuleTypes.NEAREST_ALLIES.get()).orElse(ImmutableList.of());
    }

    public static List<LivingEntity> getNearbyVisibleAllies(LivingEntity ageableMob) {
        return ageableMob.getBrain().getMemory(COTWMemoryModuleTypes.NEAREST_VISIBLE_ALLIES.get()).orElse(ImmutableList.of());
    }

    public static Optional<Player> getNearestVisibleTargetablePlayer(LivingEntity livingEntity) {
        return livingEntity.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
    }

    public static void stopWalking(PathfinderMob pathfinderMob) {
        pathfinderMob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        pathfinderMob.getNavigation().stop();
    }

    public static Optional<LivingEntity> getAttackTarget(LivingEntity livingEntity){
        return livingEntity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
    }

    public static void setAvoidTarget(LivingEntity livingEntity, LivingEntity target, int avoidTimeInTicks) {
        AiUtil.eraseAllMemories(livingEntity, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.WALK_TARGET);
        livingEntity.getBrain().setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, target, avoidTimeInTicks);
    }

    public static boolean isNearAvoidTarget(LivingEntity livingEntity, int desiredDistanceFromAvoidTarget) {
        return isNearTarget(livingEntity, desiredDistanceFromAvoidTarget, MemoryModuleType.AVOID_TARGET);
    }

    public static boolean isNearDisliked(LivingEntity livingEntity, int desiredDistanceFromDisliked) {
        return isNearTarget(livingEntity, desiredDistanceFromDisliked, COTWMemoryModuleTypes.NEAREST_VISIBLE_DISLIKED.get());
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static boolean isNearTarget(LivingEntity livingEntity, int closeEnough, MemoryModuleType<LivingEntity> targetMemory) {
        Brain<?> brain = livingEntity.getBrain();
        return brain.hasMemoryValue(targetMemory)
                && brain.getMemory(targetMemory).get().closerThan(livingEntity, closeEnough);
    }

    public static Vec3 getRandomNearbyPos(PathfinderMob pathfinderMob, int maxXZDistance, int maxYDistance) {
        Vec3 pos = LandRandomPos.getPos(pathfinderMob, maxXZDistance, maxYDistance);
        return pos == null ? pathfinderMob.position() : pos;
    }

    public static boolean seesPlayerHoldingWantedItem(LivingEntity livingEntity) {
        return livingEntity.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM);
    }

    public static boolean doesntSeeAnyPlayerHoldingWantedItem(LivingEntity livingEntity) {
        return !seesPlayerHoldingWantedItem(livingEntity);
    }

    public static void startSleeping(LivingEntity livingEntity, BlockPos bedPosition) {
        livingEntity.startSleeping(bedPosition);
        livingEntity.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, livingEntity.level.getGameTime());
        livingEntity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        livingEntity.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    public static void stopSleeping(LivingEntity livingEntity) {
        livingEntity.stopSleeping();
        livingEntity.getBrain().setMemory(MemoryModuleType.LAST_WOKEN, livingEntity.level.getGameTime());
    }
}
