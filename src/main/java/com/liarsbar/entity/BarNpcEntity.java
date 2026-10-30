package com.liarsbar.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * NPC suasana bar (dipasang oleh {@code /liarsbar bar}). Tidak ikut permainan; hanya membuat bar terasa hidup.
 * <ul>
 *   <li>PATRON: duduk diam di tempatnya (stool, booth, kursi bistro); animasi minum/ngobrol/ketawa di client.</li>
 *   <li>BARTENDER: mondar-mandir di lorong belakang counter; berhenti untuk mengelap, atau berbalik ke rak,
 *   mengambil botol, lalu menuang ke gelas di tangan kirinya.</li>
 * </ul>
 * Badan yang terlihat adalah boneka pemain di client (seperti bot meja), dianimasikan Player Animator.
 */
public class BarNpcEntity extends PathfinderMob {

    public enum Role {
        PATRON, BARTENDER
    }

    /** Aksi saat ini (dibaca client untuk memilih animasi). */
    public static final int ACT_SIT = 0, ACT_WALK = 1, ACT_WIPE = 2, ACT_POUR = 3, ACT_FETCH = 5;

    private static final EntityDataAccessor<Integer> ROLE =
            SynchedEntityData.defineId(BarNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN =
            SynchedEntityData.defineId(BarNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACT =
            SynchedEntityData.defineId(BarNpcEntity.class, EntityDataSerializers.INT);

    /** Titik duduk / titik awal, arah hadap di sana, dan pusat meja bar (asal rute). */
    private Vec3 home = Vec3.ZERO;
    private float homeYaw;
    private Vec3 center = Vec3.ZERO;
    private int pause;
    private float faceYaw;

    public BarNpcEntity(EntityType<? extends BarNpcEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
        setSilent(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.FOLLOW_RANGE, 24);
    }

    /** Siapkan NPC baru di dunia: peran, kulit, titik duduk/awal, dan pusat bar. */
    public void setup(Role role, int skin, Vec3 home, float yaw, Vec3 center) {
        entityData.set(ROLE, role.ordinal());
        entityData.set(SKIN, skin);
        this.home = home;
        this.homeYaw = yaw;
        this.center = center;
        moveTo(home.x, home.y, home.z, yaw, 0);
        yBodyRot = yHeadRot = yaw;
        applyRole();
    }

    private void applyRole() {
        boolean seated = role() == Role.PATRON;
        setNoGravity(seated);
        noPhysics = seated;
        entityData.set(ACT, seated ? ACT_SIT : ACT_WIPE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ROLE, 0);
        entityData.define(SKIN, 0);
        entityData.define(ACT, ACT_SIT);
    }

    public Role role() {
        return Role.values()[Math.floorMod(entityData.get(ROLE), Role.values().length)];
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public int act() {
        return entityData.get(ACT);
    }

    // ------------------------------------------------------------ perilaku

    /**
     * Titik henti bartender di lorong belakang counter, relatif pusat meja: {x, z, rak botol di belakangnya (1)
     * atau tidak (0, di depan cermin)}. Di depan rak ia bisa mengambil botol lalu menuang.
     */
    private static final List<float[]> BAR_STOPS = List.of(
            new float[]{-5, -6, 1}, new float[]{-3, -6, 1}, new float[]{0, -6, 0}, new float[]{3, -6, 1},
            new float[]{5, -6, 1});
    /** Arah hadap: counter (selatan) dan rak botol (utara). */
    private static final float FACE_COUNTER = 0, FACE_SHELF = 180;
    /** Lama tiap aksi (tick): mengambil botol (sama dengan npc_fetch), menuang (npc_pour). */
    private static final int FETCH_TICKS = 64, POUR_TICKS = 180;
    /** Putar badan paling cepat (derajat per tick): balik badan ke rak tidak patah. */
    private static final float TURN_SPEED = 10;
    private boolean shelfBehind;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (role() == Role.PATRON) {
            setPos(home.x, home.y, home.z);
            setDeltaMovement(Vec3.ZERO);
            setYRot(homeYaw);
            yBodyRot = yHeadRot = homeYaw;
            return;
        }
        if (pause > 0) {
            pause--;
            float yaw = net.minecraft.util.Mth.approachDegrees(getYRot(), faceYaw, TURN_SPEED);
            setYRot(yaw);
            yBodyRot = yHeadRot = yaw;
            if (pause == 0 && act() == ACT_FETCH) {
                // Botol sudah di tangan: balik badan ke counter dan menuang.
                entityData.set(ACT, ACT_POUR);
                faceYaw = FACE_COUNTER;
                pause = POUR_TICKS;
            }
            return;
        }
        if (!getNavigation().isDone()) {
            return;
        }
        if (act() == ACT_WALK) {
            // Sampai: kadang ambil botol dari rak di belakangnya, selebihnya mengelap counter.
            if (shelfBehind && random.nextInt(5) < 2) {
                entityData.set(ACT, ACT_FETCH);
                faceYaw = FACE_SHELF;
                pause = FETCH_TICKS;
            } else {
                entityData.set(ACT, ACT_WIPE);
                faceYaw = FACE_COUNTER;
                pause = 100 + random.nextInt(140);
            }
            return;
        }
        float[] s = BAR_STOPS.get(random.nextInt(BAR_STOPS.size()));
        shelfBehind = s[2] > 0.5f;
        if (getNavigation().moveTo(center.x + s[0], center.y, center.z + s[1], 1.0)) {
            entityData.set(ACT, ACT_WALK);
        } else {
            pause = 60;
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    // ------------------------------------------------------------ simpan

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Role", entityData.get(ROLE));
        tag.putInt("Skin", skin());
        tag.putDouble("HomeX", home.x);
        tag.putDouble("HomeY", home.y);
        tag.putDouble("HomeZ", home.z);
        tag.putFloat("HomeYaw", homeYaw);
        tag.putDouble("CenterX", center.x);
        tag.putDouble("CenterY", center.y);
        tag.putDouble("CenterZ", center.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ROLE, tag.getInt("Role"));
        entityData.set(SKIN, tag.getInt("Skin"));
        home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        homeYaw = tag.getFloat("HomeYaw");
        center = new Vec3(tag.getDouble("CenterX"), tag.getDouble("CenterY"), tag.getDouble("CenterZ"));
        applyRole();
    }
}
