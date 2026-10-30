package com.liarsbar.entity;

import com.liarsbar.table.SeatLayout;
import com.liarsbar.table.TableManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Meja bundar (titik pusatnya di lantai). Ukurannya ikut jumlah kursi
 * ({@link SeatLayout.Size}); digambar client, tanpa blok.
 */
public class TableEntity extends Entity {

    private static final EntityDataAccessor<Integer> TABLE_ID =
            SynchedEntityData.defineId(TableEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(TableEntity.class, EntityDataSerializers.FLOAT);

    public TableEntity(EntityType<? extends TableEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TABLE_ID, -1);
        entityData.define(RADIUS, SeatLayout.Size.SMALL.radius);
    }

    public int tableId() {
        return entityData.get(TABLE_ID);
    }

    public void setTableId(int id) {
        entityData.set(TABLE_ID, id);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public void setRadius(float radius) {
        entityData.set(RADIUS, radius);
    }

    @Override
    public void tick() {
        super.tick();
        // Meja yang tidak lagi dikelola (mis. sisa sebelum restart) dibuang.
        if (!level().isClientSide && TableManager.get(tableId()) == null) {
            discard();
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        float r = radius() + 0.5f;
        return new AABB(getX() - r, getY(), getZ() - r, getX() + r, getY() + 1.5, getZ() + r);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
