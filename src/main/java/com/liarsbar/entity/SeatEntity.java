package com.liarsbar.entity;

import com.liarsbar.table.TableManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Kursi: entity tak terlihat yang ditunggangi pemain atau bot, menghadap
 * pusat meja (yaw entity ini). Turun dari kursi selama game dicegah
 * {@link TableManager}. Badan penunggang dikunci menghadap meja; yang bisa
 * menoleh hanya kepala, sejauh {@link #HEAD_TURN} derajat (seperti perahu vanilla).
 */
public class SeatEntity extends Entity {

    private static final EntityDataAccessor<Integer> TABLE_ID =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.INT);

    /**
     * Tinggi titik duduk di atas lantai, ditambah offset tunggangan penunggang (pemain -0,35): pinggul
     * (0,75 blok di atas kaki model) pas di dudukan kursi (0,375) ditambah tebal paha.
     */
    public static final double RIDE_OFFSET = 0.02;
    /** Batas toleh kepala dari arah badan (derajat). */
    public static final float HEAD_TURN = 85f;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TABLE_ID, -1);
    }

    public int tableId() {
        return entityData.get(TABLE_ID);
    }

    public void setTableId(int id) {
        entityData.set(TABLE_ID, id);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && TableManager.get(tableId()) == null) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return RIDE_OFFSET;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        super.positionRider(passenger, move);
        lockBody(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        lockBody(passenger);
    }

    /** Badan tetap menghadap arah kursi; pandangan (kepala) dijepit di dalam batas toleh. */
    private void lockBody(Entity passenger) {
        float seatYaw = getYRot();
        if (passenger instanceof LivingEntity living) {
            living.setYBodyRot(seatYaw);
            living.yBodyRotO = seatYaw;
        }
        float rel = Mth.wrapDegrees(passenger.getYRot() - seatYaw);
        float clamped = Mth.clamp(rel, -HEAD_TURN, HEAD_TURN);
        if (clamped != rel) {
            passenger.yRotO += clamped - rel;
            passenger.setYRot(passenger.getYRot() + clamped - rel);
        }
        passenger.setYHeadRot(passenger.getYRot());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
