package com.liarsbar.entity;

import com.liarsbar.table.TableManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Badan bot di kursi kosong: model pemain dengan skin bawaan, duduk dan
 * menghadap pusat meja. Keputusannya dibuat server ({@link com.liarsbar.game.bot.DeckBot}).
 */
public class BotEntity extends LivingEntity {

    private static final EntityDataAccessor<Integer> TABLE_ID =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.INT);
    /** Skin bawaan Minecraft 1.20.1 (textures/entity/player/wide/...). */
    public static final String[] SKIN_NAMES = {"steve", "alex", "ari", "kai", "noor", "sunny", "zuri", "efe", "makena"};

    /** Indeks ke {@link #SKIN_NAMES}. */
    private static final EntityDataAccessor<Integer> SKIN =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.INT);
    /** Kursi di meja (indeks urutan giliran). */
    private static final EntityDataAccessor<Integer> SEAT =
            SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.INT);

    public BotEntity(EntityType<? extends BotEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TABLE_ID, -1);
        entityData.define(SKIN, 0);
        entityData.define(SEAT, -1);
    }

    public int seat() {
        return entityData.get(SEAT);
    }

    public void setSeat(int seat) {
        entityData.set(SEAT, seat);
    }

    public int tableId() {
        return entityData.get(TABLE_ID);
    }

    public void setTableId(int id) {
        entityData.set(TABLE_ID, id);
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public void setSkin(int skin) {
        entityData.set(SKIN, skin);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && TableManager.get(tableId()) == null) {
            discard();
            return;
        }
        // Duduk tegak menghadap pusat meja (arah kursinya).
        if (getVehicle() != null) {
            float yaw = getVehicle().getYRot();
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }

    @Override
    public double getMyRidingOffset() {
        return -0.35;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
    }
}
