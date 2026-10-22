package com.yppowers.entity;

import com.yppowers.config.YPConfig;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.ThorPower;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * The thrown Thor mace. It flies like a thrown item, and on impact strikes lightning
 * and digs a crater, then returns to its owner like a Loyalty trident.
 */
public class ThorMaceEntity extends ThrownItemEntity {
    /** If the mace somehow flies for this long without hitting anything, it impacts in place. */
    private static final int MAX_FLIGHT_TICKS = 200;

    private UUID ownerUuid;
    private boolean returning;

    public ThorMaceEntity(EntityType<? extends ThorMaceEntity> type, World world) {
        super(type, world);
    }

    public ThorMaceEntity(World world, ServerPlayerEntity owner, ItemStack stack) {
        super(YPEntities.THOR_MACE, owner, world, stack);
        this.ownerUuid = owner.getUuid();
    }

    @Override
    protected Item getDefaultItem() {
        return Items.MACE;
    }

    /** Whether the mace is flying back to its owner (already impacted). */
    public boolean isReturning() {
        return returning;
    }

    /** Begins the return trip without an impact (used when teleporting to the mace). */
    public void startReturning() {
        this.returning = true;
    }

    @Override
    public void tick() {
        if (returning) {
            tickReturn();
            return;
        }
        super.tick();
        if (!this.isRemoved() && !this.getEntityWorld().isClient() && this.age > MAX_FLIGHT_TICKS) {
            beginImpact();
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (!this.getEntityWorld().isClient()) beginImpact();
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (!this.getEntityWorld().isClient()) beginImpact();
    }

    private void beginImpact() {
        if (this.isRemoved() || returning) return;
        returning = true;
        ThorPower.doImpact((ServerWorld) this.getEntityWorld(), getOwnerPlayer(), getPos());
    }

    private void tickReturn() {
        if (this.getEntityWorld().isClient()) return;
        ServerPlayerEntity owner = getOwnerPlayer();
        if (owner == null || owner.isRemoved() || !owner.isAlive()) {
            notifyLost();
            this.discard();
            return;
        }

        Vec3d target = owner.getEyePos().add(0.0, -0.3, 0.0);
        Vec3d to = target.subtract(getPos());
        double dist = to.length();
        if (dist < 1.2) {
            ItemStack mace = this.getStack().copy();
            if (!mace.isEmpty()) owner.giveOrDropStack(mace);
            notifyReturned();
            this.discard();
            return;
        }

        double speed = Math.min(3.0, 1.2 + dist * 0.05);
        this.setNoGravity(true);
        this.setVelocity(to.normalize().multiply(speed));
        this.setPosition(getPos().add(this.getVelocity()));

        // Smash through terrain on the way back instead of phasing through it.
        ThorPower.breakAround((ServerWorld) this.getEntityWorld(), this.getBlockPos(),
                YPConfig.getInstance().thor_return_break_radius);
    }

    private Vec3d getPos() {
        return new Vec3d(this.getX(), this.getY(), this.getZ());
    }

    private ServerPlayerEntity getOwnerPlayer() {
        if (ownerUuid == null) return null;
        if (this.getEntityWorld() instanceof ServerWorld sw && sw.getServer() != null) {
            return sw.getServer().getPlayerManager().getPlayer(ownerUuid);
        }
        return null;
    }

    private ThorPower getThorPower() {
        if (this.getEntityWorld() instanceof ServerWorld sw && sw.getServer() != null) {
            var instance = PowerManager.get(sw.getServer()).getPowerInstance(PowerType.THOR);
            if (instance instanceof ThorPower thor) return thor;
        }
        return null;
    }

    private void notifyReturned() {
        ThorPower thor = getThorPower();
        if (thor != null) thor.onMaceReturned(ownerUuid);
    }

    private void notifyLost() {
        ThorPower thor = getThorPower();
        if (thor != null) thor.onMaceLost(ownerUuid);
    }
}
