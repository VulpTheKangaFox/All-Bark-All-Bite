package com.infamous.call_of_the_wild.common.entity.dog;

import com.infamous.call_of_the_wild.common.entity.*;
import com.infamous.call_of_the_wild.common.registry.COTWDogVariants;
import com.infamous.call_of_the_wild.common.registry.COTWEntityDataSerializers;
import com.infamous.call_of_the_wild.common.registry.COTWEntityTypes;
import com.infamous.call_of_the_wild.common.util.AiHelper;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.IForgeRegistry;
import org.apache.commons.lang3.tuple.MutablePair;
import org.jetbrains.annotations.Nullable;

public class Dog extends TamableAnimal implements InterestedMob, ShakingMob<Dog>, VariantMob, CollaredMob {
    private static final EntityDataAccessor<Boolean> DATA_INTERESTED_ID = SynchedEntityData.defineId(Dog.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<EntityVariant> DATA_VARIANT_ID = SynchedEntityData.defineId(Dog.class, COTWEntityDataSerializers.DOG_VARIANT.get());
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR = SynchedEntityData.defineId(Dog.class, EntityDataSerializers.INT);
    private static final byte JUMPING_ID = (byte) 1;
    private boolean isWet;
    private boolean isShaking;
    private final MutablePair<Float, Float> shakeAnims = new MutablePair<>(0.0F, 0.0F);
    private final MutablePair<Float, Float> interestedAngles = new MutablePair<>(0.0F, 0.0F);

    public final AnimationState babyAnimationState = new AnimationState();
    public final AnimationState walkAnimationState = new AnimationState();
    public final AnimationState runAnimationState = new AnimationState();
    public final AnimationState jumpAnimationState = new AnimationState();
    public final AnimationState sitAnimationState = new AnimationState();
    private int jumpTicks;
    private int jumpDuration;

    public Dog(EntityType<? extends Dog> type, Level level) {
        super(type, level);
        this.setTame(false);
        this.setPathfindingMalus(BlockPathTypes.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_POWDER_SNOW, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, (double)0.3F)
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    public boolean isAdult(){
        return !this.isBaby();
    }

    public boolean isWild(){
        return !this.isTame();
    }

    public boolean isMobile(){
        return !this.isOrderedToSit();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_INTERESTED_ID, false);
        this.entityData.define(DATA_VARIANT_ID, COTWDogVariants.BROWN.get());
        this.entityData.define(DATA_COLLAR_COLOR, DyeColor.RED.getId());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.addVariantSaveData(tag);
        this.addCollarColorSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.readVariantSaveData(tag);
        this.readCollarColorSaveData(tag);
    }

    @Override
    protected Brain.Provider<Dog> brainProvider() {
        return Brain.provider(DogAi.MEMORY_TYPES, DogAi.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return DogAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public Brain<Dog> getBrain() {
        return (Brain<Dog>)super.getBrain();
    }

    private boolean isMovingOnLandOrInWater() {
        return this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D && (this.onGround || this.isInWaterOrBubble());
    }
    @Override
    public void tick() {
        if (this.level.isClientSide()) {
            if(this.isBaby()){
                this.babyAnimationState.startIfStopped(this.tickCount);
            } else{
                this.babyAnimationState.stop();
            }

            boolean midJump = this.jumpDuration != 0;
            if(!midJump && this.jumpAnimationState.isStarted()) this.jumpAnimationState.stop();

            if(!this.isInSittingPose()){
                if (this.isMovingOnLandOrInWater() && !midJump) {
                    if(this.isSprinting()){
                        this.walkAnimationState.stop();
                        this.runAnimationState.startIfStopped(this.tickCount);
                    } else{
                        this.runAnimationState.stop();
                        this.walkAnimationState.startIfStopped(this.tickCount);
                    }
                } else {
                    this.walkAnimationState.stop();
                    this.runAnimationState.stop();
                }
            }
        }

        super.tick();
        if(this.isAlive()){
            this.tickInterest();
            this.tickShaking(this);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        if(dataAccessor == DATA_FLAGS_ID  && this.level.isClientSide){
            if(this.isInSittingPose()){
                this.walkAnimationState.stop();
                this.runAnimationState.stop();
                this.jumpAnimationState.stop();
                this.sitAnimationState.startIfStopped(this.tickCount);
            } else{
                this.sitAnimationState.stop();
            }
        }
    }

    @Override
    protected void jumpFromGround() {
        super.jumpFromGround();
        if (!this.level.isClientSide) {
            this.level.broadcastEntityEvent(this, JUMPING_ID);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.aiStepShaking(this);
        if (this.jumpTicks != this.jumpDuration) {
            ++this.jumpTicks;
        } else if (this.jumpDuration != 0) {
            this.jumpTicks = 0;
            this.jumpDuration = 0;
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.dieShaking();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if(id == JUMPING_ID){
            this.jumpAnimationState.startIfStopped(this.tickCount);
            this.jumpDuration = 10; // half a second, which is the same length as the jump animation
            this.jumpTicks = 0;
        }
        else if(!this.handleShakingEvent(id))  super.handleEntityEvent(id);
    }

    @Override
    protected void customServerAiStep() {
        this.level.getProfiler().push("dogBrain");
        this.getBrain().tick((ServerLevel)this.level, this);
        this.level.getProfiler().pop();
        this.level.getProfiler().push("dogActivityUpdate");
        DogAi.updateActivity(this);
        this.level.getProfiler().pop();
        super.customServerAiStep();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.level.isClientSide) {
            boolean canInteract = this.isOwnedBy(player)
                    || this.isTame()
                    || this.isInteresting(stack) && !this.isTame() && !this.isAggressive();
            return canInteract ? InteractionResult.CONSUME : InteractionResult.PASS;
        } else {
            return DogAi.mobInteract(this, player, hand, () -> super.mobInteract(player, hand));
        }
    }

    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        if (this.isFood(stack)) {
            this.playSound(this.getEatingSound(stack), 1.0F, 1.0F);

            float healAmount = 1.0F;
            FoodProperties foodProperties = stack.getFoodProperties(this);
            if(foodProperties != null){
                healAmount = foodProperties.getNutrition();
                AiHelper.addEatEffect(this, level, foodProperties);
            }
            if(this.getHealth() < this.getMaxHealth()) this.heal(healAmount);

            this.gameEvent(GameEvent.EAT, this);
        }

        super.usePlayerItem(player, hand, stack);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            Entity sourceEntity = source.getEntity();
            if (!this.level.isClientSide) {
                this.setOrderedToSit(false);
            }

            // for some reason, vanilla Wolves take reduced damage from non-players and non-arrows
            if (sourceEntity != null && !(sourceEntity instanceof Player) && !(sourceEntity instanceof AbstractArrow)) {
                amount = (amount + 1.0F) / 2.0F;
            }

            boolean wasHurt = super.hurt(source, amount);

            if (this.level.isClientSide) {
                return false;
            } else {
                if (wasHurt && sourceEntity instanceof LivingEntity attacker) {
                    DogAi.wasHurtBy(this, attacker);
                }

                return wasHurt;
            }
        }
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Dog offspring = COTWEntityTypes.DOG.get().create(level);
        if (partner instanceof Dog mate && offspring != null) {
            if (this.random.nextBoolean()) {
                offspring.setVariant(this.getVariant());
            } else {
                offspring.setVariant(mate.getVariant());
            }

            if (this.isTame()) {
                offspring.setOwnerUUID(this.getOwnerUUID());
                offspring.setTame(true);
                if (this.random.nextBoolean()) {
                    offspring.setCollarColor(this.getCollarColor());
                } else {
                    offspring.setCollarColor(mate.getCollarColor());
                }
            }
        }

        return offspring;
    }

    @Override
    public boolean canMate(Animal partner) {
        if (partner == this) {
            return false;
        } else if (!this.isTame()) {
            return false;
        } else if (!(partner instanceof Dog mate)) {
            return false;
        } else {
            if (!mate.isTame()) {
                return false;
            } else if (mate.isInSittingPose()) {
                return false;
            } else {
                return this.isInLove() && mate.isInLove();
            }
        }
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (!(target instanceof Creeper) && !(target instanceof Ghast)) {
            if (target instanceof Dog dog) {
                return !dog.isTame() || dog.getOwner() != owner;
            } else if (target instanceof Player targetPlayer && owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(targetPlayer)) {
                return false;
            } else if (target instanceof AbstractHorse horse && horse.isTamed()) {
                return false;
            } else {
                return !(target instanceof TamableAnimal tamable) || !tamable.isTame();
            }
        } else {
            return false;
        }
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height * 0.8F;
    }

    @Override
    public int getMaxHeadXRot() {
        return this.isInSittingPose() ? 20 : super.getMaxHeadXRot();
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return !this.isAggressive() && super.canBeLeashed(player);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double)(0.6F * this.getEyeHeight()), (double)(this.getBbWidth() * 0.4F));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        FoodProperties foodProperties = stack.getFoodProperties(this);
        return DogAi.isFood(stack) || foodProperties != null && foodProperties.isMeat();
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 8;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.level.isClientSide ? null : DogAi.getSoundForCurrentActivity(this).orElse((SoundEvent)null);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.WOLF_STEP, 0.15F, 1.0F);
    }

    protected void playSoundEvent(SoundEvent soundEvent) {
        this.playSound(soundEvent, this.getSoundVolume(), this.getVoicePitch());
    }

    // VariantMob

    @Override
    public IForgeRegistry<EntityVariant> getVariantRegistry() {
        return COTWDogVariants.DOG_VARIANT_REGISTRY.get();
    }

    @Override
    public EntityVariant getVariant() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    @Override
    public void setVariant(EntityVariant variant) {
        this.entityData.set(DATA_VARIANT_ID, variant);
    }

    // CollaredMob

    @Override
    public DyeColor getCollarColor() {
        return DyeColor.byId(this.entityData.get(DATA_COLLAR_COLOR));
    }

    @Override
    public void setCollarColor(DyeColor collarColor) {
        this.entityData.set(DATA_COLLAR_COLOR, collarColor.getId());
    }

    // ShakingMob

    @Override
    public boolean isWet() {
        return this.isWet;
    }

    @Override
    public void setIsWet(boolean isWet) {
        this.isWet = isWet;
    }

    @Override
    public boolean isShaking() {
        return this.isShaking;
    }

    @Override
    public void setIsShaking(boolean isShaking) {
        this.isShaking = isShaking;
    }

    @Override
    public MutablePair<Float, Float> getShakeAnims() {
        return this.shakeAnims;
    }

    @Override
    public void playShakeSound() {
        this.playSound(SoundEvents.WOLF_SHAKE, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    // InterestedMob

    @Override
    public boolean isInterested() {
        return this.entityData.get(DATA_INTERESTED_ID);
    }

    @Override
    public void setIsInterested(boolean isInterested) {
        this.entityData.set(DATA_INTERESTED_ID, isInterested);
    }

    @Override
    public MutablePair<Float, Float> getInterestedAngles() {
        return this.interestedAngles;
    }

    @Override
    public boolean isInteresting(ItemStack stack) {
        return DogAi.isLoved(stack);
    }
}