package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

import java.util.*;

/** Server-authoritative spatial removal, telekinetic grip, and timed falling-block gravity well. */
public final class IllusionistPower extends Power {
    private record SavedBlock(ServerWorld world, BlockPos pos, BlockState state) {}
    private static final class Erasure { final List<SavedBlock> blocks; int ticks;
        Erasure(List<SavedBlock> blocks,int ticks){this.blocks=blocks;this.ticks=ticks;} }
    private static final class PullState { final UUID owner; final ServerWorld world; final Vec3d center; final List<FallingBlockEntity> blocks;
        final Set<UUID> captured=new HashSet<>(); int ticks;
        PullState(UUID owner,ServerWorld world,Vec3d center,List<FallingBlockEntity> blocks,int ticks){this.owner=owner;this.world=world;this.center=center;this.blocks=blocks;this.ticks=ticks;} }

    private static final Map<UUID, Erasure> ACTIVE_ERASURES = new HashMap<>();
    private static final Map<UUID, PullState> ACTIVE_PULLS = new HashMap<>();
    private final Map<UUID,Integer> erasureCooldowns=new HashMap<>();
    private final Map<UUID,Integer> gripCooldowns=new HashMap<>();
    private final Map<UUID,Integer> pullCooldowns=new HashMap<>();
    private final Set<UUID> erasureArmed=new HashSet<>();
    private final Map<UUID,Entity> grips=new HashMap<>();
    private final Map<UUID,Double> gripDistances=new HashMap<>();
    private final Map<UUID,Integer> gripPassiveClock=new HashMap<>();

    @Override public PowerType getType(){return PowerType.ILLUSIONIST;}
    @Override public void onPrimaryActivate(ServerPlayerEntity player){
        if(erasureArmed.remove(player.getUuid())) player.sendMessage(Text.literal("Spatial Erasure cancelled."),true);
        else {erasureArmed.add(player.getUuid());player.sendMessage(Text.literal("Spatial Erasure armed — right-click the center block of a 3×3×3 area." ).formatted(Formatting.LIGHT_PURPLE),true);}
    }
    @Override public void onSecondaryActivate(ServerPlayerEntity player){toggleGrip(player);}
    @Override public void onTertiaryActivate(ServerPlayerEntity player){celestialPull(player);}
    @Override public void onQuaternaryActivate(ServerPlayerEntity player){throwGrip(player);}
    @Override public void onAlternateActivate(ServerPlayerEntity player){
        UUID id=player.getUuid(); if(!grips.containsKey(id)){player.sendMessage(Text.literal("Grab an entity first with H."),true);return;}
        double next=Math.min(10,Math.max(2,gripDistances.getOrDefault(id,YPConfig.getInstance().illusionist_grip_default_distance)+1));
        gripDistances.put(id,next);player.sendMessage(Text.literal("Psychokinetic Grip distance: "+next+" blocks"),true);
    }

    /** Invoked by the server UseBlock callback after G has explicitly armed the ability. */
    public boolean trySpatialErasure(ServerPlayerEntity player, BlockPos center) {
        if(!(player.getEntityWorld() instanceof ServerWorld world)||!erasureArmed.remove(player.getUuid()))return false;
        UUID owner=player.getUuid();int cooldown=erasureCooldowns.getOrDefault(owner,0);
        if(cooldown>0){player.sendMessage(Text.literal("Spatial Erasure cooldown: "+((cooldown+19)/20)+"s"),true);return true;}
        List<SavedBlock> saved=new ArrayList<>();
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
            BlockPos pos=center.add(dx,dy,dz);BlockState state=world.getBlockState(pos);
            if(state.isAir())continue;
            if(state.isOf(Blocks.BEDROCK)||state.isOf(Blocks.END_PORTAL)||state.isOf(Blocks.END_PORTAL_FRAME)
                    ||state.isOf(Blocks.NETHER_PORTAL)||world.getBlockEntity(pos)!=null||state.getHardness(world,pos)<0||isReserved(world,pos)){
                player.sendMessage(Text.literal("Spatial Erasure cancelled: protected, container, or overlapping block found." ).formatted(Formatting.RED),true);return true;
            }
            saved.add(new SavedBlock(world,pos.toImmutable(),state));
        }
        // Remove only after every position has passed validation; suppress drops by directly setting air.
        for(SavedBlock b:saved)world.setBlockState(b.pos(),Blocks.AIR.getDefaultState(),3);
        ACTIVE_ERASURES.put(owner,new Erasure(saved,Math.max(1,YPConfig.getInstance().illusionist_spatial_erasure_duration_ticks)));
        erasureCooldowns.put(owner,Math.max(0,YPConfig.getInstance().illusionist_spatial_erasure_cooldown_ticks));
        world.spawnParticles(ParticleTypes.PORTAL,center.getX()+.5,center.getY()+.5,center.getZ()+.5,70,1.5,1.5,1.5,.35);
        world.playSound(null,center,SoundEvents.BLOCK_END_PORTAL_SPAWN,SoundCategory.PLAYERS,1,.8f);
        return true;
    }

    private boolean isReserved(ServerWorld world,BlockPos pos){
        for(Erasure e:ACTIVE_ERASURES.values())for(SavedBlock b:e.blocks)if(b.world()==world&&b.pos().equals(pos))return true;
        return false;
    }

    private void toggleGrip(ServerPlayerEntity player){
        UUID id=player.getUuid();
        if(grips.containsKey(id)){releaseGrip(player,false);return;}
        int cd=gripCooldowns.getOrDefault(id,0);if(cd>0){player.sendMessage(Text.literal("Grip release cooldown: "+((cd+19)/20)+"s"),true);return;}
        Entity target=findLookEntity(player,Math.max(1,YPConfig.getInstance().illusionist_grip_range));
        if(target==null){player.sendMessage(Text.literal("Look at an entity or dropped item within 15 blocks."),true);return;}
        grips.put(id,target);gripDistances.put(id,Math.max(2,Math.min(10,YPConfig.getInstance().illusionist_grip_default_distance)));
        player.sendMessage(Text.literal("Psychokinetic Grip engaged. B throws; R adjusts distance; H releases." ).formatted(Formatting.LIGHT_PURPLE),true);
        player.getEntityWorld().playSound(null,player.getBlockPos(),SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.PLAYERS,.7f,1.5f);
    }

    private Entity findLookEntity(ServerPlayerEntity player,double range){
        if(!(player.getEntityWorld() instanceof ServerWorld world))return null;
        Vec3d start=player.getEyePos(),dir=PowerCombatUtil.safeLook(player);
        Box box=player.getBoundingBox().stretch(dir.multiply(range)).expand(1.2);
        Entity best=null;double bestDistance=range;double bestDot=.94;
        for(Entity e:world.getOtherEntities(player,box,x->x.isAlive()&&(x instanceof LivingEntity||x instanceof ItemEntity||x instanceof ExperienceOrbEntity))){
            Vec3d toward=e.getBoundingBox().getCenter().subtract(start);double d=toward.length();if(d<.1||d>range)continue;
            double dot=dir.dotProduct(toward.normalize());if(dot>bestDot||Math.abs(dot-bestDot)<1e-4&&d<bestDistance){best=e;bestDot=dot;bestDistance=d;}
        }
        return best;
    }

    private void throwGrip(ServerPlayerEntity player){
        Entity target=grips.get(player.getUuid());if(target==null){player.sendMessage(Text.literal("No held target."),true);return;}
        releaseGrip(player,true);
    }
    private void releaseGrip(ServerPlayerEntity player,boolean thrown){
        UUID id=player.getUuid();Entity target=grips.remove(id);
        gripDistances.remove(id);
        if(target!=null&&!target.isRemoved()&&thrown){Vec3d v=PowerCombatUtil.safeLook(player).multiply(Math.max(0.1,YPConfig.getInstance().illusionist_grip_throw_multiplier));target.setVelocity(v);target.velocityDirty=true;
            if(target instanceof LivingEntity living)living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING,20,0,false,false));
            player.sendMessage(Text.literal("Telekinetic throw released."),true);
        }
        gripCooldowns.put(id,Math.max(0,YPConfig.getInstance().illusionist_grip_release_cooldown_ticks));
    }

    private void celestialPull(ServerPlayerEntity player){
        if(!(player.getEntityWorld() instanceof ServerWorld world))return;
        UUID id=player.getUuid();int cd=pullCooldowns.getOrDefault(id,0);if(cd>0){player.sendMessage(Text.literal("Celestial Pull cooldown: "+((cd+19)/20)+"s"),true);return;}
        Vec3d look=PowerCombatUtil.safeLook(player);Vec3d endpoint=player.getEyePos().add(look.multiply(40));
        BlockHitResult hit=world.raycast(new RaycastContext(player.getEyePos(),endpoint,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
        BlockPos targetPos=hit.getType()==HitResult.Type.BLOCK?hit.getBlockPos():BlockPos.ofFloored(endpoint);
        int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,targetPos.getX(),targetPos.getZ());
        Vec3d center=new Vec3d(targetPos.getX()+.5,Math.max(world.getBottomY()+1,ground),targetPos.getZ()+.5);
        if(!world.getWorldBorder().contains(BlockPos.ofFloored(center))){player.sendMessage(Text.literal("Celestial Pull target is outside the world border."),true);return;}
        List<FallingBlockEntity> falling=new ArrayList<>();
        for(int dx=-2;dx<=2;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-2;dz<=2;dz++){
            if(dx*dx+dy*dy+dz*dz>6)continue;
            BlockState state=((dx+dy+dz)&1)==0?Blocks.BLACKSTONE.getDefaultState():Blocks.MAGMA_BLOCK.getDefaultState();
            BlockPos spawnPos=BlockPos.ofFloored(center.x+dx,center.y+25+dy,center.z+dz);
            FallingBlockEntity fallingBlock=FallingBlockEntity.spawnFromBlock(world,spawnPos,state);
            if(fallingBlock!=null){fallingBlock.setDestroyedOnLanding();fallingBlock.setVelocity(dx*.015,-.05, dz*.015);falling.add(fallingBlock);}
        }
        ACTIVE_PULLS.put(id,new PullState(id,world,center,falling,Math.max(1,YPConfig.getInstance().illusionist_celestial_pull_duration_ticks)));
        pullCooldowns.put(id,Math.max(0,YPConfig.getInstance().illusionist_celestial_pull_cooldown_ticks));
        world.spawnParticles(ParticleTypes.PORTAL,center.x,center.y+25,center.z,90,2,2,2,.25);
        world.playSound(null,BlockPos.ofFloored(center),SoundEvents.BLOCK_END_PORTAL_SPAWN,SoundCategory.PLAYERS,2,.5f);
        player.sendMessage(Text.literal("Celestial Pull: gravity well forming."),true);
    }

    @Override public void tick(ServerPlayerEntity player){
        UUID id=player.getUuid();decrement(erasureCooldowns,id);decrement(gripCooldowns,id);decrement(pullCooldowns,id);
        Entity held=grips.get(id);
        if(held!=null){
            if(held.isRemoved()||!held.isAlive()||held.getEntityWorld()!=player.getEntityWorld()||player.squaredDistanceTo(held)>Math.pow(YPConfig.getInstance().illusionist_grip_range+3,2)){
                grips.remove(id);gripDistances.remove(id);
            }else{
                Vec3d destination=player.getEyePos().add(PowerCombatUtil.safeLook(player).multiply(gripDistances.getOrDefault(id,5.0)));
                Vec3d velocity=destination.subtract(held.getEntityPos()).multiply(.35);
                held.setVelocity(velocity);held.velocityDirty=true;
                if(player.age%2==0 && player.getEntityWorld() instanceof ServerWorld gripWorld)
                    gripWorld.spawnParticles(ParticleTypes.ENCHANT,held.getX(),held.getY()+.5,held.getZ(),2,.12,.12,.12,.01);
            }
        }
        int passive=gripPassiveClock.getOrDefault(id,0)+1;
        if(passive>=10){passive=0;pullNearbyItems(player);}
        gripPassiveClock.put(id,passive);
    }

    private void pullNearbyItems(ServerPlayerEntity player){
        if(!(player.getEntityWorld() instanceof ServerWorld world))return;
        double r=Math.max(0,YPConfig.getInstance().illusionist_grip_passive_radius);
        for(Entity e:world.getOtherEntities(player,player.getBoundingBox().expand(r),x->x instanceof ItemEntity||x instanceof ExperienceOrbEntity)){
            if(e.squaredDistanceTo(player)>r*r)continue;
            Vec3d delta=player.getEntityPos().add(0,.5,0).subtract(e.getEntityPos());
            if(delta.lengthSquared()<.01)continue;
            e.addVelocity(delta.normalize().multiply(.22));e.velocityDirty=true;
        }
    }

    public static void serverTick(MinecraftServer server){
        Iterator<Map.Entry<UUID,Erasure>> erasures=ACTIVE_ERASURES.entrySet().iterator();
        while(erasures.hasNext()){
            Map.Entry<UUID,Erasure> entry=erasures.next();Erasure erasure=entry.getValue();
            if(--erasure.ticks>0)continue;
            for(SavedBlock b:erasure.blocks){
                if(b.world().isChunkLoaded(ChunkPos.toLong(b.pos()))&&b.world().getBlockState(b.pos()).isAir())b.world().setBlockState(b.pos(),b.state(),3);
            }
            ServerPlayerEntity owner=server.getPlayerManager().getPlayer(entry.getKey());
            if(!GraceManager.isActive(server)) {
                Set<UUID> processed=new HashSet<>();
                for(SavedBlock savedBlock:erasure.blocks) {
                    ServerWorld world=savedBlock.world();
                    Box cell=new Box(savedBlock.pos());
                    for(Entity entity:world.getOtherEntities(null,cell.expand(0.35),e->e instanceof LivingEntity&&e.isAlive())) {
                        if(!processed.add(entity.getUuid()) || !(entity instanceof LivingEntity living))continue;
                        if(entity instanceof ServerPlayerEntity targetPlayer) {
                            if(owner==null || targetPlayer==owner || AllianceManager.get(server).isAlly(owner,targetPlayer))continue;
                        }
                        living.damage(world,world.getDamageSources().magic(),(float)Math.max(0,YPConfig.getInstance().illusionist_spatial_erasure_damage));
                        living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,Math.max(1,YPConfig.getInstance().illusionist_spatial_erasure_slowness_ticks),3));
                    }
                }
            }
            erasures.remove();
        }
        Iterator<Map.Entry<UUID,PullState>> pulls=ACTIVE_PULLS.entrySet().iterator();
        while(pulls.hasNext()){
            Map.Entry<UUID,PullState> entry=pulls.next();PullState state=entry.getValue();
            ServerPlayerEntity owner=server.getPlayerManager().getPlayer(state.owner);
            if(owner==null||!owner.isAlive()||owner.getEntityWorld()!=state.world){
                for(FallingBlockEntity b:state.blocks)b.discard();pulls.remove();continue;
            }
            if(--state.ticks>0){
                double r=Math.max(1,YPConfig.getInstance().illusionist_celestial_pull_radius);
                Box area=new Box(state.center.x-r,state.center.y-r,state.center.z-r,state.center.x+r,state.center.y+r,state.center.z+r);
                for(Entity e:state.world.getOtherEntities(owner,area,x->!(x instanceof ServerPlayerEntity)&&x instanceof LivingEntity&&x.isAlive())){
                    if(e.squaredDistanceTo(state.center)>r*r)continue;
                    state.captured.add(e.getUuid());Vec3d delta=state.center.add(0,1,0).subtract(e.getEntityPos());
                    if(delta.lengthSquared()>.01){Vec3d v=delta.normalize().multiply(.18).add(0,.035,0);e.addVelocity(v);e.velocityDirty=true;}
                }
                if(state.ticks%5==0)state.world.spawnParticles(ParticleTypes.SMOKE,state.center.x,state.center.y,state.center.z,18,2,2,2,.01);
            }else{
                for(FallingBlockEntity b:state.blocks)b.discard();
                for(Entity e:state.world.iterateEntities()) if(state.captured.contains(e.getUuid())&&e instanceof LivingEntity living&&living.isAlive())
                    living.damage(state.world,state.world.getDamageSources().magic(),(float)Math.max(0,YPConfig.getInstance().illusionist_celestial_pull_damage));
                state.world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,state.center.x,state.center.y,state.center.z,1,0,0,0,0);
                state.world.spawnParticles(ParticleTypes.PORTAL,state.center.x,state.center.y,state.center.z,160,4,3,4,.35);
                EpicVfx.impact(state.world, state.center.x, state.center.y, state.center.z, 1.6);
                state.world.playSound(null, BlockPos.ofFloored(state.center), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 3, .6f);
                pulls.remove();
            }
        }
    }

    public static void shutdown(MinecraftServer server){
        for(Erasure erasure:ACTIVE_ERASURES.values())for(SavedBlock b:erasure.blocks){
            if(b.world().isChunkLoaded(ChunkPos.toLong(b.pos()))&&b.world().getBlockState(b.pos()).isAir())b.world().setBlockState(b.pos(),b.state(),3);
        }
        ACTIVE_ERASURES.clear();for(PullState state:ACTIVE_PULLS.values())for(FallingBlockEntity b:state.blocks)b.discard();ACTIVE_PULLS.clear();
    }
    private static void decrement(Map<UUID,Integer> map,UUID id){int v=map.getOrDefault(id,0);if(v>0)map.put(id,v-1);}
    public boolean tryThrowGripOnAttack(ServerPlayerEntity player) {
        if (!grips.containsKey(player.getUuid())) return false;
        throwGrip(player);
        return true;
    }
    @Override public void onPlayerDeath(ServerPlayerEntity player){cleanup(player.getUuid());}
    @Override public void onRemove(ServerPlayerEntity player){cleanup(player.getUuid());}
    @Override public void onPlayerDisconnect(ServerPlayerEntity player){cleanup(player.getUuid());}
    private void cleanup(UUID id){erasureArmed.remove(id);Entity held=grips.remove(id);gripDistances.remove(id);gripPassiveClock.remove(id);
        if(held!=null&&held.isAlive())held.setVelocity(Vec3d.ZERO);erasureCooldowns.remove(id);gripCooldowns.remove(id);pullCooldowns.remove(id);}
}
