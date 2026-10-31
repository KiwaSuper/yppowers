package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Kitsune uses the vanilla fox entity/model for its visible avatar; all combat is server-side. */
public final class KitsunePower extends Power {
    private static final class Foxfire { final UUID owner; final ServerWorld world; int ticks; int damageClock;
        Foxfire(UUID owner,ServerWorld world,int ticks){this.owner=owner;this.world=world;this.ticks=ticks;} }
    private static final class Transformation {
        final ServerWorld world; final FoxEntity fox; int ticks;
        final StatusEffectInstance oldInvisibility; final StatusEffectInstance oldSpeed;
        Transformation(ServerWorld world,FoxEntity fox,int ticks,StatusEffectInstance oldInvisibility,StatusEffectInstance oldSpeed){
            this.world=world;this.fox=fox;this.ticks=ticks;this.oldInvisibility=oldInvisibility;this.oldSpeed=oldSpeed;
        }
    }
    private final Map<UUID,Map<UUID,Foxfire>> foxfireByOwner=new HashMap<>();
    private final Map<UUID,Transformation> transformations=new HashMap<>();
    private final Map<UUID,Integer> transformationCooldowns=new HashMap<>();
    private final Map<UUID,Integer> clawCooldowns=new HashMap<>();
    private final Map<UUID,Integer> burstCooldowns=new HashMap<>();
    private final Map<UUID,Integer> pounceCooldowns=new HashMap<>();
    private final Map<UUID,Integer> detonationCooldowns=new HashMap<>();

    @Override public PowerType getType(){return PowerType.KITSUNE;}
    @Override public void onPrimaryActivate(ServerPlayerEntity player){clawSwipe(player);}
    @Override public void onSecondaryActivate(ServerPlayerEntity player){foxfireBurst(player);}
    @Override public void onTertiaryActivate(ServerPlayerEntity player){spiritPounce(player);}
    @Override public void onQuaternaryActivate(ServerPlayerEntity player){foxfireDetonation(player);}
    @Override public void onAlternateActivate(ServerPlayerEntity player){toggleTransformation(player);}

    private void clawSwipe(ServerPlayerEntity player){
        if (!transformations.containsKey(player.getUuid())) { player.sendMessage(Text.literal("Claw Swipe is only available during Giant Kitsune Transformation."), true); return; }
        if(!(player.getEntityWorld() instanceof ServerWorld world))return;
        UUID id=player.getUuid();int cd=clawCooldowns.getOrDefault(id,0);if(cd>0){notice(player,"Claw Swipe",cd);return;}
        double range=Math.max(1,YPConfig.getInstance().kitsune_claw_range);Vec3d look=PowerCombatUtil.safeLook(player);
        Vec3d center=player.getEyePos().add(look.multiply(range*.55));Box area=new Box(center.x-range,center.y-range,center.z-range,center.x+range,center.y+range,center.z+range);
        AllianceManager allies=AllianceManager.get(world.getServer());int hits=0;
        for(LivingEntity target:world.getEntitiesByClass(LivingEntity.class,area,e->e!=player&&e.isAlive())){
            Vec3d delta=target.getBoundingBox().getCenter().subtract(player.getEyePos());double distance=delta.length();if(distance>range||distance<.1||look.dotProduct(delta.normalize())<.35)continue;
            if(target instanceof ServerPlayerEntity p&&allies.isAlly(player,p))continue;
            target.damage(world,world.getDamageSources().playerAttack(player),(float)Math.max(0,YPConfig.getInstance().kitsune_claw_damage));
            applyFoxfire(player,target);Vec3d push=delta.normalize().multiply(.8).add(0,.25,0);target.addVelocity(push);target.velocityDirty=true;hits++;
        }
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK,center.x,center.y,center.z,8,.8,.6,.8,0);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,center.x,center.y,center.z,20,.8,.6,.8,.04);
        EpicVfx.impact(world, center.x, center.y, center.z, 1.0);
        world.playSound(null,player.getBlockPos(),SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,SoundCategory.PLAYERS,1,0.75f);
        clawCooldowns.put(id,Math.max(0,YPConfig.getInstance().kitsune_claw_cooldown_ticks));
        if(hits==0)player.sendMessage(Text.literal("Claw Swipe missed."),true);
    }

    private void foxfireBurst(ServerPlayerEntity player){
        if (!transformations.containsKey(player.getUuid())) { player.sendMessage(Text.literal("Foxfire Burst is only available during Giant Kitsune Transformation."), true); return; }
        if(!(player.getEntityWorld() instanceof ServerWorld world))return;
        UUID id=player.getUuid();int cd=burstCooldowns.getOrDefault(id,0);if(cd>0){notice(player,"Foxfire Burst",cd);return;}
        Vec3d origin=player.getEyePos(),dir=PowerCombatUtil.safeLook(player);double maxRange=24;AllianceManager allies=AllianceManager.get(world.getServer());
        world.spawnParticles(net.minecraft.particle.DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), origin.x, origin.y, origin.z, 24, .15, .15, .15, .15);
        for(int step=1;step<=24;step++){
            Vec3d pos=origin.add(dir.multiply(step));
            if(step%2==0){world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,pos.x,pos.y,pos.z,3,.12,.12,.12,.02);world.spawnParticles(ParticleTypes.WITCH,pos.x,pos.y,pos.z,2,.1,.1,.1,.01);}
        }
        Box area=player.getBoundingBox().stretch(dir.multiply(maxRange)).expand(1.2);
        for(LivingEntity target:world.getEntitiesByClass(LivingEntity.class,area,e->e!=player&&e.isAlive())){
            Vec3d delta=target.getBoundingBox().getCenter().subtract(origin);double distance=delta.length();if(distance>maxRange||distance<.1)continue;
            if(dir.dotProduct(delta.normalize())<.965)continue;if(target instanceof ServerPlayerEntity p&&allies.isAlly(player,p))continue;
            target.damage(world,world.getDamageSources().magic(),(float)Math.max(0,YPConfig.getInstance().kitsune_burst_damage));applyFoxfire(player,target);
        }
        world.playSound(null,player.getBlockPos(),SoundEvents.ENTITY_BLAZE_SHOOT,SoundCategory.PLAYERS,1,1.4f);
        burstCooldowns.put(id,Math.max(0,YPConfig.getInstance().kitsune_burst_cooldown_ticks));
    }

    private void spiritPounce(ServerPlayerEntity player){
        if (!transformations.containsKey(player.getUuid())) { player.sendMessage(Text.literal("Spirit Pounce is only available during Giant Kitsune Transformation."), true); return; }
        if(!(player.getEntityWorld() instanceof ServerWorld world))return;
        UUID id=player.getUuid();int cd=pounceCooldowns.getOrDefault(id,0);if(cd>0){notice(player,"Spirit Pounce",cd);return;}
        double range=Math.max(1,YPConfig.getInstance().kitsune_pounce_range);Vec3d origin=player.getEyePos(),dir=PowerCombatUtil.safeLook(player);
        Box area=player.getBoundingBox().stretch(dir.multiply(range)).expand(1.4);AllianceManager allies=AllianceManager.get(world.getServer());LivingEntity best=null;double bestD=range*range;
        for(LivingEntity e:world.getEntitiesByClass(LivingEntity.class,area,x->x!=player&&x.isAlive())){
            if(e instanceof ServerPlayerEntity p&&allies.isAlly(player,p))continue;Vec3d d=e.getBoundingBox().getCenter().subtract(origin);double dist=d.length();if(dist>range||dist<.1||dir.dotProduct(d.normalize())<.92)continue;
            if(dist*dist<bestD){best=e;bestD=dist*dist;}
        }
        if(best==null){player.setVelocity(dir.x*.9,.45,dir.z*.9);player.velocityDirty=true;player.sendMessage(Text.literal("Pounce launched, but no valid target was in reach."),true);}
        else{
            Vec3d delta=best.getEntityPos().subtract(player.getEntityPos());player.setVelocity(delta.normalize().multiply(1.5).add(0,.35,0));player.velocityDirty=true;
            best.damage(world,world.getDamageSources().playerAttack(player),(float)Math.max(0,YPConfig.getInstance().kitsune_pounce_damage));applyFoxfire(player,best);
            Vec3d push=delta.normalize().multiply(Math.max(0,YPConfig.getInstance().kitsune_pounce_knockback)).add(0,.4,0);best.addVelocity(push);best.velocityDirty=true;
        }
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,player.getX(),player.getY(),player.getZ(),30,.5,.5,.5,.08);
        EpicVfx.activation(world, player.getX(), player.getY(), player.getZ(), 1.0);
        pounceCooldowns.put(id,Math.max(0,YPConfig.getInstance().kitsune_pounce_cooldown_ticks));
    }

    private void toggleTransformation(ServerPlayerEntity player){
        UUID id=player.getUuid();Transformation current=transformations.get(id);
        if(current!=null){endTransformation(player,"You returned to normal form.");return;}
        int cd=transformationCooldowns.getOrDefault(id,0);if(cd>0){notice(player,"Giant Kitsune Transformation",cd);return;}
        if(!(player.getEntityWorld() instanceof ServerWorld world))return;
        FoxEntity fox=EntityType.FOX.create(world,SpawnReason.TRIGGERED);if(fox==null){player.sendMessage(Text.literal("The fox avatar could not manifest."),true);return;}
        fox.refreshPositionAndAngles(player.getX(),player.getY(),player.getZ(),player.getYaw(),player.getPitch());
        fox.setCustomName(Text.literal("Kitsune · "+player.getName().getString()).formatted(Formatting.AQUA,Formatting.BOLD));fox.setCustomNameVisible(false);
        fox.addCommandTag("yppowers_kitsune_avatar");fox.addCommandTag("yppowers_owner_"+id);fox.setPersistent();fox.setInvulnerable(true);fox.setNoGravity(true);
        // Use vanilla's scale attribute where provided by the target game; this does not replace the model.
        try { var scale=fox.getAttributeInstance(EntityAttributes.SCALE);if(scale!=null)scale.setBaseValue(Math.max(1.0,YPConfig.getInstance().kitsune_transformation_speed_multiplier)); }
        catch (Throwable ignored) { /* Some mappings may not expose a scale attribute on this entity. */ }
        StatusEffectInstance old=player.getStatusEffect(StatusEffects.INVISIBILITY);
        StatusEffectInstance oldSpeed=player.getStatusEffect(StatusEffects.SPEED);
        transformations.put(id,new Transformation(world,fox,Math.max(1,YPConfig.getInstance().kitsune_transformation_duration_ticks),old==null?null:new StatusEffectInstance(old),oldSpeed==null?null:new StatusEffectInstance(oldSpeed)));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY,Math.max(1,YPConfig.getInstance().kitsune_transformation_duration_ticks),0,false,false,false));
        world.spawnEntity(fox);
        double speed=Math.max(1.0,YPConfig.getInstance().kitsune_transformation_speed_multiplier);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,Math.max(1,YPConfig.getInstance().kitsune_transformation_duration_ticks),Math.max(0,(int)Math.ceil(speed)-1),false,false));
        world.playSound(null,player.getBlockPos(),SoundEvents.ENTITY_FOX_SCREECH,SoundCategory.PLAYERS,1.4f,.7f);
        player.sendMessage(Text.literal("GIANT KITSUNE — Foxfire transformation active." ).formatted(Formatting.AQUA,Formatting.BOLD),true);
    }

    private void endTransformation(ServerPlayerEntity player,String message){
        Transformation trans=transformations.remove(player.getUuid());if(trans!=null&&!trans.fox.isRemoved())trans.fox.discard();
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        if(trans!=null&&trans.oldInvisibility!=null)player.addStatusEffect(trans.oldInvisibility);
        player.removeStatusEffect(StatusEffects.SPEED);
        if(trans!=null&&trans.oldSpeed!=null)player.addStatusEffect(trans.oldSpeed);
        transformationCooldowns.put(player.getUuid(),Math.max(0,YPConfig.getInstance().kitsune_transformation_cooldown_ticks));
        player.sendMessage(Text.literal(message),true);
    }

    private void applyFoxfire(ServerPlayerEntity owner,LivingEntity target){
        if(!(owner.getEntityWorld() instanceof ServerWorld world)||target==owner)return;
        if(target instanceof ServerPlayerEntity p&&AllianceManager.get(world.getServer()).isAlly(owner,p))return;
        Map<UUID,Foxfire> active=foxfireByOwner.computeIfAbsent(owner.getUuid(),ignored->new HashMap<>());
        active.put(target.getUuid(),new Foxfire(owner.getUuid(),world,Math.max(1,YPConfig.getInstance().kitsune_foxfire_duration_ticks)));
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,target.getX(),target.getBodyY(.5),target.getZ(),12,.35,.4,.35,.02);
    }

    private void foxfireDetonation(ServerPlayerEntity player){
        if(!(player.getEntityWorld() instanceof ServerWorld world))return;
        UUID id=player.getUuid();int cd=detonationCooldowns.getOrDefault(id,0);if(cd>0){notice(player,"Foxfire Detonation",cd);return;}
        Map<UUID,Foxfire> active=foxfireByOwner.get(id);if(active==null||active.isEmpty()){player.sendMessage(Text.literal("No targets are afflicted with your Foxfire."),true);return;}
        AllianceManager allies=AllianceManager.get(world.getServer());Set<UUID> processed=new HashSet<>();double radius=Math.max(0,YPConfig.getInstance().kitsune_detonation_radius);
        for(UUID targetId:new ArrayList<>(active.keySet())){
            if(!processed.add(targetId))continue;Entity entity=null;for(Entity candidate:world.iterateEntities())if(candidate.getUuid().equals(targetId)){entity=candidate;break;}
            if(!(entity instanceof LivingEntity target)||!target.isAlive())continue;
            if(target instanceof ServerPlayerEntity p&&allies.isAlly(player,p)){active.remove(targetId);continue;}
            Box area=target.getBoundingBox().expand(radius);
            for(LivingEntity near:world.getEntitiesByClass(LivingEntity.class,area,e->e.isAlive())){
                if(near==player||(near instanceof ServerPlayerEntity ally&&allies.isAlly(player,ally)))continue;
                if(near.getEntityPos().squaredDistanceTo(target.getEntityPos())>radius*radius)continue;
                near.damage(world,world.getDamageSources().magic(),(float)Math.max(0,YPConfig.getInstance().kitsune_detonation_damage));
            }
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,target.getX(),target.getBodyY(.5),target.getZ(),1,0,0,0,0);
            EpicVfx.impact(world, target.getX(), target.getBodyY(.5), target.getZ(), 1.1);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,target.getX(),target.getBodyY(.5),target.getZ(),30,.8,.6,.8,.06);
            active.remove(targetId);
        }
        if(active.isEmpty())foxfireByOwner.remove(id);
        detonationCooldowns.put(id,Math.max(0,YPConfig.getInstance().kitsune_detonation_cooldown_ticks));
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4f, .8f);
    }

    @Override public void onAttack(ServerPlayerEntity attacker,Entity target){if(target instanceof LivingEntity living&&PowerCombatUtil.canHarm(attacker,target))applyFoxfire(attacker,living);}

    @Override public void tick(ServerPlayerEntity player){
        UUID id=player.getUuid();decrement(transformationCooldowns,id);decrement(clawCooldowns,id);decrement(burstCooldowns,id);decrement(pounceCooldowns,id);decrement(detonationCooldowns,id);
        Transformation trans=transformations.get(id);
        if(trans!=null){
            if(!player.isAlive()||player.getEntityWorld()!=trans.world||trans.fox.isRemoved()||trans.ticks--<=0){endTransformation(player,"Kitsune transformation ended.");}
            else{
                trans.fox.teleport(trans.world,player.getX(),player.getY(),player.getZ(),Set.of(),player.getYaw(),player.getPitch(),false);
                if(player.age%2==0){trans.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,player.getX(),player.getY()+.2,player.getZ(),5,.4,.1,.4,.02);trans.world.spawnParticles(net.minecraft.particle.DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), player.getX(), player.getY() + .3, player.getZ(), 2, .3, .1, .3, .01);}
                if(player.getVelocity().horizontalLengthSquared()>.04){trans.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,player.getX(),player.getY()+.05,player.getZ(),4,.35,.05,.35,.015);}
            }
        }
        Map<UUID,Foxfire> active=foxfireByOwner.get(id);if(active!=null&&player.getEntityWorld() instanceof ServerWorld world){
            Iterator<Map.Entry<UUID,Foxfire>> it=active.entrySet().iterator();
            while(it.hasNext()){
                Map.Entry<UUID,Foxfire> entry=it.next();Foxfire f=entry.getValue();Entity target=null;
                for(Entity e:world.iterateEntities())if(e.getUuid().equals(entry.getKey())){target=e;break;}
                if(target==null||!target.isAlive()||f.world!=world||--f.ticks<=0){it.remove();continue;}
                if(target instanceof LivingEntity living){if(--f.damageClock<=0){f.damageClock=20;living.damage(world,world.getDamageSources().magic(),(float)Math.max(0,YPConfig.getInstance().kitsune_foxfire_damage_per_second));}
                    if(player.age%4==0)world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,target.getX(),target.getBodyY(.5),target.getZ(),2,.2,.3,.2,.02);}
            }
            if(active.isEmpty())foxfireByOwner.remove(id);
        }
    }

    private void notice(ServerPlayerEntity player,String name,int ticks){player.sendMessage(Text.literal(name+" cooldown: "+((ticks+19)/20)+"s").formatted(Formatting.YELLOW),true);}
    private static void decrement(Map<UUID,Integer> map,UUID id){int n=map.getOrDefault(id,0);if(n>0)map.put(id,n-1);}
    @Override public void onPlayerDeath(ServerPlayerEntity player){cleanup(player,true);}
    @Override public void onRemove(ServerPlayerEntity player){cleanup(player,true);}
    @Override public void onPlayerJoin(ServerPlayerEntity player){/* transformation state is never restored across logout; cleanup is done on disconnect */}
    @Override public void onPlayerDisconnect(ServerPlayerEntity player){cleanup(player,true);}
    private void cleanup(ServerPlayerEntity player,boolean restore){
        UUID id=player.getUuid();Transformation trans=transformations.remove(id);if(trans!=null)trans.fox.discard();
        player.removeStatusEffect(StatusEffects.INVISIBILITY);player.removeStatusEffect(StatusEffects.SPEED);
        Map<UUID,Foxfire> effects=foxfireByOwner.remove(id);if(effects!=null)effects.clear();
        transformationCooldowns.remove(id);clawCooldowns.remove(id);burstCooldowns.remove(id);pounceCooldowns.remove(id);detonationCooldowns.remove(id);
    }
}
