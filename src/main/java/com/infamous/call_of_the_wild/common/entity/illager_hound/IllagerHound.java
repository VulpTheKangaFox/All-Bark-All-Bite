package com.infamous.call_of_the_wild.common.entity.illager_hound;

import com.google.common.collect.ImmutableList;
import com.infamous.call_of_the_wild.common.entity.HasOwner;
import com.infamous.call_of_the_wild.common.ai.AiUtil;
import com.infamous.call_of_the_wild.common.util.DebugUtil;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Team;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("NullableProblems")
public class IllagerHound extends Monster implements HasOwner, IEntityAdditionalSpawnData {
    protected static final EntityDataAccessor<Optional<UUID>> DATA_OWNERUUID_ID = SynchedEntityData.defineId(IllagerHound.class, EntityDataSerializers.OPTIONAL_UUID);

    protected static final ImmutableList<? extends SensorType<? extends Sensor<? super IllagerHound>>> SENSOR_TYPES =
            ImmutableList.of(
                    SensorType.NEAREST_LIVING_ENTITIES,
                    SensorType.NEAREST_PLAYERS
            );
    protected static final ImmutableList<? extends MemoryModuleType<?>> MEMORY_TYPES =
            ImmutableList.of(
                    MemoryModuleType.NEAREST_LIVING_ENTITIES,
                    MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    MemoryModuleType.NEAREST_VISIBLE_PLAYER,
                    MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
                    MemoryModuleType.LOOK_TARGET,
                    MemoryModuleType.WALK_TARGET,
                    MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                    MemoryModuleType.PATH,
                    MemoryModuleType.ATTACK_TARGET,
                    MemoryModuleType.ATTACK_COOLING_DOWN
            );
    @Nullable
    private LivingEntity cachedOwner;

    public IllagerHound(EntityType<? extends IllagerHound> entityType, Level level) {
        super(entityType, level);
        this.xpReward = Enemy.XP_REWARD_MEDIUM;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNERUUID_ID, Optional.empty());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.writeOwnerNBT(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.readOwnerNBT(tag);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isOwnedBy(target) && super.canAttack(target);
    }

    @Override
    public Team getTeam() {
        return this.getOwnerTeam().orElse(super.getTeam());
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return this.isOwnerAlliedTo(other) || super.isAlliedTo(other);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected Brain.Provider<IllagerHound> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        Brain<IllagerHound> brain = this.brainProvider().makeBrain(dynamic);
        IllagerHoundAi.makeBrain(brain);
        return brain;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<IllagerHound> getBrain() {
        return (Brain<IllagerHound>) super.getBrain();
    }

    @Override
    protected void customServerAiStep() {
        this.level.getProfiler().push("houndBrain");
        this.getBrain().tick((ServerLevel)this.level, this);
        this.level.getProfiler().pop();
        this.level.getProfiler().push("houndActivityUpdate");
        IllagerHoundAi.updateActivity(this);
        this.level.getProfiler().pop();
        super.customServerAiStep();
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendEntityBrain(this);
        if(this.level instanceof ServerLevel serverLevel){
            DebugUtil.sendEntityBrain(this, serverLevel);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (this.level.isClientSide) {
            return null;
        } else {
            return this.brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET) ? SoundEvents.WOLF_GROWL : SoundEvents.WOLF_AMBIENT;
        }
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
    protected void playStepSound(BlockPos blockPos, BlockState blockState) {
        this.playSound(SoundEvents.WOLF_STEP, 0.15F, 1.0F);
    }

    protected void playAngrySound() {
        this.playSound(SoundEvents.WOLF_GROWL, this.getSoundVolume(), this.getVoicePitch());
    }

    // OwnableMob

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNERUUID_ID).orElse(null);
    }

    @Override
    public void setOwnerUUID(@Nullable UUID ownerUUID) {
        this.entityData.set(DATA_OWNERUUID_ID, Optional.ofNullable(ownerUUID));
    }

    @Override
    public void setOwner(LivingEntity owner) {
        this.setOwnerUUID(owner.getUUID());
        this.cachedOwner = owner;
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        if (this.cachedOwner != null && !this.cachedOwner.isRemoved()) {
            return this.cachedOwner;
        } else if (this.getOwnerUUID() != null && this.level instanceof ServerLevel serverLevel) {
            this.cachedOwner = AiUtil.getLivingEntityFromUUID(serverLevel, this.getOwnerUUID()).orElse(null);
            return this.cachedOwner;
        } else {
            return null;
        }
    }

    // IEntityAdditionalSpawnData

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        LivingEntity owner = this.getOwner();
        buffer.writeInt(owner != null ? owner.getId() : 0);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf additionalData) {
        int ownerId = additionalData.readInt();
        this.cachedOwner = AiUtil.getLivingEntityFromId(this.level, ownerId).orElse(null);
    }

    public float getTailAngle() {
        if (this.isAggressive()) {
            return (float) (49F * Math.PI / 100F);
        } else {
            return ((float)Math.PI / 5F);
        }
    }
}
