package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Persistent unlockable spirits, safe Shadow Step, and a timed spectral ultimate. */
public final class SpiritChannelerPower extends Power {
    private static final class Summon { final MobEntity mob; int ticks; final boolean ultimate;
        Summon(MobEntity mob, int ticks, boolean ultimate) { this.mob=mob; this.ticks=ticks; this.ultimate=ultimate; } }
    private final Map<UUID, String> selectedForm = new HashMap<>();
    private final Map<UUID, Integer> summonCooldown = new HashMap<>();
    private final Map<UUID, Integer> stepCooldown = new HashMap<>();
    private final Map<UUID, Integer> ultimateCooldown = new HashMap<>();
    private final Map<UUID, List<Summon>> summons = new HashMap<>();
    private final Map<UUID, Integer> warningTicks = new HashMap<>();

    @Override public PowerType getType() { return PowerType.SPIRIT_CHANNELER; }
    @Override public void onPrimaryActivate(ServerPlayerEntity player) { summonSpirit(player); }
    @Override public void onSecondaryActivate(ServerPlayerEntity player) { shadowStep(player); }
    @Override public void onTertiaryActivate(ServerPlayerEntity player) { activateRestlessShadows(player); }
    @Override public void onQuaternaryActivate(ServerPlayerEntity player) { cycleSpirit(player); }
    @Override public void onAlternateActivate(ServerPlayerEntity player) { cycleSpirit(player); }

    private Set<String> unlocks(ServerPlayerEntity player) {
        return PowerManager.get(player.getEntityWorld().getServer()).getSpiritChannelerUnlocks(player.getUuid());
    }

    public void recordKill(ServerPlayerEntity player, Entity killed) {
        if (player.getEntityWorld().getServer() == null) return;
        String form = null;
        net.minecraft.item.Item required = null;
        if (killed.getType() == EntityType.WOLF) { form = "WOLF"; required = Items.BONE; }
        else if (killed.getType() == EntityType.GUARDIAN) { form = "GUARDIAN"; required = Items.PRISMARINE_SHARD; }
        else if (killed.getType() == EntityType.WITHER_SKELETON) { form = "WRAITH"; required = Items.PHANTOM_MEMBRANE; }
        if (form == null) return;
        if (!consumeOne(player, required)) {
            player.sendMessage(Text.literal("Spirit trace found. Carry a " + required.getName().getString() + " to bind it permanently.").formatted(Formatting.GRAY), true);
            return;
        }
        boolean newUnlock = PowerManager.get(player.getEntityWorld().getServer()).unlockSpiritForm(player.getUuid(), form);
        if (newUnlock) player.sendMessage(Text.literal("Spirit Form unlocked permanently: " + form).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
    }

    private boolean consumeOne(ServerPlayerEntity player, net.minecraft.item.Item item) {
        for (int i=0; i<player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) { stack.decrement(1); return true; }
        }
        return false;
    }

    private void cycleSpirit(ServerPlayerEntity player) {
        List<String> forms = new ArrayList<>();
        for (String form : List.of("WOLF", "GUARDIAN", "WRAITH")) if (unlocks(player).contains(form)) forms.add(form);
        if (forms.isEmpty()) {
            player.sendMessage(Text.literal("No Spirit Forms unlocked. Kill a wolf + carry a bone, a guardian + prismarine shard, or a wither skeleton + phantom membrane.").formatted(Formatting.YELLOW), true);
            return;
        }
        String current = selectedForm.getOrDefault(player.getUuid(), forms.get(0));
        int index = forms.indexOf(current);
        selectedForm.put(player.getUuid(), forms.get((index+1+forms.size())%forms.size()));
        player.sendMessage(Text.literal("Selected Spirit: " + selectedForm.get(player.getUuid())).formatted(Formatting.LIGHT_PURPLE), true);
    }

    private void summonSpirit(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        UUID id=player.getUuid(); int cd=summonCooldown.getOrDefault(id,0);
        if (cd>0) { player.sendMessage(Text.literal("Spirit Summon cooldown: "+((cd+19)/20)+"s"),true); return; }
        Set<String> available=unlocks(player);
        String form=selectedForm.getOrDefault(id, available.stream().sorted().findFirst().orElse(""));
        if (form.isEmpty() || !available.contains(form)) { player.sendMessage(Text.literal("Unlock a Spirit Form before summoning.").formatted(Formatting.YELLOW),true); return; }
        List<Summon> list=summons.computeIfAbsent(id, ignored->new ArrayList<>());
        list.removeIf(s->s.mob.isRemoved()||!s.mob.isAlive());
        long active=list.stream().filter(s->!s.ultimate).count();
        if(active>=3){player.sendMessage(Text.literal("You already have three spirits active."),true);return;}
        MobEntity mob = switch(form) {
            case "GUARDIAN" -> EntityType.IRON_GOLEM.create(world, SpawnReason.TRIGGERED);
            case "WRAITH" -> EntityType.VEX.create(world, SpawnReason.TRIGGERED);
            default -> EntityType.WOLF.create(world, SpawnReason.TRIGGERED);
        };
        if(mob==null){player.sendMessage(Text.literal("Spirit could not manifest here."),true);return;}
        mob.refreshPositionAndAngles(player.getX()+1,player.getY(),player.getZ()+1,player.getYaw(),0);
        mob.setCustomName(Text.literal(form+" Spirit · "+player.getName().getString()).formatted(Formatting.LIGHT_PURPLE));
        mob.setCustomNameVisible(true); mob.addCommandTag("yppowers_spirit"); mob.addCommandTag("yppowers_owner_"+id); mob.setPersistent();
        double hp=form.equals("GUARDIAN")?80:form.equals("WRAITH")?18:30;
        if(mob.getAttributeInstance(EntityAttributes.MAX_HEALTH)!=null)mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(hp);
        mob.setHealth((float)hp);
        if(form.equals("WOLF") && mob instanceof WolfEntity wolf) wolf.setOwner(player);
        if(form.equals("GUARDIAN") && mob instanceof IronGolemEntity) mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        if(form.equals("WRAITH")) mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100000, 0, false, false));
        world.spawnEntity(mob); list.add(new Summon(mob,Math.max(20,YPConfig.getInstance().spirit_lifespan_ticks),false));
        summonCooldown.put(id,Math.max(0,YPConfig.getInstance().spirit_summon_cooldown_ticks));
        world.spawnParticles(ParticleTypes.SOUL, player.getX(), player.getBodyY(.6), player.getZ(), 25,.5,.5,.5,.04);
        EpicVfx.activation(world, player.getX(), player.getY(), player.getZ(), 0.9);
    }

    private void shadowStep(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        UUID id=player.getUuid(); int cd=stepCooldown.getOrDefault(id,0);
        if(cd>0){player.sendMessage(Text.literal("Shadow Step cooldown: "+((cd+19)/20)+"s"),true);return;}
        Vec3d look=PowerCombatUtil.safeLook(player); double range=Math.max(1,YPConfig.getInstance().spirit_shadow_step_range);
        BlockPos base=BlockPos.ofFloored(player.getEntityPos().add(look.multiply(range)));
        BlockPos safe=findSafeSpot(world,base,3);
        if(safe==null){player.sendMessage(Text.literal("No safe destination in range."),true);return;}
        world.spawnParticles(ParticleTypes.PORTAL,player.getX(),player.getY()+.8,player.getZ(),30,.4,.6,.4,.2);
        player.teleport(world,safe.getX()+.5,safe.getY(),safe.getZ()+.5,Set.of(),player.getYaw(),player.getPitch(),false);
        world.spawnParticles(ParticleTypes.PORTAL,player.getX(),player.getY()+.8,player.getZ(),30,.4,.6,.4,.2);
        stepCooldown.put(id,Math.max(0,YPConfig.getInstance().spirit_shadow_step_cooldown_ticks));
    }

    private BlockPos findSafeSpot(ServerWorld world, BlockPos base, int radius) {
        for(int dy=2;dy>=-2;dy--) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            BlockPos p=base.add(dx,dy,dz);
            if(!world.getWorldBorder().contains(p))continue;
            if(world.getBlockState(p).isAir()&&world.getBlockState(p.up()).isAir()&&world.getBlockState(p.down()).isSolidBlock(world,p.down())) return p;
        }
        return null;
    }

    /** Called only by the network special-case so an underfunded ultimate still manifests. */
    public void activateRestlessShadows(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer()==null) return;
        UUID id=player.getUuid(); int cd=ultimateCooldown.getOrDefault(id,0);
        if(cd>0){player.sendMessage(Text.literal("Restless Shadows cooldown: "+((cd+19)/20)+"s"),true);return;}
        VexEntity shade=EntityType.VEX.create(world,SpawnReason.TRIGGERED);
        if(shade==null){player.sendMessage(Text.literal("Restless Shadows could not manifest; no Energy was spent."),true);return;}
        double cost=Math.max(0,YPConfig.getInstance().spirit_restless_shadows_energy_cost);
        double energy=EnergyManager.get(world.getServer()).getEnergy(id);
        boolean catastrophic=energy+1.0e-6<cost;
        if(catastrophic) {
            EnergyManager.get(world.getServer()).setEnergy(id,0);
        } else {
            if(!EnergyManager.get(world.getServer()).trySpendDirect(player,cost,"Restless Shadows"))return;
        }
        int duration=Math.max(20,YPConfig.getInstance().spirit_restless_shadows_duration_ticks);
        List<Summon> list=summons.computeIfAbsent(id,ignored->new ArrayList<>());
        shade.refreshPositionAndAngles(player.getX(),player.getY()+1,player.getZ(),player.getYaw(),0);
        shade.setInvisible(true); shade.setCustomName(Text.literal("Restless Shadow").formatted(Formatting.DARK_PURPLE));
        shade.setCustomNameVisible(false); shade.addCommandTag("yppowers_restless_shadow"); shade.addCommandTag("yppowers_owner_"+id); shade.setPersistent();
        shade.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH,duration,2,false,false));
        shade.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(40.0); shade.setHealth(40.0f);
        world.spawnEntity(shade); list.add(new Summon(shade,duration,true));
        ultimateCooldown.put(id,Math.max(0,YPConfig.getInstance().spirit_restless_shadows_cooldown_ticks));
        if(catastrophic) {
            warningTicks.put(id,100);
            com.yppowers.network.YPNetworking.syncHudWarning(player, "ARE YOU SURE?", 100);
            player.sendMessage(Text.literal("ARE YOU SURE?").formatted(Formatting.RED,Formatting.BOLD),true);
            double radius=Math.max(1,YPConfig.getInstance().spirit_restless_shadows_radius);
            Box area=player.getBoundingBox().expand(radius);
            for(Entity e:world.getOtherEntities(player,area,x->x.isAlive())) {
                e.damage(world,world.getDamageSources().magic(),8.0f);
                Vec3d dir=e.getEntityPos().subtract(player.getEntityPos()).normalize();
                e.addVelocity(dir.x*3.0,1.2,dir.z*3.0); e.velocityDirty=true;
            }
            player.damage(world,world.getDamageSources().magic(),8.0f);
            Vec3d own=player.getRotationVec(1).multiply(-3).add(0,1.2,0); player.addVelocity(own); player.velocityDirty=true;
            world.spawnParticles(ParticleTypes.SONIC_BOOM,player.getX(),player.getY()+.5,player.getZ(),1,0,0,0,0);
            world.playSound(null,player.getBlockPos(),SoundEvents.ENTITY_WARDEN_SONIC_BOOM,SoundCategory.PLAYERS,2,0.5f);
            player.sendMessage(Text.literal("The shadow is uncontrolled! Energy collapsed and a shockwave erupted.").formatted(Formatting.DARK_RED),false);
        } else {
            player.sendMessage(Text.literal("Restless Shadows is hunting your enemies."),true);
            world.playSound(null,player.getBlockPos(),SoundEvents.BLOCK_END_PORTAL_SPAWN,SoundCategory.PLAYERS,1.5f,.6f);
        }
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID id=player.getUuid(); decrement(summonCooldown,id);decrement(stepCooldown,id);decrement(ultimateCooldown,id);
        int warning=warningTicks.getOrDefault(id,0); if(warning>0) warningTicks.put(id,warning-1);
        if(!(player.getEntityWorld() instanceof ServerWorld world)||world.getServer()==null)return;
        List<Summon> list=summons.get(id);if(list==null)return;
        AllianceManager allies=AllianceManager.get(world.getServer());
        list.removeIf(s->{
            if(s.mob.isRemoved()||!s.mob.isAlive()||--s.ticks<=0){s.mob.discard();return true;}
            if(s.ticks%10==0){
                if(GraceManager.isActive(world.getServer())) { s.mob.setTarget(null); return false; }
                LivingEntity target=null;double best=24*24;
                Box box=s.mob.getBoundingBox().expand(24);
                for(LivingEntity e:world.getEntitiesByClass(LivingEntity.class,box,x->x!=player&&x!=s.mob&&x.isAlive())){
                    boolean ally=e instanceof ServerPlayerEntity p&&allies.isAlly(player,p);
                    if(ally)continue;
                    double d=s.mob.squaredDistanceTo(e);if(d<best){target=e;best=d;}
                }
                s.mob.setTarget(target);
                if(target==null&&s.mob.squaredDistanceTo(player)>100)s.mob.getNavigation().startMovingTo(player,1.1);
                if(target==null)s.mob.setTarget(null);
                if(s.ultimate&&s.ticks%5==0)world.spawnParticles(ParticleTypes.WITCH,s.mob.getX(),s.mob.getY()+.5,s.mob.getZ(),5,.3,.3,.3,.02);
            }
            return false;
        });
        if(list.isEmpty())summons.remove(id);
    }
    private static void decrement(Map<UUID,Integer> map,UUID id){int n=map.getOrDefault(id,0);if(n>0)map.put(id,n-1);}
    @Override public void onPlayerDeath(ServerPlayerEntity player){clear(player.getUuid());}
    @Override public void onRemove(ServerPlayerEntity player){clear(player.getUuid());}
    private void clear(UUID id){List<Summon> list=summons.remove(id);if(list!=null)for(Summon s:list)s.mob.discard();warningTicks.remove(id);summonCooldown.remove(id);stepCooldown.remove(id);ultimateCooldown.remove(id);}
}
