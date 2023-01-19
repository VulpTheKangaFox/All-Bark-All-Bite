package com.infamous.call_of_the_wild.common.entity.dog;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.infamous.call_of_the_wild.common.ABABTags;
import com.infamous.call_of_the_wild.common.entity.SharedWolfAi;
import com.infamous.call_of_the_wild.common.registry.ABABActivities;
import com.infamous.call_of_the_wild.common.registry.ABABMemoryModuleTypes;
import com.infamous.call_of_the_wild.common.ai.AiUtil;
import com.infamous.call_of_the_wild.common.ai.DigAi;
import com.infamous.call_of_the_wild.common.ai.GenericAi;
import com.infamous.call_of_the_wild.common.registry.ABABSensorTypes;
import com.infamous.call_of_the_wild.common.util.MiscUtil;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.Collection;
import java.util.Optional;

public class DogAi {

    public static final Collection<? extends MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.ANGRY_AT,
            MemoryModuleType.ATTACK_COOLING_DOWN,
            MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.AVOID_TARGET,
            MemoryModuleType.BREED_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.DIG_COOLDOWN,
            ABABMemoryModuleTypes.DIG_LOCATION.get(),
            ABABMemoryModuleTypes.DISABLE_WALK_TO_FETCH_ITEM.get(),
            ABABMemoryModuleTypes.DISABLE_WALK_TO_PLAY_ITEM.get(),
            ABABMemoryModuleTypes.DOG_VIBRATION_LISTENER.get(),
            ABABMemoryModuleTypes.FETCHING_DISABLED.get(),
            ABABMemoryModuleTypes.FETCHING_ITEM.get(),
            //MemoryModuleType.HAS_HUNTING_COOLDOWN,
            MemoryModuleType.HUNTED_RECENTLY,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.INTERACTION_TARGET,
            ABABMemoryModuleTypes.IS_FOLLOWING.get(),
            MemoryModuleType.IS_PANICKING,
            MemoryModuleType.IS_TEMPTED,
            MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
            MemoryModuleType.LOOK_TARGET,
            ABABMemoryModuleTypes.NEAREST_ADULTS.get(),
            ABABMemoryModuleTypes.NEAREST_BABIES.get(),
            ABABMemoryModuleTypes.NEAREST_ALLIES.get(),
            MemoryModuleType.NEAREST_ATTACKABLE,
            MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_PLAYERS,
            MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM,
            MemoryModuleType.NEAREST_VISIBLE_ADULT,
            ABABMemoryModuleTypes.NEAREST_VISIBLE_ADULTS.get(),
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
            ABABMemoryModuleTypes.NEAREST_VISIBLE_BABIES.get(),
            ABABMemoryModuleTypes.NEAREST_VISIBLE_HUNTABLE.get(),
            ABABMemoryModuleTypes.NEAREST_VISIBLE_ALLIES.get(),
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
            MemoryModuleType.PATH,
            ABABMemoryModuleTypes.PLAYING_DISABLED.get(),
            ABABMemoryModuleTypes.PLAYING_WITH_ITEM.get(),
            MemoryModuleType.TEMPTING_PLAYER,
            MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
            ABABMemoryModuleTypes.TIME_TRYING_TO_REACH_FETCH_ITEM.get(),
            ABABMemoryModuleTypes.TIME_TRYING_TO_REACH_PLAY_ITEM.get(),
            MemoryModuleType.UNIVERSAL_ANGER,
            MemoryModuleType.WALK_TARGET
    );
    public static final Collection<? extends SensorType<? extends Sensor<? super Dog>>> SENSOR_TYPES = ImmutableList.of(
            ABABSensorTypes.ANIMAL_TEMPTATIONS.get(),
            SensorType.HURT_BY,

            // dependent on NEAREST_VISIBLE_LIVING_ENTITIES
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_ADULT,
            ABABSensorTypes.NEAREST_ALLIES.get(),
            ABABSensorTypes.DOG_SPECIFIC_SENSOR.get(),
            ABABSensorTypes.DOG_VIBRATION_SENSOR.get(),

            SensorType.NEAREST_ITEMS,
            SensorType.NEAREST_PLAYERS
    );

    public static Brain<?> makeBrain(Brain<Dog> brain) {
        initCoreActivity(brain);
        initFightActivity(brain);
        initRetreatActivity(brain);
        initDiggingActivity(brain);
        initFetchActivity(brain);
        initIdleActivity(brain);
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<Dog> brain) {
        brain.addActivityWithConditions(Activity.CORE,
                DogGoalPackages.getCorePackage(),
                ImmutableSet.of());
    }

    private static void initFightActivity(Brain<Dog> brain) {
        brain.addActivityAndRemoveMemoriesWhenStopped(Activity.FIGHT,
                DogGoalPackages.getFightPackage(),
                ImmutableSet.of(
                        Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)
                ),
                ImmutableSet.of(MemoryModuleType.ATTACK_TARGET));
    }

    private static void initRetreatActivity(Brain<Dog> brain) {
        brain.addActivityAndRemoveMemoriesWhenStopped(Activity.AVOID,
                DogGoalPackages.getAvoidPackage(),
                ImmutableSet.of(
                        Pair.of(MemoryModuleType.AVOID_TARGET, MemoryStatus.VALUE_PRESENT)
                ),
                ImmutableSet.of(MemoryModuleType.AVOID_TARGET));
    }

    private static void initDiggingActivity(Brain<Dog> brain) {
        brain.addActivityAndRemoveMemoriesWhenStopped(Activity.DIG,
                DogGoalPackages.getDigPackage(),
                ImmutableSet.of(
                        Pair.of(ABABMemoryModuleTypes.DIG_LOCATION.get(), MemoryStatus.VALUE_PRESENT)
                ),
                ImmutableSet.of(ABABMemoryModuleTypes.DIG_LOCATION.get()));
    }

    private static void initFetchActivity(Brain<Dog> brain) {
        brain.addActivityAndRemoveMemoriesWhenStopped(ABABActivities.FETCH.get(),
                DogGoalPackages.getFetchPackage(),
                ImmutableSet.of(
                        Pair.of(ABABMemoryModuleTypes.FETCHING_ITEM.get(), MemoryStatus.VALUE_PRESENT)
                ),
                ImmutableSet.of(ABABMemoryModuleTypes.FETCHING_ITEM.get()));
    }

    private static void initIdleActivity(Brain<Dog> brain) {
        brain.addActivity(Activity.IDLE, DogGoalPackages.getIdlePackage());
    }

    /**
     * Called by {@link Dog#mobInteract(Player, InteractionHand)}
     */
    public static InteractionResult mobInteract(Dog dog, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();
        Level level = dog.level;

        if(dog.isTame()){
            if (!(item instanceof DyeItem dyeItem)) {
                if(DogGoalPackages.canBury(stack) && !AiUtil.hasAnyMemory(dog, ABABMemoryModuleTypes.DIG_LOCATION.get(), MemoryModuleType.DIG_COOLDOWN)){
                    Optional<BlockPos> digLocation = generateDigLocation(dog);
                    if(digLocation.isPresent()){
                        yieldAsPet(dog);
                        DigAi.setDigLocation(dog, digLocation.get());
                        ItemStack singleton = stack.split(1);
                        holdInMouth(dog, singleton);
                        return InteractionResult.CONSUME;
                    } else{
                        return InteractionResult.PASS;
                    }
                }

                if(dog.isFood(stack) && AiUtil.isInjured(dog)){
                    dog.usePlayerItem(player, hand, stack);
                    return InteractionResult.CONSUME;
                }

                InteractionResult animalInteractResult = dog.animalInteract(player, hand);
                boolean willNotBreed = !animalInteractResult.consumesAction() || dog.isBaby();
                if (willNotBreed && dog.isOwnedBy(player)) {
                    dog.setOrderedToSit(!dog.isOrderedToSit());
                    dog.setJumping(false);
                    yieldAsPet(dog);
                    setFollowing(dog);
                    return InteractionResult.CONSUME;
                }

                return animalInteractResult;
            } else{
                DyeColor dyecolor = dyeItem.getDyeColor();
                if (dyecolor != dog.getCollarColor()) {
                    dog.setCollarColor(dyecolor);
                    dog.usePlayerItem(player, hand, stack);

                    return InteractionResult.CONSUME;
                }
            }
        } else if(dog.isFood(stack) && !dog.isAggressive()){
            dog.usePlayerItem(player, hand, stack);
            if (MiscUtil.oneInChance(dog.getRandom(), 3) && !ForgeEventFactory.onAnimalTame(dog, player)) {
                dog.tame(player);
                dog.setOrderedToSit(true);
                yieldAsPet(dog);
                setFollowing(dog);
                level.broadcastEntityEvent(dog, SharedWolfAi.SUCCESSFUL_TAME_ID);
            } else {
                level.broadcastEntityEvent(dog, SharedWolfAi.FAILED_TAME_ID);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static void setFollowing(Dog dog) {
        dog.getBrain().setMemory(ABABMemoryModuleTypes.IS_FOLLOWING.get(), Unit.INSTANCE);
    }

    public static void yieldAsPet(Dog dog) {
        GenericAi.stopWalking(dog);

        AiUtil.setItemPickupCooldown(dog, DogGoalPackages.ITEM_PICKUP_COOLDOWN);

        AiUtil.eraseAllMemories(dog,
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleType.ANGRY_AT,
                MemoryModuleType.UNIVERSAL_ANGER,
                MemoryModuleType.AVOID_TARGET,
                ABABMemoryModuleTypes.FETCHING_ITEM.get(),
                ABABMemoryModuleTypes.DIG_LOCATION.get());
    }

    public static void holdInMouth(Dog dog, ItemStack stack) {
        if (dog.hasItemInMouth()) {
            DogGoalPackages.stopHoldingItemInMouth(dog);
        }

        dog.holdInMouth(stack);
    }

    /**
     * Called by {@link Dog#customServerAiStep()}
     */
    public static void updateActivity(Dog dog) {
        Brain<Dog> brain = dog.getBrain();
        Activity previous = brain.getActiveNonCoreActivity().orElse(null);
        brain.setActiveActivityToFirstValid(ImmutableList.of(Activity.FIGHT, Activity.AVOID, Activity.DIG, ABABActivities.FETCH.get(), Activity.IDLE));
        Activity current = brain.getActiveNonCoreActivity().orElse(null);

        if (previous != current) {
            getSoundForCurrentActivity(dog).ifPresent(dog::playSoundEvent);
        }

        dog.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
        dog.setSprinting(canSprint(dog));
    }

    private static boolean canSprint(Dog dog) {
        return AiUtil.hasAnyMemory(dog,
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleType.AVOID_TARGET,
                MemoryModuleType.IS_PANICKING,
                ABABMemoryModuleTypes.FETCHING_ITEM.get(),
                ABABMemoryModuleTypes.DIG_LOCATION.get());
    }

    public static Optional<SoundEvent> getSoundForCurrentActivity(Dog dog) {
        return dog.getBrain().getActiveNonCoreActivity().map((a) -> getSoundForActivity(dog, a));
    }

    private static SoundEvent getSoundForActivity(Dog dog, Activity activity) {
        if (activity == Activity.FIGHT) {
            return SoundEvents.WOLF_GROWL;
        } else if (activity == Activity.AVOID && GenericAi.isNearAvoidTarget(dog, SharedWolfAi.DESIRED_DISTANCE_FROM_DISLIKED)) {
            return SoundEvents.WOLF_HURT;
        } else if (MiscUtil.oneInChance(dog.getRandom(), 3)) {
            return dog.isTame() && dog.getHealth() < dog.getMaxHealth() * 0.5F ? SoundEvents.WOLF_WHINE : SoundEvents.WOLF_PANT;
        } else {
            return SoundEvents.WOLF_AMBIENT;
        }
    }

    /**
     * Called by {@link Dog#wantsToPickUp(ItemStack)}
     */
    public static boolean wantsToPickup(Dog dog, ItemStack stack) {
        if (AiUtil.hasAnyMemory(dog,
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleType.AVOID_TARGET,
                MemoryModuleType.IS_PANICKING,
                MemoryModuleType.BREED_TARGET)) {
            return false;
        } else if (DogGoalPackages.canFetch(stack)) {
            return DogGoalPackages.isNotHoldingItem(dog) && dog.isTame();
        }
        return false;
    }

    /**
     * Called by {@link Dog#pickUpItem(ItemEntity)}
     */
    public static void pickUpItem(Dog dog, ItemEntity itemEntity) {
        dog.take(itemEntity, 1);
        ItemStack singleton = MiscUtil.removeOneItemFromItemEntity(itemEntity);
        holdInMouth(dog, singleton);
    }

    public static Optional<BlockPos> generateDigLocation(Dog dog){
        Vec3 randomPos = LandRandomPos.getPos(dog, 10, 7);
        if(randomPos == null) return Optional.empty();

        BlockPos blockPos = new BlockPos(randomPos);
        return Optional.of(blockPos).filter(bp -> dog.level.getBlockState(bp.below()).is(ABABTags.DOG_CAN_DIG));
    }

    public static void commandCome(Dog dog) {
        dog.setOrderedToSit(false);
        DogAi.yieldAsPet(dog);
        setFollowing(dog);
    }

    public static void commandGo(Dog dog) {
        dog.setOrderedToSit(false);
        stopFollowing(dog);
    }

    private static void stopFollowing(Dog dog) {
        dog.getBrain().eraseMemory(ABABMemoryModuleTypes.IS_FOLLOWING.get());
    }

    public static void commandSit(Dog dog) {
        dog.setOrderedToSit(true);
        dog.setJumping(false);
        DogAi.yieldAsPet(dog);
    }
}
