package com.infamous.call_of_the_wild.common.ai;

import com.infamous.call_of_the_wild.common.util.ReflectionUtil;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.ForgeEventFactory;
import org.apache.commons.lang3.function.TriFunction;

import java.util.Optional;
import java.util.UUID;

public class AiUtil {

    private static final String LIVING_ENTITY_GET_SOUND_VOLUME = "m_6121_";

    public static void addEatEffect(LivingEntity eater, Level level, FoodProperties foodProperties) {
        for(Pair<MobEffectInstance, Float> pair : foodProperties.getEffects()) {
            if (!level.isClientSide && pair.getFirst() != null && level.random.nextFloat() < pair.getSecond()) {
                eater.addEffect(new MobEffectInstance(pair.getFirst()));
            }
        }
    }

    public static boolean hasAnyMemory(LivingEntity livingEntity, MemoryModuleType<?>... memoryModuleTypes){
        Brain<?> brain = livingEntity.getBrain();
        for(MemoryModuleType<?> memoryModuleType : memoryModuleTypes){
            if(brain.hasMemoryValue(memoryModuleType)) return true;
        }
        return false;
    }

    public static void eraseMemories(LivingEntity livingEntity, MemoryModuleType<?>... memoryModuleTypes) {
        Brain<?> brain = livingEntity.getBrain();
        for (MemoryModuleType<?> memoryModuleType : memoryModuleTypes) {
            brain.eraseMemory(memoryModuleType);
        }
    }

    public static boolean isClose(Mob mob, LivingEntity target, int closeEnough) {
        return target.distanceToSqr(mob) <= Mth.square(closeEnough);
    }

    public static boolean isAttackable(Mob mob, LivingEntity target, boolean requireLineOfSight){
        return requireLineOfSight ? Sensor.isEntityAttackable(mob, target) : Sensor.isEntityAttackableIgnoringLineOfSight(mob, target);
    }

    public static boolean isHuntableBabyTurtle(Mob mob, LivingEntity target, int closeEnough, boolean requireLineOfSight) {
        return isClose(mob, target, closeEnough)
                && target instanceof Turtle turtle
                && Turtle.BABY_ON_LAND_SELECTOR.test(turtle)
                && isAttackable(mob, turtle, requireLineOfSight);
    }

    public static boolean canBeConsideredAnAlly(LivingEntity mob, LivingEntity other) {
        return mob.isAlliedTo(other)
                || mob.getType() == other.getType() && mob.getTeam() == null && other.getTeam() == null;
    }

    public static Optional<LivingEntity> getLivingEntityFromUUID(ServerLevel level, UUID uuid) {
        return Optional.of(uuid).map(level::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isLookingAtMe(LivingEntity me, LivingEntity target, double offset) {
        Vec3 viewVector = target.getViewVector(1.0F).normalize();
        Vec3 eyeToMyEyeVector = target.getEyePosition().vectorTo(me.getEyePosition());
        double distance = eyeToMyEyeVector.length();
        eyeToMyEyeVector = eyeToMyEyeVector.normalize();
        double dot = viewVector.dot(eyeToMyEyeVector);
        return dot > 1.0D - offset / distance && target.hasLineOfSight(me);
    }

    public static <T extends Entity> InteractionResult interactOn(Player player, T entity, InteractionHand hand, TriFunction<T, Player, InteractionHand, InteractionResult> interactCallback){
        ItemStack itemInHand = player.getItemInHand(hand);
        ItemStack itemInHandCopy = itemInHand.copy();
        InteractionResult interactionResult = interactCallback.apply(entity, player, hand); // replaces Entity#interact(Player, InteractionHand);
        if (interactionResult.consumesAction()) {
            if (player.getAbilities().instabuild && itemInHand == player.getItemInHand(hand) && itemInHand.getCount() < itemInHandCopy.getCount()) {
                itemInHand.setCount(itemInHandCopy.getCount());
            }

            if (!player.getAbilities().instabuild && itemInHand.isEmpty()) {
                ForgeEventFactory.onPlayerDestroyItem(player, itemInHandCopy, hand);
            }
            return interactionResult;
        } else {
            if (!itemInHand.isEmpty() && entity instanceof LivingEntity) {
                if (player.getAbilities().instabuild) {
                    itemInHand = itemInHandCopy;
                }

                InteractionResult interactLivingEntity = itemInHand.interactLivingEntity(player, (LivingEntity)entity, hand);
                if (interactLivingEntity.consumesAction()) {
                    if (itemInHand.isEmpty() && !player.getAbilities().instabuild) {
                        ForgeEventFactory.onPlayerDestroyItem(player, itemInHandCopy, hand);
                        player.setItemInHand(hand, ItemStack.EMPTY);
                    }

                    return interactLivingEntity;
                }
            }

            return InteractionResult.PASS;
        }
    }

    public static boolean isInjured(LivingEntity livingEntity){
        return livingEntity.getHealth() < livingEntity.getMaxHealth();
    }

    public static void animalEat(Animal animal, ItemStack stack, float soundVolume) {
        if (animal.isFood(stack) && !animal.level.isClientSide) {
            playSoundEvent(animal, animal.getEatingSound(stack), soundVolume);

            float healAmount = 1.0F;
            FoodProperties foodProperties = stack.getFoodProperties(animal);
            if(foodProperties != null){
                healAmount = foodProperties.getNutrition();
                addEatEffect(animal, animal.level, foodProperties);
            }
            if(isInjured(animal)) animal.heal(healAmount);

            animal.ate();
        }
    }

    public static void playSoundEvent(LivingEntity livingEntity, SoundEvent soundEvent, float soundVolume) {
        livingEntity.playSound(soundEvent, soundVolume, livingEntity.getVoicePitch());
    }

    @SuppressWarnings("ConstantConditions")
    public static float getSoundVolume(LivingEntity livingEntity) {
        return ReflectionUtil.callMethod(LIVING_ENTITY_GET_SOUND_VOLUME, livingEntity);
    }

    public static void setWalkAndLookTargetMemories(LivingEntity mob, Entity target, float speedModifier, int closeEnough) {
        PositionTracker lookTarget = new EntityTracker(target, true);
        WalkTarget walkTarget = new WalkTarget(target, speedModifier, closeEnough);
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, lookTarget);
        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
    }

    public static void setWalkAndLookTargetMemories(LivingEntity mob, BlockPos target, float speedModifier, int closeEnough) {
        PositionTracker lookTarget = new BlockPosTracker(target);
        WalkTarget walkTarget = new WalkTarget(target, speedModifier, closeEnough);
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, lookTarget);
        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
    }

    public static boolean onCheckCooldown(ServerLevel level, long lastCheckTimestamp, long checkCooldown) {
        long ticksSinceLastCheck = level.getGameTime() - lastCheckTimestamp;
        return lastCheckTimestamp != 0 && ticksSinceLastCheck > 0 && ticksSinceLastCheck < checkCooldown;
    }

    public static void setItemPickupCooldown(LivingEntity mob, int itemPickupCooldownTicks) {
        mob.getBrain().setMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, itemPickupCooldownTicks);
    }

    public static Optional<LivingEntity> getLivingEntityFromId(Level level, int id) {
        return Optional.ofNullable(level.getEntity(id)).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast);
    }

    public static Optional<LivingEntity> getOwner(OwnableEntity ownableEntity) {
        return Optional.ofNullable(ownableEntity.getOwner()).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast);
    }

    public static Optional<LivingEntity> getTarget(LivingEntity livingEntity){
        return livingEntity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) ?
                livingEntity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET) :
                Optional.ofNullable(livingEntity instanceof Mob mob ? mob.getTarget() : null);
    }

    public static Optional<Entity> getTargetedEntity(Entity looker, int distance) {
        Vec3 eyePosition = looker.getEyePosition();
        Vec3 viewVector = looker.getViewVector(1.0F).scale(distance);
        Vec3 targetPosition = eyePosition.add(viewVector);
        AABB searchBox = looker.getBoundingBox().expandTowards(viewVector).inflate(1.0D);
        int distanceSquared = distance * distance;
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(looker, eyePosition, targetPosition, searchBox, AiUtil::canHitEntity, distanceSquared);
        if (entityHitResult == null) {
            return Optional.empty();
        } else {
            return eyePosition.distanceToSqr(entityHitResult.getLocation()) > (double)distanceSquared ? Optional.empty() : Optional.of(entityHitResult.getEntity());
        }
    }

    private static boolean canHitEntity(Entity entity) {
        return !entity.isSpectator() && entity.isPickable();
    }

    public static HitResult getHitResult(Entity looker, int distance){
        Vec3 eyePosition = looker.getEyePosition();
        Vec3 viewVector = looker.getViewVector(1.0F).scale(distance);
        Vec3 targetPosition = eyePosition.add(viewVector);

        HitResult hitResult = looker.level.clip(new ClipContext(eyePosition, targetPosition, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker));
        if (hitResult.getType() != HitResult.Type.MISS) {
            targetPosition = hitResult.getLocation();
        }
        AABB searchBox = looker.getBoundingBox().expandTowards(viewVector).inflate(1.0D);
        int distanceSquared = distance * distance;
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(looker, eyePosition, targetPosition, searchBox, AiUtil::canHitEntity, distanceSquared);

        if (entityHitResult != null) {
            hitResult = entityHitResult;
        }

        return hitResult;
    }
}