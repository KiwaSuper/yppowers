package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.particle.BlockStateParticleEffect;
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
    /** Celestial Pull: a hovering moon, a weightless area, a gravity well, and a staged crater blast. */
    private static final class PullState {
        final UUID owner; final ServerWorld world; final Vec3d center; final Vec3d moon;
        final double moonRadius, craterRadius, weightlessRadius, moonHeight;
        final List<FallingBlockEntity> blocks=new ArrayList<>();
        final ArrayDeque<BlockPos> pending=new ArrayDeque<>();
        final Set<UUID> captured=new HashSet<>();
        final Set<UUID> weightless=new HashSet<>();
        int ticks; int age; int blastAge=-1;
        int assembleBatch=14, removeBatch=40, totalPlanned;
        PullState(UUID owner,ServerWorld world,Vec3d center,Vec3d moon,double moonRadius,double moonHeight,double craterRadius,double weightlessRadius,int ticks){
            this.owner=owner;this.world=world;this.center=center;this.moon=moon;this.moonRadius=moonRadius;this.moonHeight=moonHeight;
            this.craterRadius=craterRadius;this.weightlessRadius=weightlessRadius;this.ticks=ticks;
        } }
    private static final int CRATER_TICKS=16;
    /** Entities whose gravity could not be restored yet (unloaded chunk, offline player); retried until found. */
    private static final Map<UUID,Integer> PENDING_GRAVITY_RESTORE=new HashMap<>();

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
        EpicVfx.activation(world,center.getX()+.5,center.getY(),center.getZ()+.5,1.4);
        EpicVfx.implosion(world,ParticleTypes.REVERSE_PORTAL,center.getX()+.5,center.getY()+.5,center.getZ()+.5,5.0,24,.3);
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
        for(Entity e:world.getOtherEntities(player,box,x->x.isAlive()&&(x instanceof LivingEntity||x instanceof ItemEntity||x instanceof ExperienceOrbEntity)&&!(x instanceof ServerPlayerEntity ally&&AllianceManager.get(world.getServer()).isAlly(player,ally)))){
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
        if(target!=null&&!target.isRemoved()&&thrown){Vec3d v=PowerCombatUtil.safeLook(player).multiply(Math.max(0.1,YPConfig.getInstance().illusionist_grip_throw_multiplier));target.setVelocity(v);((com.yppowers.mixin.EntityVelocityAccessor) (Object) target).invokeYppowersScheduleVelocityUpdate();
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
        YPConfig cfg=YPConfig.getInstance();
        double moonRadius=Math.max(2.0,Math.min(14.0,cfg.illusionist_celestial_moon_radius*Math.max(0.5,cfg.illusionist_celestial_moon_scale)));
        // Keep the underside well above the target however large the moon is.
        double moonHeight=Math.max(Math.max(10.0,cfg.illusionist_celestial_moon_height),moonRadius*2.8);
        double maxMoonY=world.getBottomY()+world.getHeight()-moonRadius-2;
        Vec3d moon=new Vec3d(center.x,Math.min(center.y+moonHeight,maxMoonY),center.z);
        int duration=Math.max(1,Math.min(560,cfg.illusionist_celestial_pull_duration_ticks)); // moon blocks must not outlive the falling-block despawn timer
        PullState state=new PullState(id,world,center,moon,moonRadius,moon.y-center.y,
                Math.max(Math.max(cfg.illusionist_celestial_crater_radius,cfg.illusionist_celestial_pull_radius),moonRadius*1.8),cfg.illusionist_celestial_weightless_radius,duration);
        // Hollow shell of blocks. It assembles in a rising spiral over ~1.2 seconds (small batches each tick) instead of
        // popping in, and is thinned evenly if it would exceed the configured entity budget.
        List<BlockPos> shell=new ArrayList<>();
        int reach=(int)Math.ceil(moonRadius);
        for(int dx=-reach;dx<=reach;dx++)for(int dy=-reach;dy<=reach;dy++)for(int dz=-reach;dz<=reach;dz++){
            double d=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(d>moonRadius||d<moonRadius-1.15||((dx+dy+dz)&1)!=0)continue;
            shell.add(BlockPos.ofFloored(moon.x+dx,moon.y+dy,moon.z+dz));
        }
        final double centerY=moon.y,radiusForSort=moonRadius;
        shell.sort(Comparator.comparingDouble(pos->{
            double angle=Math.atan2(pos.getZ()+.5-moon.z,pos.getX()+.5-moon.x)/(2.0*Math.PI)+.5;
            double height=(pos.getY()+.5-centerY+radiusForSort)/(2.0*radiusForSort);
            return height*3.0+angle; // three turns of spiral from the bottom to the top
        }));
        int budget=Math.max(100,cfg.illusionist_celestial_moon_max_blocks);
        if(shell.size()>budget){
            List<BlockPos> thinned=new ArrayList<>(budget);
            double step=shell.size()/(double)budget;
            for(double i=0;i<shell.size()&&thinned.size()<budget;i+=step)thinned.add(shell.get((int)i));
            shell=thinned;
        }
        state.totalPlanned=shell.size();
        state.assembleBatch=Math.max(8,(int)Math.ceil(shell.size()/24.0));
        state.removeBatch=Math.max(40,(int)Math.ceil(shell.size()/8.0));
        state.pending.addAll(shell);
        ACTIVE_PULLS.put(id,state);
        applyWeightless(world.getServer(),state,player);
        pullCooldowns.put(id,Math.max(0,cfg.illusionist_celestial_pull_cooldown_ticks));
        world.spawnParticles(ParticleTypes.PORTAL,moon.x,moon.y,moon.z,90,2,2,2,.25);
        EpicVfx.column(world,ParticleTypes.REVERSE_PORTAL,center.x,center.y,center.z,Math.min(40.0,moon.y-center.y),30);
        EpicVfx.shockwave(world,center.x,center.y,center.z,8.0);
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
                held.setVelocity(velocity);((com.yppowers.mixin.EntityVelocityAccessor) (Object) held).invokeYppowersScheduleVelocityUpdate();
                if(player.age%2==0 && player.getEntityWorld() instanceof ServerWorld gripWorld)
                    gripWorld.spawnParticles(ParticleTypes.ENCHANT,held.getX(),held.getY()+.5,held.getZ(),2,.12,.12,.12,.01);
            }
        }
        int passive=gripPassiveClock.getOrDefault(id,0)+1;
        if(passive>=2){passive=0;pullNearbyItems(player);}
        gripPassiveClock.put(id,passive);
    }

    private void pullNearbyItems(ServerPlayerEntity player){
        if(!(player.getEntityWorld() instanceof ServerWorld world))return;
        double r=Math.max(0,YPConfig.getInstance().illusionist_grip_passive_radius);
        for(Entity e:world.getOtherEntities(player,player.getBoundingBox().expand(r),x->x instanceof ItemEntity||x instanceof ExperienceOrbEntity)){
            if(e.squaredDistanceTo(player)>r*r)continue;
            Vec3d delta=player.getEntityPos().add(0,.5,0).subtract(e.getEntityPos());
            if(delta.lengthSquared()<.01)continue;
            e.addVelocity(delta.normalize().multiply(.1));((com.yppowers.mixin.EntityVelocityAccessor) (Object) e).invokeYppowersScheduleVelocityUpdate();
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
                        if(owner!=null&&PowerCombatUtil.isFriendlyMinion(owner,entity))continue;
                        living.damage(world,world.getDamageSources().magic(),(float)Math.max(0,YPConfig.getInstance().illusionist_spatial_erasure_damage));
                        living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,Math.max(1,YPConfig.getInstance().illusionist_spatial_erasure_slowness_ticks),3));
                    }
                }
            }
            erasures.remove();
        }
        if(!PENDING_GRAVITY_RESTORE.isEmpty()&&server.getTicks()%20==0){
            Iterator<Map.Entry<UUID,Integer>> pending=PENDING_GRAVITY_RESTORE.entrySet().iterator();
            while(pending.hasNext()){
                Map.Entry<UUID,Integer> entry=pending.next();
                if(restoreGravity(server,entry.getKey(),true)!=null||entry.getValue()-20<=0)pending.remove();
                else entry.setValue(entry.getValue()-20);
            }
        }
        Iterator<Map.Entry<UUID,PullState>> pulls=ACTIVE_PULLS.entrySet().iterator();
        while(pulls.hasNext()){
            Map.Entry<UUID,PullState> entry=pulls.next();PullState state=entry.getValue();
            ServerPlayerEntity owner=server.getPlayerManager().getPlayer(state.owner);
            if(owner==null||!owner.isAlive()||owner.getEntityWorld()!=state.world){abortPull(server,state);pulls.remove();continue;}
            if(state.blastAge>=0){if(tickCrater(state))pulls.remove();continue;}
            state.age++;
            // Assemble the moon in small batches; only air is replaced, so terrain is never removed.
            for(int i=0;i<state.assembleBatch&&!state.pending.isEmpty();i++){
                FallingBlockEntity moonBlock=spawnMoonBlock(state.world,state.pending.poll(),state.blocks.size());
                if(moonBlock!=null)state.blocks.add(moonBlock);
            }
            if(--state.ticks>0){
                if(state.age%10==1)applyWeightless(server,state,owner);
                double r=state.craterRadius;
                Box area=new Box(state.center.x-r,state.center.y-r,state.center.z-r,state.center.x+r,state.center.y+r,state.center.z+r);
                Vec3d hover=new Vec3d(state.center.x,state.center.y+Math.min(10.0,state.moonHeight*.45),state.center.z);
                for(Entity e:state.world.getOtherEntities(owner,area,x->!(x instanceof ServerPlayerEntity)&&x instanceof LivingEntity&&x.isAlive()&&!PowerCombatUtil.isFriendlyMinion(owner,x))){
                    if(e.squaredDistanceTo(state.center)>r*r)continue;
                    state.captured.add(e.getUuid());
                    // Weightless prey drift up into the well and hang beneath the moon.
                    Vec3d delta=hover.subtract(e.getEntityPos());
                    Vec3d v=delta.lengthSquared()<2.25?Vec3d.ZERO:delta.multiply(.1);
                    if(v.lengthSquared()>1.44)v=v.normalize().multiply(1.2);
                    e.setVelocity(v);((com.yppowers.mixin.EntityVelocityAccessor) (Object) e).invokeYppowersScheduleVelocityUpdate();
                }
                if(state.age%4==0){
                    state.world.spawnParticles(ParticleTypes.LARGE_SMOKE,state.moon.x,state.moon.y,state.moon.z,10,state.moonRadius*.6,state.moonRadius*.6,state.moonRadius*.6,.01);
                    state.world.spawnParticles(ParticleTypes.END_ROD,state.moon.x,state.moon.y,state.moon.z,4,state.moonRadius*.7,state.moonRadius*.7,state.moonRadius*.7,.02);
                }
                if(state.age%12==0)EpicVfx.column(state.world,ParticleTypes.REVERSE_PORTAL,state.center.x,state.center.y,state.center.z,Math.min(30.0,state.moonHeight),12);
                tickMoonVfx(state);
            }else beginCrater(server,state);
        }
    }

    /** Cinematic staging while the moon hangs: rotating corona, ground shadow, gravity tendrils, and a comet-like final approach. */
    private static void tickMoonVfx(PullState state){
        ServerWorld world=state.world;Vec3d moon=state.moon,c=state.center;
        double r=state.moonRadius;
        double progress=Math.min(1.0,state.age/(double)Math.max(1,state.age+state.ticks));
        // Corona: a ring of light that rotates around the moon and brightens as the strike approaches.
        double spin=state.age*.12;int corona=(int)(6+10*progress);
        for(int k=0;k<corona;k++){
            double theta=spin+2.0*Math.PI*k/corona,phi=Math.PI*(.15+.7*((k*.6180339887)%1.0));
            world.spawnParticles(k%3==0?ParticleTypes.SOUL_FIRE_FLAME:ParticleTypes.END_ROD,
                    moon.x+Math.cos(theta)*Math.sin(phi)*r*1.05,moon.y+Math.cos(phi)*r*1.05,moon.z+Math.sin(theta)*Math.sin(phi)*r*1.05,0,
                    -Math.sin(theta),0.0,Math.cos(theta),.04);
        }
        if(state.age%6==0){
            // Ground shadow that tightens toward the impact point.
            double shadow=r*(1.5-.6*progress);
            EpicVfx.ring(world,ParticleTypes.LARGE_SMOKE,c.x,c.y+.15,c.z,shadow,.0,24,0.0);
            // Gravity tendrils spiralling up from the ground to the underside of the moon.
            EpicVfx.helix(world,ParticleTypes.REVERSE_PORTAL,c.x,c.y,c.z,Math.max(4.0,moon.y-r-c.y),Math.min(6.0,r*.6),2.0,22,.06);
        }
        // Final approach: the moon "ignites" and a ring of fire sweeps down to the ground, with a rising charge sound.
        if(state.ticks==26)world.playSound(null,BlockPos.ofFloored(c),SoundEvents.ENTITY_WARDEN_SONIC_CHARGE,SoundCategory.PLAYERS,5.0f,.6f);
        if(state.ticks<=14){
            double f=1.0-state.ticks/14.0;
            double y=(moon.y-r)+((c.y+1.0)-(moon.y-r))*f;
            EpicVfx.ring(world,ParticleTypes.FLAME,moon.x,y,moon.z,r*.55,.02,20,-.04);
            world.spawnParticles(ParticleTypes.LAVA,moon.x,moon.y-r,moon.z,3,r*.5,.3,r*.5,0);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,moon.x,moon.y-r,moon.z,6,r*.5,.3,r*.5,.02);
        }
    }

    private static FallingBlockEntity spawnMoonBlock(ServerWorld world,BlockPos pos,int index){
        if(!world.getBlockState(pos).isAir())return null;
        BlockState block=index%7==0?Blocks.CRYING_OBSIDIAN.getDefaultState():index%5==0?Blocks.MAGMA_BLOCK.getDefaultState():index%3==0?Blocks.OBSIDIAN.getDefaultState():Blocks.BLACKSTONE.getDefaultState();
        FallingBlockEntity fallingBlock=FallingBlockEntity.spawnFromBlock(world,pos,block);
        if(fallingBlock==null)return null;
        fallingBlock.setNoGravity(true);fallingBlock.setVelocity(Vec3d.ZERO);fallingBlock.setDestroyedOnLanding();
        return fallingBlock;
    }

    /** Makes every non-allied living entity and item in the area weightless; gravity is restored when the skill ends. */
    private static void applyWeightless(MinecraftServer server,PullState state,ServerPlayerEntity owner){
        double wr=state.weightlessRadius;AllianceManager allies=AllianceManager.get(server);
        Box box=new Box(state.center.x-wr,state.center.y-wr,state.center.z-wr,state.center.x+wr,state.center.y+wr,state.center.z+wr);
        for(Entity e:state.world.getOtherEntities(owner,box,x->x.isAlive()&&!x.hasNoGravity()&&(x instanceof LivingEntity||x instanceof ItemEntity))){
            if(e.squaredDistanceTo(state.center)>wr*wr)continue;
            if(e instanceof ServerPlayerEntity other&&allies.isAlly(owner,other))continue;
            if(PowerCombatUtil.isFriendlyMinion(owner,e))continue;
            e.setNoGravity(true);state.weightless.add(e.getUuid());
        }
    }

    private static Entity restoreGravity(MinecraftServer server,UUID id,boolean slowFall){
        for(ServerWorld world:server.getWorlds()){
            Entity e=world.getEntity(id);
            if(e==null)continue;
            e.setNoGravity(false);
            if(slowFall&&e instanceof LivingEntity living&&living.isAlive())
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING,80,0,false,false));
            return e;
        }
        return null;
    }

    private static void beginCrater(MinecraftServer server,PullState state){
        state.blastAge=0;
        YPConfig cfg=YPConfig.getInstance();
        float damage=(float)Math.max(0,Math.max(cfg.illusionist_celestial_pull_damage,cfg.illusionist_celestial_crater_damage));
        for(UUID capturedId:state.captured){
            Entity captured=state.world.getEntity(capturedId);
            if(captured instanceof LivingEntity living&&living.isAlive())living.damage(state.world,state.world.getDamageSources().magic(),damage);
        }
        // Gravity returns: captured prey are slammed down into the crater, everything else drifts down gently.
        for(UUID id:state.weightless){
            boolean wasCaptured=state.captured.contains(id);
            Entity e=restoreGravity(server,id,!wasCaptured);
            if(e==null){PENDING_GRAVITY_RESTORE.put(id,72000);continue;}
            if(wasCaptured&&e.isAlive()){e.setVelocity(0,-1.8,0);((com.yppowers.mixin.EntityVelocityAccessor) (Object) e).invokeYppowersScheduleVelocityUpdate();}
        }
        ServerWorld world=state.world;Vec3d c=state.center;
        world.playSound(null,BlockPos.ofFloored(c),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.BLOCKS,5,.5f);
        world.playSound(null,BlockPos.ofFloored(c),SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,SoundCategory.WEATHER,6,.6f);
        world.playSound(null,BlockPos.ofFloored(c),SoundEvents.ENTITY_WARDEN_SONIC_BOOM,SoundCategory.PLAYERS,4,.7f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,c.x,c.y+1,c.z,1,0,0,0,0);
        world.spawnParticles(ParticleTypes.SONIC_BOOM,c.x,c.y+1,c.z,1,0,0,0,0);
        EpicVfx.flash(world,c.x,c.y+1,c.z);
        EpicVfx.sphereBurst(world,ParticleTypes.PORTAL,c.x,c.y+1,c.z,60,.7);
        EpicVfx.sphereBurst(world,ParticleTypes.END_ROD,c.x,c.y+1,c.z,40,.5);
        EpicVfx.column(world,ParticleTypes.END_ROD,c.x,c.y,c.z,18.0,18);
        EpicVfx.shockwave(world,c.x,c.y,c.z,state.craterRadius);
    }

    /** Plays out the crater blast over several ticks (blocks are removed in batches); returns true when finished. */
    private static boolean tickCrater(PullState state){
        int age=state.blastAge++;
        int removed=0;Iterator<FallingBlockEntity> blocks=state.blocks.iterator();
        while(blocks.hasNext()&&removed<state.removeBatch){
            FallingBlockEntity shard=blocks.next();
            // The moon disintegrates into falling debris instead of vanishing.
            state.world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,shard.getBlockState()),shard.getX(),shard.getY(),shard.getZ(),3,.35,.35,.35,.12);
            shard.discard();blocks.remove();removed++;
        }
        ServerWorld world=state.world;Vec3d c=state.center;
        if(age%2==0&&age<=CRATER_TICKS){
            double progress=Math.min(1.0,(age+1)/(double)CRATER_TICKS);
            double r=state.craterRadius*(1.0-Math.pow(1.0-progress,2.0));
            int points=(int)Math.max(16,Math.min(40,16+r*2));
            BlockState ground=world.getBlockState(BlockPos.ofFloored(c.x,c.y-1,c.z));
            if(ground.isAir())ground=Blocks.DEEPSLATE.getDefaultState();
            for(int i=0;i<points;i++){
                double a=2.0*Math.PI*i/points;
                world.spawnParticles(ParticleTypes.CLOUD,c.x+Math.cos(a)*r,c.y+.2,c.z+Math.sin(a)*r,1,.1,.05,.1,.01);
                if(i%2==0)world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,ground),c.x+Math.cos(a)*r*.9,c.y+.3,c.z+Math.sin(a)*r*.9,2,.3,.3,.3,.15);
            }
            if(age==2||age==6)for(int k=0;k<2;k++){
                double a=ThreadLocalRandom.current().nextDouble(0,Math.PI*2),d=ThreadLocalRandom.current().nextDouble(.2,.8)*state.craterRadius;
                net.minecraft.entity.LightningEntity bolt=EntityType.LIGHTNING_BOLT.create(world,SpawnReason.TRIGGERED);
                if(bolt!=null){bolt.setCosmetic(true);bolt.refreshPositionAfterTeleport(c.x+Math.cos(a)*d,c.y,c.z+Math.sin(a)*d);world.spawnEntity(bolt);}
            }
        }
        return age>=CRATER_TICKS&&state.blocks.isEmpty();
    }

    private static void abortPull(MinecraftServer server,PullState state){
        for(FallingBlockEntity b:state.blocks)b.discard();
        state.blocks.clear();
        for(UUID id:state.weightless)if(restoreGravity(server,id,true)==null)PENDING_GRAVITY_RESTORE.put(id,72000);
    }

    public static void shutdown(MinecraftServer server){
        for(Erasure erasure:ACTIVE_ERASURES.values())for(SavedBlock b:erasure.blocks){
            if(b.world().isChunkLoaded(ChunkPos.toLong(b.pos()))&&b.world().getBlockState(b.pos()).isAir())b.world().setBlockState(b.pos(),b.state(),3);
        }
        ACTIVE_ERASURES.clear();
        for(PullState state:ACTIVE_PULLS.values())abortPull(server,state);
        ACTIVE_PULLS.clear();
        // Last chance before the world saves: restore anything still pending so nothing stays weightless.
        PENDING_GRAVITY_RESTORE.keySet().removeIf(id->restoreGravity(server,id,false)!=null);
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
