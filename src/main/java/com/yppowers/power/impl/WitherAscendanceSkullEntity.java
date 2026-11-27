package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.World.ExplosionSourceType;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;

public final class WitherAscendanceSkullEntity extends WitherSkullEntity {
   public WitherAscendanceSkullEntity(World world, LivingEntity owner, Vec3d velocity) {
      super(world, owner, velocity);
   }

   @Override
   protected void onCollision(HitResult hitResult) {
      if (this.getEntityWorld() instanceof ServerWorld world) {
         Entity ownerEntity = this.getOwner();
         if (hitResult instanceof EntityHitResult entityHit
            && ownerEntity instanceof ServerPlayerEntity owner
            && entityHit.getEntity() instanceof LivingEntity target
            && PowerCombatUtil.canHarm(owner, target)) {
            YPConfig config = YPConfig.getInstance();
            float damage = (float)Math.max(0.0, config.fire_god_wither_skull_damage);
            if (damage > 0.0F && target.damage(world, this.getDamageSources().witherSkull(this, owner), damage)) {
               int witherTicks = Math.max(0, config.fire_god_wither_skull_wither_ticks);
               if (witherTicks > 0 && target.isAlive()) {
                  target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, witherTicks, 0, false, true, true), owner);
               }
            }
         }

         final ServerPlayerEntity ownerx = ownerEntity instanceof ServerPlayerEntity player ? player : null;
         float blastPower = (float)Math.max(0.0, YPConfig.getInstance().fire_god_wither_skull_explosion_power);
         if (blastPower > 0.0F) {
            ExplosionBehavior safeBlast = new ExplosionBehavior() {
               @Override
               public boolean canDestroyBlock(Explosion explosion, BlockView worldx, BlockPos pos, BlockState state, float power) {
                  return false;
               }

               @Override
               public boolean shouldDamage(Explosion explosion, Entity entity) {
                  return ownerx != null && PowerCombatUtil.canHarm(ownerx, entity);
               }
            };
            world.createExplosion(
               this, this.getDamageSources().witherSkull(this, ownerx), safeBlast, this.getEntityPos(), blastPower, false, ExplosionSourceType.NONE
            );
         }

         world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, this.getX(), this.getY(), this.getZ(), 14, 0.18, 0.18, 0.18, 0.025);
         world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.65F, 1.25F);
      }

      this.discard();
   }
}
