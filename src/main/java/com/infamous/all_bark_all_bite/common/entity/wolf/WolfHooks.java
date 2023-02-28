package com.infamous.all_bark_all_bite.common.entity.wolf;

import com.infamous.all_bark_all_bite.AllBarkAllBite;
import com.infamous.all_bark_all_bite.common.util.ai.TrustAi;
import com.infamous.all_bark_all_bite.common.util.ai.BrainUtil;
import com.infamous.all_bark_all_bite.config.ABABConfig;
import com.infamous.all_bark_all_bite.common.entity.EntityAnimationController;
import com.infamous.all_bark_all_bite.common.entity.SharedWolfAi;
import com.infamous.all_bark_all_bite.common.registry.ABABMemoryModuleTypes;
import com.infamous.all_bark_all_bite.common.util.DebugUtil;
import com.infamous.all_bark_all_bite.common.util.EntityDimensionsUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

public class WolfHooks {
    public static void onWolfJoinLevel(Wolf wolf, boolean loadedFromDisk) {
        wolf.goalSelector.removeAllGoals();
        wolf.targetSelector.removeAllGoals();
        WolfBrain.makeBrain(BrainUtil.getTypedBrain(wolf));
        wolf.getNavigation().setCanFloat(true);
        if(!loadedFromDisk) {
            WolfAi.initMemories(wolf, wolf.getRandom());
        }
    }

    public static void onWolfUpdate(Wolf wolf, ServerLevel level) {
        level.getProfiler().push("wolfBrain");
        BrainUtil.getTypedBrain(wolf).tick(level, wolf);
        level.getProfiler().pop();
        if(AllBarkAllBite.ENABLE_BRAIN_DEBUG){
            DebugUtil.sendEntityBrain(wolf, level,
                    ABABMemoryModuleTypes.FOLLOW_TRIGGER_DISTANCE.get(),
                    ABABMemoryModuleTypes.IS_ALERT.get(),
                    ABABMemoryModuleTypes.IS_FOLLOWING.get(),
                    ABABMemoryModuleTypes.IS_ORDERED_TO_FOLLOW.get(),
                    ABABMemoryModuleTypes.IS_ORDERED_TO_SIT.get(),
                    ABABMemoryModuleTypes.TRUST.get());
        }
    }

    public static void onWolfPupSpawn(Wolf pup, @Nullable Player bredByPlayer) {
        if(bredByPlayer != null){
            TrustAi.setTrust(pup, ABABConfig.wolfStartingTrust.get());
            TrustAi.setLikedPlayer(pup, bredByPlayer);
        }
    }

    public static void onWolfJump(LivingEntity entity) {
        entity.level.broadcastEntityEvent(entity, EntityAnimationController.JUMPING_EVENT_ID);
    }

    public static Brain.Provider<Wolf> getWolfBrainProvider() {
        return Brain.provider(WolfAi.MEMORY_TYPES, WolfAi.SENSOR_TYPES);
    }

    public static EntityDimensions onWolfSize(Entity entity, EntityDimensions originalSize) {
        originalSize = EntityDimensionsUtil.resetIfSleeping(entity, originalSize);
        originalSize = EntityDimensionsUtil.unfixIfNeeded(originalSize);
        EntityDimensions resize = originalSize.scale(ABABConfig.wolfHitboxSizeScale.get().floatValue());
        resize = EntityDimensionsUtil.resizeForLongJumpIfNeeded(entity, resize, SharedWolfAi.LONG_JUMPING_SCALE);
        return resize;
    }

    public static boolean wolfWantsToPickUp(ItemStack stack, Mob wolf) {
        return ForgeEventFactory.getMobGriefingEvent(wolf.level, wolf) && wolf.canPickUpLoot() && SharedWolfAi.isAbleToPickUp(wolf, stack);
    }

    public static boolean canWolfTakeItem(ItemStack stack, Mob wolf, boolean canMobTakeItem) {
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        if (!wolf.getItemBySlot(slot).isEmpty()) {
            return false;
        } else {
            return slot == EquipmentSlot.MAINHAND && canMobTakeItem;
        }
    }

    public static boolean canWolfHoldItem(ItemStack itemStack, Animal wolf) {
        ItemStack itemInMouth = wolf.getMainHandItem();
        return itemInMouth.isEmpty() || wolf.isFood(itemStack) && !wolf.isFood(itemInMouth);
    }

    public static void onWolfPickUpItem(Mob wolf, ItemEntity itemEntity) {
        wolf.onItemPickup(itemEntity);
        SharedWolfAi.pickUpAndHoldItem(wolf, itemEntity);
    }

    public static SoundEvent getWolfEatingSound() {
        return SoundEvents.FOX_EAT;
    }

    public static boolean canWolfMate(TamableAnimal wolf, Animal partner) {
        return partner != wolf
                && partner instanceof Wolf mate
                && SharedWolfAi.canMove(wolf) && SharedWolfAi.canMove(mate)
                && wolf.isInLove() && mate.isInLove();
    }

    @Nullable
    public static SoundEvent getWolfAmbientSound(Wolf wolf) {
        return wolf.level.isClientSide ? null : WolfAi.getSoundForCurrentActivity(wolf).orElse(null);
    }
}
