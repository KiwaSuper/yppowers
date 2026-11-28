package com.yppowers.power.shadowarcher;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import com.yppowers.system.TargetingUtil;
import com.yppowers.system.EpicVfx;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.artifact.GrimoireFlightManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.item.YPItemData;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.mixin.PersistentProjectileDamageAccessor;
import com.yppowers.mixin.PersistentProjectilePierceInvoker;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import com.yppowers.system.PowerCombatUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DragonBreathParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.WeightedPool;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import net.minecraft.world.World.ExplosionSourceType;

public class ShadowArcherPower extends Power {
   private final Map<UUID, ArrowType> selectedArrow = new HashMap<>();
   private final Map<UUID, Set<ArrowType>> unlockedArrows = new HashMap<>();
   private final Map<UUID, Integer> morphCooldowns = new HashMap<>();
   private final Map<UUID, EntityType<?>> currentMorph = new HashMap<>();
   private final Map<UUID, ArrowType> arrowTypes = new HashMap<>();
   private final Set<UUID> processedArrows = new HashSet<>();
   private final Map<UUID, UUID> arrowOwners = new HashMap<>();
   private final Map<UUID, Entity> morphDisguises = new HashMap<>();
   private final Map<UUID, Double> morphReachBonus = new HashMap<>();
   private static final Identifier MORPH_REACH_ID = Identifier.of("yppowers", "shadow_archer_morph_reach");
   private static final float DEFAULT_FLY_SPEED = 0.05F;
   private final Map<UUID, ShadowArcherPower.MorphViewState> syncedMorphViews = new HashMap<>();
   private long lastArrowCleanupTick = Long.MIN_VALUE;
   private final Map<UUID, Integer> railshotHeldTicks = new HashMap<>();
   private final Map<UUID, Integer> railshotStreak = new HashMap<>();
   private final Map<UUID, Boolean> railshotWasHolding = new HashMap<>();
   private final Map<UUID, Integer> railshotArrowLevel = new HashMap<>();
   private final Map<UUID, ShadowArcherPower.TracerMark> tracerMarks = new HashMap<>();
   private final Map<UUID, Double> phaseArrowTravel = new HashMap<>();
   private final Map<UUID, UUID> latestArrow = new HashMap<>();
   private final Set<UUID> arrowsThatHit = new HashSet<>();
   private final Map<UUID, Long> arrowOrder = new HashMap<>();
   private long arrowSequence;
   private final Map<UUID, Integer> arrowSwapCooldowns = new HashMap<>();
   public static final String BOW_FLAG = "YPShadowBow";
   private final Map<UUID, EnumMap<ShadowArcherMorphRegistry.Ability, Integer>> morphAbilityCooldowns = new HashMap<>();
   private final Map<UUID, Map<RegistryEntry<StatusEffect>, StatusEffectInstance>> morphSavedEffects = new HashMap<>();
   private final Map<UUID, ShadowArcherPower.FlightSnapshot> morphSavedFlight = new HashMap<>();
   private final Map<UUID, Text> morphSavedNames = new HashMap<>();
   private final Map<UUID, Boolean> morphSavedNameVisibility = new HashMap<>();
   private final Set<UUID> morphStateInitialized = new HashSet<>();
   private static final List<RegistryEntry<StatusEffect>> MORPH_MANAGED_EFFECTS = List.of(
      StatusEffects.INVISIBILITY,
      StatusEffects.SPEED,
      StatusEffects.STRENGTH,
      StatusEffects.JUMP_BOOST,
      StatusEffects.RESISTANCE,
      StatusEffects.FIRE_RESISTANCE,
      StatusEffects.WATER_BREATHING,
      StatusEffects.DOLPHINS_GRACE,
      StatusEffects.NIGHT_VISION,
      StatusEffects.SLOW_FALLING,
      StatusEffects.REGENERATION,
      StatusEffects.POISON,
      StatusEffects.WEAKNESS,
      StatusEffects.SLOWNESS,
      StatusEffects.BLINDNESS,
      StatusEffects.LEVITATION,
      StatusEffects.GLOWING
   );

   @Override
   public PowerType getType() {
      return PowerType.SHADOW_ARCHER;
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      this.initializePlayer(player);
      this.ensureBow(player);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.initializePlayer(player);
      this.ensureBow(player);
   }

   private void initializePlayer(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      Set<ArrowType> unlocked = this.unlockedArrows.computeIfAbsent(uuid, k -> new HashSet<>());
      unlocked.add(ArrowType.SPLIT_SHOT);
      if (player.getEntityWorld().getServer() != null) {
         unlocked.addAll(PowerManager.get(player.getEntityWorld().getServer()).getShadowArcherUnlockedArrows(uuid));
      }

      this.selectedArrow.putIfAbsent(uuid, ArrowType.SPLIT_SHOT);
      YPNetworking.syncShadowArcherMorphCatalog(player);
      this.syncMorphView(player, false, -1, 1.62F, 1.8F, true);
   }

   public static boolean specialArrowsEnabled(ServerPlayerEntity player) {
      return player != null && player.getEntityWorld().getServer() != null
         ? !PowerManager.get(player.getEntityWorld().getServer()).areShadowArcherSpecialArrowsDisabled(player.getUuid())
         : true;
   }

   public void toggleSpecialArrows(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         boolean nowDisabled = !manager.areShadowArcherSpecialArrowsDisabled(player.getUuid());
         manager.setShadowArcherSpecialArrowsEnabled(player.getUuid(), !nowDisabled);
         player.sendMessage(
            Text.literal(nowDisabled ? "Special arrows OFF — your bow now fires ordinary arrows." : "Special arrows ON — the selected special arrow is back.")
               .formatted(nowDisabled ? Formatting.GRAY : Formatting.LIGHT_PURPLE),
            true
         );
      }
   }

   public void cycleBowEnchantMode(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         String next = "MENDING".equals(manager.getShadowArcherBowMode(player.getUuid())) ? "INFINITY" : "MENDING";
         manager.setShadowArcherBowMode(player.getUuid(), next);
         this.applyBowEnchantments(player);
         player.sendMessage(
            Text.literal(
                  "Shadow Bow enchant set: "
                     + (
                        "MENDING".equals(next)
                           ? "Mending I (Power V, Punch II, Flame I, Unbreaking III)"
                           : "Infinity I (Power V, Punch II, Flame I, Unbreaking III)"
                     )
               )
               .formatted(Formatting.DARK_PURPLE),
            true
         );
         player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8F, 1.2F);
      }
   }

   private static boolean isShadowBow(ItemStack stack) {
      return stack.isOf(Items.BOW) && YPItemData.hasFlag(stack, "YPShadowBow");
   }

   private boolean ensureBow(ServerPlayerEntity player) {
      for (int i = 0; i < player.getInventory().size(); i++) {
         if (isShadowBow(player.getInventory().getStack(i))) {
            this.applyBowEnchantments(player);
            return false;
         }
      }

      ItemStack bow = new ItemStack(Items.BOW);
      YPItemData.setFlag(bow, "YPShadowBow", true);
      bow.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Duskfall").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC));
      this.applyBowEnchantments(player, bow);
      player.giveOrDropStack(bow);
      return true;
   }

   private void applyBowEnchantments(ServerPlayerEntity player) {
      for (int i = 0; i < player.getInventory().size(); i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (isShadowBow(stack)) {
            this.applyBowEnchantments(player, stack);
         }
      }
   }

   private void applyBowEnchantments(ServerPlayerEntity player, ItemStack bow) {
      YPConfig cfg = YPConfig.getInstance();
      String mode = player.getEntityWorld().getServer() == null
         ? cfg.shadow_archer_bow_default_mode
         : PowerManager.get(player.getEntityWorld().getServer()).getShadowArcherBowMode(player.getUuid());
      Registry<Enchantment> enchantments = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
      bow.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
      EnchantmentHelper.apply(bow, builder -> {
         if (cfg.shadow_archer_bow_power_level > 0) {
            builder.add(enchantments.getOrThrow(Enchantments.POWER), cfg.shadow_archer_bow_power_level);
         }

         if (cfg.shadow_archer_bow_punch_level > 0) {
            builder.add(enchantments.getOrThrow(Enchantments.PUNCH), cfg.shadow_archer_bow_punch_level);
         }

         if (cfg.shadow_archer_bow_flame_level > 0) {
            builder.add(enchantments.getOrThrow(Enchantments.FLAME), cfg.shadow_archer_bow_flame_level);
         }

         if (cfg.shadow_archer_bow_unbreaking_level > 0) {
            builder.add(enchantments.getOrThrow(Enchantments.UNBREAKING), cfg.shadow_archer_bow_unbreaking_level);
         }

         builder.add(enchantments.getOrThrow("MENDING".equals(mode) ? Enchantments.MENDING : Enchantments.INFINITY), 1);
      });
   }

   public void teleportToLatestArrow(ServerPlayerEntity player) {
      ServerWorld uuid = player.getEntityWorld();
      if (uuid instanceof ServerWorld) {
         UUID var12 = player.getUuid();
         int cooldown = this.arrowSwapCooldowns.getOrDefault(var12, 0);
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Arrow Swap cooldown: " + (cooldown + 19) / 20 + "s").formatted(Formatting.YELLOW), true);
         } else {
            ArrowEntity arrow = this.findLatestEligibleArrow(player, uuid);
            if (arrow == null) {
               this.latestArrow.remove(var12);
               player.sendMessage(Text.literal("You have no arrow in flight that has not hit a target.").formatted(Formatting.GRAY), true);
            } else {
               this.latestArrow.put(var12, arrow.getUuid());
               YPConfig cfg = YPConfig.getInstance();
               double maxDistance = Math.max(8.0, cfg.shadow_archer_arrow_swap_max_distance);
               if (player.squaredDistanceTo(arrow) > maxDistance * maxDistance) {
                  player.sendMessage(Text.literal("That arrow is too far away to swap with.").formatted(Formatting.RED), true);
               } else {
                  Vec3d destination = TargetingUtil.findSafeLandingNear(uuid, player, arrow.getEntityPos());
                  if (destination == null) {
                     player.sendMessage(Text.literal("There is no safe space at the arrow's position.").formatted(Formatting.RED), true);
                  } else if (EnergyManager.get(uuid.getServer()).trySpendDirect(player, cfg.shadow_archer_arrow_swap_energy_cost, "Arrow Swap")) {
                     Vec3d origin = player.getEntityPos();
                     Vec3d arrowVelocity = arrow.getVelocity();
                     uuid.spawnParticles(ParticleTypes.PORTAL, true, true, player.getX(), player.getY() + 0.9, player.getZ(), 26, 0.4, 0.6, 0.4, 0.2);
                     EpicVfx.beam(uuid, ParticleTypes.SOUL_FIRE_FLAME, origin.add(0.0, 1.0, 0.0), arrow.getEntityPos(), 0.35);
                     player.teleport(uuid, destination.x, destination.y, destination.z, Set.of(), player.getYaw(), player.getPitch(), false);
                     arrow.setPosition(origin.x, origin.y + 1.2, origin.z);
                     arrow.setVelocity(arrowVelocity);
                     ((EntityVelocityAccessor)arrow).invokeYppowersScheduleVelocityUpdate();
                     EpicVfx.cue(
                        uuid,
                        player.getId(),
                        "shadow_archer",
                        "swap",
                        player.getX(),
                        player.getY() + 0.9,
                        player.getZ(),
                        destination.subtract(origin),
                        1.0F,
                        18,
                        0.0F
                     );
                     EpicVfx.cue(
                        uuid, player.getId(), "shadow_archer", "swap", arrow.getX(), arrow.getY(), arrow.getZ(), origin.subtract(destination), 1.0F, 18, 0.0F
                     );
                     uuid.spawnParticles(ParticleTypes.PORTAL, true, true, player.getX(), player.getY() + 0.9, player.getZ(), 26, 0.4, 0.6, 0.4, 0.2);
                     uuid.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, arrow.getX(), arrow.getY(), arrow.getZ(), 18, 0.25, 0.25, 0.25, 0.03);
                     EpicVfx.activation(uuid, player.getX(), player.getY(), player.getZ(), 0.7);
                     uuid.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.9F, 1.35F);
                     uuid.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.PLAYERS, 0.7F, 1.2F);
                     this.arrowSwapCooldowns.put(var12, Math.max(0, cfg.shadow_archer_arrow_swap_cooldown_ticks));
                  }
               }
            }
         }
      }
   }

   private ArrowEntity findLatestEligibleArrow(ServerPlayerEntity player, ServerWorld world) {
      UUID uuid = player.getUuid();
      ArrowEntity best = null;
      long bestOrder = Long.MIN_VALUE;

      for (Entry<UUID, UUID> entry : this.arrowOwners.entrySet()) {
         if (uuid.equals(entry.getValue())
            && !this.arrowsThatHit.contains(entry.getKey())
            && world.getEntity(entry.getKey()) instanceof ArrowEntity candidate
            && !candidate.isRemoved()
            && candidate.getOwner() == player) {
            long order = this.arrowOrder.getOrDefault(entry.getKey(), 0L);
            if (order > bestOrder) {
               bestOrder = order;
               best = candidate;
            }
         }
      }

      return best;
   }

   private void markArrowHit(ArrowEntity arrow) {
      UUID arrowId = arrow.getUuid();
      this.arrowsThatHit.add(arrowId);
      this.latestArrow.values().removeIf(arrowId::equals);
   }

   private void rememberArrow(ServerPlayerEntity player, ArrowEntity arrow) {
      this.latestArrow.put(player.getUuid(), arrow.getUuid());
      this.arrowsThatHit.remove(arrow.getUuid());
      this.arrowOrder.put(arrow.getUuid(), ++this.arrowSequence);
   }

   @Override
   public void onQuaternaryActivate(ServerPlayerEntity player) {
      List<ArrowType> list = this.getAvailableArrowTypes(player);
      ArrowType current = this.selectedArrow.getOrDefault(player.getUuid(), ArrowType.SPLIT_SHOT);
      int index = list.indexOf(current);
      if (index < 0) {
         index = 0;
      }

      if (list.size() == 1) {
         player.sendMessage(
            Text.literal(
               "Only Split Shot is unlocked. Carry 8 arrows plus a themed item to unlock more: Eye of Ender = Homing, TNT = TNT, Lightning Rod = Lightning, Packed Ice = Ice Prison, Lead = Pinning, Obsidian = Phase, Compass = Tracer, Black Dye = Eclipse."
            ),
            true
         );
         this.selectedArrow.put(player.getUuid(), ArrowType.SPLIT_SHOT);
      } else {
         this.selectArrowType(player, list.get((index + 1) % list.size()).name(), false);
      }
   }

   public List<ArrowType> getAvailableArrowTypes(ServerPlayerEntity player) {
      Set<ArrowType> unlocked = this.unlockedArrows.computeIfAbsent(player.getUuid(), ignored -> new HashSet<>());
      unlocked.add(ArrowType.SPLIT_SHOT);
      if (player.getEntityWorld().getServer() != null) {
         unlocked.addAll(PowerManager.get(player.getEntityWorld().getServer()).getShadowArcherUnlockedArrows(player.getUuid()));
      }

      return Arrays.stream(ArrowType.values()).filter(unlocked::contains).toList();
   }

   public ArrowType getSelectedArrowType(ServerPlayerEntity player) {
      return this.selectedArrow.getOrDefault(player.getUuid(), ArrowType.SPLIT_SHOT);
   }

   public boolean selectArrowType(ServerPlayerEntity player, String requested) {
      return this.selectArrowType(player, requested, true);
   }

   private boolean selectArrowType(ServerPlayerEntity player, String requested, boolean checkGrace) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (manager.getPowerType(player.getUuid()) == PowerType.SHADOW_ARCHER
            && manager.isPowerEnabled(player, PowerType.SHADOW_ARCHER)
            && (!checkGrace || !GraceManager.blockAbility(player))) {
            ArrowType next;
            try {
               next = ArrowType.valueOf(requested == null ? "" : requested.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var7) {
               return false;
            }

            if (!this.getAvailableArrowTypes(player).contains(next)) {
               player.sendMessage(Text.literal("That arrow type is still locked.").formatted(Formatting.YELLOW), true);
               return false;
            } else {
               this.selectedArrow.put(player.getUuid(), next);
               this.railshotHeldTicks.remove(player.getUuid());
               this.railshotStreak.remove(player.getUuid());
               this.railshotWasHolding.remove(player.getUuid());
               player.sendMessage(Text.literal("Selected Arrow: " + next.getDisplayName()), true);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      player.sendMessage(Text.literal("Open the Shadow Archer morph menu with V."), true);
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      this.activateMorphSlot(player, 0);
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      this.activateMorphSlot(player, 1);
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      this.activateMorphSlot(player, 2);
   }

   public void requestMorph(ServerPlayerEntity player, String requestedName) {
      UUID uuid = player.getUuid();
      String requested = requestedName == null ? "" : requestedName.trim();
      if (!requested.equalsIgnoreCase("human") && !requested.equalsIgnoreCase("reset") && !requested.equalsIgnoreCase("none")) {
         int cooldown = this.morphCooldowns.getOrDefault(uuid, 0);
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Morph on cooldown: " + (cooldown + 19) / 20 + "s"), true);
         } else if (requested.isEmpty()) {
            player.sendMessage(Text.literal("Enter a mob name or registry ID."), true);
         } else {
            EntityType<?> matched = this.findKilledMob(player, requested);
            if (matched == null) {
               player.sendMessage(Text.literal("You must defeat that mob before you can morph into it."), true);
            } else if (matched.equals(this.currentMorph.get(uuid))) {
               player.sendMessage(Text.literal("You are already morphed into " + matched.getName().getString() + "."), true);
            } else {
               boolean wasMorphed = this.currentMorph.containsKey(uuid);
               this.endMorph(player);
               if (wasMorphed) {
                  ServerWorld var9 = player.getEntityWorld();
                  if (var9 instanceof ServerWorld) {
                     EpicVfx.implosion(var9, ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 1.8, 18, 0.16);
                  }
               }

               this.currentMorph.put(uuid, matched);
               this.beginMorphState(player);
               this.startMorphCooldown(uuid);
               this.refreshDisguise(player, matched);
               if (matched.equals(this.currentMorph.get(uuid))) {
                  ServerWorld var12 = player.getEntityWorld();
                  if (var12 instanceof ServerWorld) {
                     EpicVfx.activation(var12, player.getX(), player.getY(), player.getZ(), 0.9);
                  }
               }

               ShadowArcherMorphRegistry.Profile profile = ShadowArcherMorphRegistry.forEntityType(matched);
               player.sendMessage(
                  Text.literal(
                     "Morphed into "
                        + profile.displayName()
                        + ". G: "
                        + profile.primary().displayName()
                        + " | H: "
                        + profile.secondary().displayName()
                        + " | R: "
                        + profile.alternate().displayName()
                  ),
                  true
               );
            }
         }
      } else {
         boolean wasMorphedx = this.currentMorph.containsKey(uuid);
         this.endMorph(player);
         if (wasMorphedx) {
            ServerWorld wasMorphedxx = player.getEntityWorld();
            if (wasMorphedxx instanceof ServerWorld) {
               EpicVfx.implosion(wasMorphedxx, ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 1.8, 18, 0.16);
            }
         }

         this.startMorphCooldown(uuid);
         player.sendMessage(Text.literal("Returned to human form."), true);
      }
   }

   private void startMorphCooldown(UUID uuid) {
      int ticks = Math.max(0, YPConfig.getInstance().shadow_archer_morph_cooldown_ticks);
      if (ticks == 0) {
         this.morphCooldowns.remove(uuid);
      } else {
         this.morphCooldowns.put(uuid, ticks);
      }
   }

   public boolean isInFlyingMorph(UUID playerId) {
      EntityType<?> morph = this.currentMorph.get(playerId);
      return morph != null && ShadowArcherMorphRegistry.forEntityType(morph).traits().contains(ShadowArcherMorphRegistry.Trait.FLIGHT);
   }

   private EntityType<?> findKilledMob(ServerPlayerEntity player, String requested) {
      if (player.getEntityWorld().getServer() == null) {
         return null;
      } else {
         Set<String> killedIds = PowerManager.get(player.getEntityWorld().getServer()).getShadowArcherKills(player.getUuid());
         String normalized = requested.toLowerCase(Locale.ROOT).replace(' ', '_');
         Identifier requestedId = Identifier.tryParse(normalized.contains(":") ? normalized : "minecraft:" + normalized);
         if (requestedId != null) {
            String canonical = requestedId.toString();
            if (killedIds.contains(canonical) && "minecraft".equals(requestedId.getNamespace())) {
               EntityType<?> direct = Registries.ENTITY_TYPE.getOptionalValue(requestedId).orElse(null);
               if (direct != null && ShadowArcherMorphRegistry.getVanillaFormIds().contains(requestedId.getPath())) {
                  return direct;
               }
            }
         }

         for (String rawId : killedIds) {
            Identifier id = Identifier.tryParse(rawId);
            if (id != null) {
               EntityType<?> type = Registries.ENTITY_TYPE.getOptionalValue(id).orElse(null);
               if (type != null
                  && "minecraft".equals(id.getNamespace())
                  && ShadowArcherMorphRegistry.getVanillaFormIds().contains(id.getPath())
                  && (
                     normalized.equals(rawId.toLowerCase(Locale.ROOT))
                        || normalized.equals(id.getPath().toLowerCase(Locale.ROOT))
                        || requested.equalsIgnoreCase(type.getName().getString())
                  )) {
                  return type;
               }
            }
         }

         return null;
      }
   }

   public void recordKill(ServerPlayerEntity player, EntityType<?> entityType) {
      if (entityType != null && player.getEntityWorld().getServer() != null) {
         PowerManager.get(player.getEntityWorld().getServer()).recordShadowArcherKill(player.getUuid(), entityType);
         YPNetworking.syncShadowArcherMorphCatalog(player);
      }
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.endMorph(player);
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      UUID uuid = player.getUuid();
      this.currentMorph.remove(uuid);
      Entity oldDisguise = this.morphDisguises.remove(uuid);
      if (oldDisguise != null && !oldDisguise.isRemoved()) {
         oldDisguise.discard();
      }

      this.morphSavedEffects.remove(uuid);
      this.morphSavedFlight.remove(uuid);
      if (!player.isCreative() && !player.isSpectator()) {
         MinecraftServer server = player.getEntityWorld().getServer();
         boolean keepFlight = server != null && GrimoireFlightManager.hasAnyActiveModFlight(player, PowerManager.get(server));
         if (!keepFlight) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.getAbilities().setFlySpeed(0.05F);
            player.sendAbilitiesUpdate();
         }
      }

      this.clearMorphReach(player);
      if (this.morphSavedNames.containsKey(uuid)) {
         player.setCustomName(this.morphSavedNames.remove(uuid));
      }

      Boolean visible = this.morphSavedNameVisibility.remove(uuid);
      if (visible != null) {
         player.setCustomNameVisible(visible);
      }

      this.morphStateInitialized.remove(uuid);
      this.ensureBow(player);
   }

   public static void shutdown(MinecraftServer server) {
      if (server == null) {
         return;
      }
      if (PowerManager.get(server).getPowerInstance(PowerType.SHADOW_ARCHER) instanceof ShadowArcherPower archer) {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            archer.endMorph(player, false);
         }
         for (Entity disguise : archer.morphDisguises.values()) {
            if (disguise != null && !disguise.isRemoved()) {
               disguise.discard();
            }
         }
         archer.currentMorph.clear();
         archer.morphDisguises.clear();
         archer.morphSavedEffects.clear();
         archer.morphSavedFlight.clear();
         archer.morphSavedNames.clear();
         archer.morphSavedNameVisibility.clear();
         archer.morphStateInitialized.clear();
         archer.morphReachBonus.clear();
         archer.syncedMorphViews.clear();
         archer.arrowTypes.clear();
         archer.arrowOwners.clear();
         archer.processedArrows.clear();
         archer.phaseArrowTravel.clear();
         archer.arrowsThatHit.clear();
         archer.arrowOrder.clear();
         archer.latestArrow.clear();
         archer.railshotArrowLevel.clear();
         archer.tracerMarks.clear();
      }
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.endMorph(player, false);
      this.syncedMorphViews.remove(player.getUuid());
      this.railshotHeldTicks.remove(player.getUuid());
      this.railshotWasHolding.remove(player.getUuid());
      this.railshotStreak.remove(player.getUuid());
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      this.endMorph(player);
      this.selectedArrow.remove(player.getUuid());
      this.unlockedArrows.remove(player.getUuid());
      this.morphCooldowns.remove(player.getUuid());
      this.morphAbilityCooldowns.remove(player.getUuid());
      this.arrowSwapCooldowns.remove(player.getUuid());
      this.latestArrow.remove(player.getUuid());
      this.railshotHeldTicks.remove(player.getUuid());
      this.railshotStreak.remove(player.getUuid());
      this.railshotWasHolding.remove(player.getUuid());
   }

   @Override
   public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
      return isShadowBow(stack);
   }

   @Override
   public List<String> ensureSpecialGear(ServerPlayerEntity player) {
      return this.ensureBow(player) ? List.of("Duskfall (Shadow Bow)") : List.of();
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.morphCooldowns.remove(playerId);
      this.morphAbilityCooldowns.remove(playerId);
      this.arrowSwapCooldowns.remove(playerId);
      this.railshotHeldTicks.remove(playerId);
      this.railshotStreak.remove(playerId);
      this.railshotWasHolding.remove(playerId);
   }

   private void beginMorphState(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (!this.morphStateInitialized.contains(uuid)) {
         Map<RegistryEntry<StatusEffect>, StatusEffectInstance> snapshot = new HashMap<>();

         for (RegistryEntry<StatusEffect> effect : MORPH_MANAGED_EFFECTS) {
            StatusEffectInstance existing = player.getStatusEffect(effect);
            if (existing != null) {
               snapshot.put(effect, new StatusEffectInstance(existing));
            }
         }

         this.morphSavedEffects.put(uuid, snapshot);
         this.morphSavedFlight.put(uuid, new ShadowArcherPower.FlightSnapshot(player.getAbilities().allowFlying, player.getAbilities().flying));
         this.morphSavedNames.put(uuid, player.getCustomName());
         this.morphSavedNameVisibility.put(uuid, player.isCustomNameVisible());
         this.morphStateInitialized.add(uuid);
      }
   }

   private void endMorph(ServerPlayerEntity player) {
      this.endMorph(player, true);
   }

   private void endMorph(ServerPlayerEntity player, boolean sendViewPacket) {
      UUID uuid = player.getUuid();
      this.currentMorph.remove(uuid);
      Entity disguise = this.morphDisguises.remove(uuid);
      if (disguise != null && !disguise.isRemoved()) {
         disguise.discard();
      }

      if (this.morphStateInitialized.remove(uuid)) {
         for (RegistryEntry<StatusEffect> effect : MORPH_MANAGED_EFFECTS) {
            player.removeStatusEffect(effect);
         }

         Map<RegistryEntry<StatusEffect>, StatusEffectInstance> saved = this.morphSavedEffects.remove(uuid);
         if (saved != null) {
            for (Entry<RegistryEntry<StatusEffect>, StatusEffectInstance> entry : saved.entrySet()) {
               player.addStatusEffect(new StatusEffectInstance(entry.getValue()));
            }
         }

         ShadowArcherPower.FlightSnapshot flight = this.morphSavedFlight.remove(uuid);
         if (flight != null && !player.isCreative() && !player.isSpectator()) {
            MinecraftServer server = player.getEntityWorld().getServer();
            boolean keepFlight = server != null && GrimoireFlightManager.hasAnyActiveModFlight(player, PowerManager.get(server));
            if (!keepFlight) {
               player.getAbilities().allowFlying = false;
               player.getAbilities().flying = false;
               player.getAbilities().setFlySpeed(0.05F);
               player.sendAbilitiesUpdate();
            }
         }

         if (this.morphSavedNames.containsKey(uuid)) {
            player.setCustomName(this.morphSavedNames.remove(uuid));
         }

         Boolean visible = this.morphSavedNameVisibility.remove(uuid);
         if (visible != null) {
            player.setCustomNameVisible(visible);
         }

         this.clearMorphReach(player);
      } else {
         this.morphSavedEffects.remove(uuid);
         this.morphSavedFlight.remove(uuid);
         this.morphSavedNames.remove(uuid);
         this.morphSavedNameVisibility.remove(uuid);
      }

      this.syncMorphView(player, false, -1, 1.62F, 1.8F, sendViewPacket);
   }

   private void refreshDisguise(ServerPlayerEntity player, EntityType<?> type) {
      UUID uuid = player.getUuid();
      Entity disguise = this.morphDisguises.get(uuid);
      if (disguise == null || disguise.isRemoved() || disguise.getEntityWorld() != player.getEntityWorld() || !type.equals(disguise.getType())) {
         if (disguise != null && !disguise.isRemoved()) {
            disguise.discard();
         }

         Entity replacement = type.create(player.getEntityWorld(), SpawnReason.MOB_SUMMONED);
         if (replacement == null) {
            this.endMorph(player);
            player.sendMessage(Text.literal("That mob cannot be used as a morph."), true);
            return;
         }

         replacement.setNoGravity(true);
         replacement.setInvulnerable(true);
         replacement.setSilent(true);
         replacement.setCustomName(null);
         replacement.addCommandTag("yppowers_morph_disguise");
         if (replacement instanceof MobEntity mob) {
            mob.setAiDisabled(true);
         }

         player.getEntityWorld().spawnEntity(replacement);
         this.morphDisguises.put(uuid, replacement);
         disguise = replacement;
      }

      this.applyMorphEffect(player, StatusEffects.INVISIBILITY, 0, 40);
      player.setCustomName(type.getName());
      player.setCustomNameVisible(false);
      disguise.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
      disguise.setVelocity(Vec3d.ZERO);
      ((EntityVelocityAccessor)disguise).invokeYppowersScheduleVelocityUpdate();
      if (disguise instanceof LivingEntity living) {
         living.setHealth(living.getMaxHealth());
      }

      Vec3d feet = player.getEntityPos();
      disguise.setBoundingBox(new Box(feet.x, feet.y, feet.z, feet.x, feet.y, feet.z));
      this.applyMorphReach(player, disguise.getHeight());
      this.syncMorphView(player, true, disguise.getId(), disguise.getEyeHeight(disguise.getPose()), disguise.getHeight(), true);
   }

   private void syncMorphView(ServerPlayerEntity player, boolean morphed, int disguiseEntityId, float eyeHeight, float entityHeight, boolean sendPacket) {
      UUID uuid = player.getUuid();
      float safeEyeHeight = Float.isFinite(eyeHeight) ? Math.max(0.1F, Math.min(8.0F, eyeHeight)) : 1.62F;
      float safeEntityHeight = Float.isFinite(entityHeight) ? Math.max(0.1F, Math.min(16.0F, entityHeight)) : 1.8F;
      int safeEntityId = morphed ? Math.max(0, disguiseEntityId) : -1;
      ShadowArcherPower.MorphViewState next = new ShadowArcherPower.MorphViewState(morphed, safeEntityId, safeEyeHeight, safeEntityHeight);
      ShadowArcherPower.MorphViewState previous = this.syncedMorphViews.put(uuid, next);
      if (sendPacket && !next.equals(previous)) {
         YPNetworking.syncShadowArcherMorphView(player, next.morphed(), next.disguiseEntityId(), next.eyeHeight(), next.entityHeight());
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      int cooldown = this.morphCooldowns.getOrDefault(uuid, 0);
      if (cooldown > 0) {
         if (cooldown == 1) {
            this.morphCooldowns.remove(uuid);
         } else {
            this.morphCooldowns.put(uuid, cooldown - 1);
         }
      }

      this.tickMorphAbilityCooldowns(uuid);
      int swapCooldown = this.arrowSwapCooldowns.getOrDefault(uuid, 0);
      if (swapCooldown > 0) {
         if (swapCooldown == 1) {
            this.arrowSwapCooldowns.remove(uuid);
         } else {
            this.arrowSwapCooldowns.put(uuid, swapCooldown - 1);
         }
      }

      boolean holdingBow = player.isUsingItem()
         && player.getActiveItem().isOf(Items.BOW)
         && specialArrowsEnabled(player)
         && this.selectedArrow.getOrDefault(uuid, ArrowType.SPLIT_SHOT) == ArrowType.RAILSHOT;
      if (holdingBow) {
         this.railshotWasHolding.put(uuid, true);
         this.railshotHeldTicks.put(uuid, Math.min(1000, this.railshotHeldTicks.getOrDefault(uuid, 0) + 1));
      } else if (this.railshotWasHolding.getOrDefault(uuid, false)) {
         this.railshotWasHolding.put(uuid, false);
         this.railshotHeldTicks.put(uuid, 0);
      }

      int streak = this.railshotStreak.getOrDefault(uuid, 0);
      if (!holdingBow
         && streak > 0
         && player.getEntityWorld().getServer() != null
         && player.getEntityWorld().getServer().getTicks() % Math.max(1, YPConfig.getInstance().shadow_archer_railshot_charge_decay_ticks) == 0) {
         this.railshotStreak.put(uuid, Math.max(0, streak - 1));
      }

      EntityType<?> morph = this.currentMorph.get(uuid);
      if (morph != null) {
         if (GraceManager.isActive(player.getEntityWorld().getServer())) {
            this.endMorph(player);
         } else {
            this.beginMorphState(player);
            this.refreshDisguise(player, morph);
            this.tickMorphTraits(player, ShadowArcherMorphRegistry.forEntityType(morph));
         }
      }

      this.checkArrowCrafting(player);
      this.processArrows(player);
      if (player.getEntityWorld().getServer() != null
         && player.getEntityWorld().getServer().getTicks() % 20 == 0
         && this.lastArrowCleanupTick != player.getEntityWorld().getServer().getTicks()) {
         this.lastArrowCleanupTick = player.getEntityWorld().getServer().getTicks();
         this.cleanupTrackedArrows(player.getEntityWorld().getServer());
      }
   }

   private void activateMorphSlot(ServerPlayerEntity player, int slot) {
      EntityType<?> type = this.currentMorph.get(player.getUuid());
      if (type != null) {
         ShadowArcherMorphRegistry.Profile profile = ShadowArcherMorphRegistry.forEntityType(type);

         ShadowArcherMorphRegistry.Ability ability = switch (slot) {
            case 0 -> profile.primary();
            case 1 -> profile.secondary();
            default -> profile.alternate();
         };
         if (ability != ShadowArcherMorphRegistry.Ability.NONE) {
            EnumMap<ShadowArcherMorphRegistry.Ability, Integer> cooldowns = this.morphAbilityCooldowns
               .computeIfAbsent(player.getUuid(), ignored -> new EnumMap<>(ShadowArcherMorphRegistry.Ability.class));
            int remaining = cooldowns.getOrDefault(ability, 0);
            if (remaining > 0) {
               player.sendMessage(Text.literal(ability.displayName() + " cooldown: " + (remaining + 19) / 20 + "s"), true);
            } else {
               if (this.executeMorphAbility(player, ability)) {
                  int ticks = this.abilityCooldownTicks(ability);
                  if (ticks > 0) {
                     cooldowns.put(ability, ticks);
                  }
               }
            }
         }
      }
   }

   private void tickMorphAbilityCooldowns(UUID uuid) {
      EnumMap<ShadowArcherMorphRegistry.Ability, Integer> cooldowns = this.morphAbilityCooldowns.get(uuid);
      if (cooldowns != null && !cooldowns.isEmpty()) {
         cooldowns.replaceAll((ability, ticks) -> Math.max(0, ticks - 1));
         cooldowns.values().removeIf(ticks -> ticks <= 0);
         if (cooldowns.isEmpty()) {
            this.morphAbilityCooldowns.remove(uuid);
         }
      }
   }

   private int abilityCooldownTicks(ShadowArcherMorphRegistry.Ability ability) {
      return switch (ability) {
         case TELEPORT -> 80;
         case PHASE_DASH -> 60;
         case DRAGON_BREATH, DRAGON_ROAR, WITHER_BLAST, WITHER_BARRAGE, SONIC_BOOM, CREEPER_BLAST -> 100;
         case GROUND_SLAM, GUARD, MAGIC_FANGS, WITCH_BREW, WEB_SNARE, SPORE_CLOUD, SHULKER_LIFT, HEALING_PULSE, UTILITY_PULSE, ARMADILLO_ROLL, TRADE_WINDS -> 80;
         case CHARGE, EQUINE_DASH, HEADBUTT, AQUA_DASH, DEEP_DASH, AIR_DASH, FOX_DASH, FROG_LEAP, BOUNCE, SLIME_SLAM, GUST -> 45;
         case LAVA_DASH -> 60;
         case RANGED_SHOT, FIRE_BLAST, FIRE_BURST, BEAM, SNOWBURST, SAND_SHOT -> 50;
         case SWORD_SLASH, POISON_BITE, INFECTION_BITE, BEE_STING, ROOT_PULSE, DARK_BURST, ECHO_PULSE, PUFFER_BURST, HOWL -> 60;
         case NONE -> 0;
      };
   }

   private boolean executeMorphAbility(ServerPlayerEntity player, ShadowArcherMorphRegistry.Ability ability) {
      boolean used = this.executeMorphAbilityCore(player, ability);
      if (used) {
         ServerWorld var5 = player.getEntityWorld();
         if (var5 instanceof ServerWorld) {
            EpicVfx.activation(var5, player.getX(), player.getY(), player.getZ(), 0.8);
         }
      }

      return used;
   }

   private boolean executeMorphAbilityCore(ServerPlayerEntity player, ShadowArcherMorphRegistry.Ability ability) {
      ServerWorld world = player.getEntityWorld();
      Vec3d look = player.getRotationVec(1.0F).normalize();
      double px = player.getX();
      double py = player.getY() + 1.0;
      double pz = player.getZ();
      switch (ability) {
         case TELEPORT:
            if (!this.safeMorphTeleport(player, 8.0)) {
               player.sendMessage(Text.literal("No safe teleport destination in front of you."), true);
               return false;
            }

            world.spawnParticles(ParticleTypes.PORTAL, true, true, px, py, pz, 24, 0.5, 0.8, 0.5, 0.15);
            world.spawnParticles(ParticleTypes.PORTAL, true, true, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.5, 0.8, 0.5, 0.15);
            this.playMorphSound(player, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, 0.9F, 1.0F);
            return true;
         case PHASE_DASH:
            if (!this.safeMorphTeleport(player, 5.0)) {
               this.applyDash(player, 1.25, 0.2);
            }

            this.applyMorphEffect(player, StatusEffects.SLOW_FALLING, 0, 40);
            world.spawnParticles(ParticleTypes.PORTAL, true, true, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.45, 0.7, 0.45, 0.1);
            this.playMorphSound(player, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, 0.8F, 1.3F);
            return true;
         case DRAGON_BREATH:
            this.damageCone(player, 20.0, 35.0, 9.0F, StatusEffects.WEAKNESS, 0, 80, 0.55);
            world.spawnParticles(
               DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), true, true,
               px + look.x * 2.0,
               py + look.y * 2.0,
               pz + look.z * 2.0,
               36,
               1.0,
               0.8,
               1.0,
               0.03
            );
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8F, 0.8F);
            return true;
         case DRAGON_ROAR:
            this.damageRadius(player, 7.0, 7.0F, StatusEffects.WEAKNESS, 0, 60, 1.2);
            world.spawnParticles(DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), true, true, px, py, pz, 45, 2.0, 1.2, 2.0, 0.05);
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0F, 0.6F);
            return true;
         case WITHER_BLAST:
            this.damageRadius(player, 6.0, 7.0F, StatusEffects.WITHER, 1, 80, 0.9);
            world.spawnParticles(ParticleTypes.SOUL, true, true, px, py, pz, 30, 1.4, 1.0, 1.4, 0.03);
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0F, 0.65F);
            return true;
         case WITHER_BARRAGE:
            this.damageCone(player, 26.0, 9.0, 8.0F, StatusEffects.WITHER, 1, 100, 0.25);
            world.spawnParticles(ParticleTypes.SOUL, true, true, px + look.x * 2.0, py + look.y * 2.0, pz + look.z * 2.0, 26, 0.6, 0.6, 0.6, 0.02);
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8F, 0.6F);
            return true;
         case SONIC_BOOM:
            this.damageCone(player, 22.0, 5.0, 10.0F, StatusEffects.SLOWNESS, 0, 30, 1.0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, true, true, px + look.x * 5.0, py + look.y * 5.0, pz + look.z * 5.0, 1, 0.0, 0.0, 0.0, 0.0);
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0F, 0.55F);
            return true;
         case CREEPER_BLAST:
            this.damageRadius(player, 4.5, 9.0F, StatusEffects.WEAKNESS, 0, 50, 1.5);
            world.spawnParticles(ParticleTypes.EXPLOSION, true, true, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ParticleTypes.SMOKE, true, true, px, py, pz, 35, 1.2, 0.8, 1.2, 0.08);
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0F, 0.8F);
            return true;
         case GROUND_SLAM:
            this.damageRadius(player, 5.0, 8.0F, StatusEffects.SLOWNESS, 0, 35, 1.0);
            world.spawnParticles(ParticleTypes.CLOUD, true, true, px, player.getY() + 0.15, pz, 30, 1.4, 0.15, 1.4, 0.04);
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.9F, 0.75F);
            return true;
         case GUARD:
         case ARMADILLO_ROLL:
            this.applyMorphEffect(player, StatusEffects.RESISTANCE, 1, ability == ShadowArcherMorphRegistry.Ability.ARMADILLO_ROLL ? 60 : 50);
            this.applyMorphEffect(player, StatusEffects.SLOWNESS, 0, 30);
            world.spawnParticles(ParticleTypes.CRIT, true, true, px, py, pz, 18, 0.75, 0.8, 0.75, 0.1);
            this.playMorphSound(player, SoundEvents.BLOCK_GLASS_BREAK, 0.5F, 0.7F);
            return true;
         case MAGIC_FANGS:
            this.damageCone(player, 12.0, 25.0, 7.0F, StatusEffects.SLOWNESS, 1, 40, 0.35);
            world.spawnParticles(ParticleTypes.ENCHANT, true, true, px + look.x * 4.0, py + look.y * 2.0, pz + look.z * 4.0, 24, 1.0, 0.7, 1.0, 0.05);
            this.playMorphSound(player, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.2F);
            return true;
         case WITCH_BREW:
         case SPORE_CLOUD: {
            RegistryEntry<StatusEffect> effect = ability == ShadowArcherMorphRegistry.Ability.WITCH_BREW ? StatusEffects.WEAKNESS : StatusEffects.POISON;
            this.damageRadius(player, 5.0, 3.5F, effect, 0, 100, 0.2);
            world.spawnParticles(ParticleTypes.WITCH, true, true, px, py, pz, 36, 1.1, 0.8, 1.1, 0.04);
            this.playMorphSound(player, SoundEvents.BLOCK_BREWING_STAND_BREW, 0.8F, 1.0F);
            return true;
         }
         case WEB_SNARE:
            this.damageCone(player, 11.0, 28.0, 3.5F, StatusEffects.SLOWNESS, 3, 60, 0.15);
            world.spawnParticles(ParticleTypes.CLOUD, true, true, px + look.x * 3.0, py + look.y * 2.0, pz + look.z * 3.0, 25, 0.7, 0.7, 0.7, 0.01);
            return true;
         case SHULKER_LIFT:
            this.damageCone(player, 20.0, 9.0, 4.0F, StatusEffects.LEVITATION, 0, 50, 0.15);
            world.spawnParticles(ParticleTypes.PORTAL, true, true, px + look.x * 3.0, py + look.y * 3.0, pz + look.z * 3.0, 22, 0.6, 0.6, 0.6, 0.03);
            return true;
         case HEALING_PULSE:
            player.heal(5.0F);
            this.applyMorphEffect(player, StatusEffects.REGENERATION, 1, 60);
            Box area = player.getBoundingBox().expand(6.0);

            for (LivingEntity target : world.getEntitiesByClass(
               LivingEntity.class, area, e -> e instanceof ServerPlayerEntity && AllianceManager.get(world.getServer()).isAlly(player, e)
            )) {
               target.heal(2.0F);
               target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0));
            }

            world.spawnParticles(ParticleTypes.HEART, true, true, px, py, pz, 18, 0.8, 0.8, 0.8, 0.03);
            this.playMorphSound(player, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.55F, 1.5F);
            return true;
         case UTILITY_PULSE:
         case TRADE_WINDS:
         case ECHO_PULSE: {
            Box pulseArea = player.getBoundingBox().expand(14.0);

            for (LivingEntity targetx : world.getEntitiesByClass(
               LivingEntity.class, pulseArea, e -> e != player && (!(e instanceof ServerPlayerEntity) || !AllianceManager.get(world.getServer()).isAlly(player, e))
            )) {
               targetx.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0));
            }

            this.applyMorphEffect(player, StatusEffects.REGENERATION, 0, 60);
            if (ability == ShadowArcherMorphRegistry.Ability.TRADE_WINDS) {
               this.applyMorphEffect(player, StatusEffects.SPEED, 1, 100);
            }

            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, true, true, px, py, pz, 24, 1.0, 0.8, 1.0, 0.03);
            this.playMorphSound(player, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.65F, 1.4F);
            return true;
         }
         case CHARGE:
         case EQUINE_DASH:
         case HEADBUTT:
            this.applyDash(
               player,
               ability == ShadowArcherMorphRegistry.Ability.EQUINE_DASH ? 1.65 : 1.4,
               ability == ShadowArcherMorphRegistry.Ability.EQUINE_DASH ? 0.48 : 0.15
            );
            this.damageCone(player, 5.5, 42.0, ability == ShadowArcherMorphRegistry.Ability.EQUINE_DASH ? 5.0F : 7.0F, null, 0, 0, 1.0);
            world.spawnParticles(ParticleTypes.CLOUD, true, true, px, py, pz, 16, 0.65, 0.4, 0.65, 0.03);
            this.playMorphSound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.85F, 0.8F);
            return true;
         case AQUA_DASH:
         case DEEP_DASH:
            this.applyDash(player, player.isTouchingWater() ? 1.65 : 1.05, 0.15);
            this.applyMorphEffect(player, StatusEffects.WATER_BREATHING, 0, 100);
            if (player.isTouchingWater()) {
               this.applyMorphEffect(player, StatusEffects.DOLPHINS_GRACE, 1, 100);
            }

            world.spawnParticles(ParticleTypes.BUBBLE, true, true, px, py, pz, 24, 0.5, 0.7, 0.5, 0.06);
            return true;
         case AIR_DASH:
            this.applyDash(player, 1.2, 0.55);
            this.applyMorphEffect(player, StatusEffects.SLOW_FALLING, 0, 45);
            world.spawnParticles(ParticleTypes.CLOUD, true, true, px, py, pz, 16, 0.4, 0.4, 0.4, 0.04);
            return true;
         case FOX_DASH:
            this.applyDash(player, 1.6, 0.25);
            this.applyMorphEffect(player, StatusEffects.SPEED, 1, 45);
            return true;
         case FROG_LEAP:
            Vec3d v = player.getVelocity();
            player.setVelocity(v.x + look.x * 0.9, Math.max(1.35, v.y + 0.9), v.z + look.z * 0.9);
            ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
            world.spawnParticles(ParticleTypes.SPLASH, true, true, px, player.getY(), pz, 20, 0.45, 0.2, 0.45, 0.02);
            return true;
         case BOUNCE:
         case SLIME_SLAM:
            Vec3d velocity = player.getVelocity();
            player.setVelocity(velocity.x + look.x * 0.75, Math.max(0.9, velocity.y + 1.05), velocity.z + look.z * 0.75);
            ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
            if (ability == ShadowArcherMorphRegistry.Ability.SLIME_SLAM) {
               this.damageRadius(player, 3.5, 5.0F, StatusEffects.SLOWNESS, 0, 30, 0.6);
            }

            world.spawnParticles(ParticleTypes.ITEM_SLIME, true, true, px, player.getY() + 0.2, pz, 20, 0.55, 0.15, 0.55, 0.03);
            return true;
         case GUST:
            this.pushCone(player, 14.0, 30.0, 1.4, 0.55);
            world.spawnParticles(ParticleTypes.CLOUD, true, true, px + look.x * 2.0, py + look.y * 2.0, pz + look.z * 2.0, 32, 0.75, 0.75, 0.75, 0.05);
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.65F, 1.3F);
            return true;
         case LAVA_DASH:
            boolean inLava = player.isInLava();
            this.applyDash(player, inLava ? 1.95 : 1.15, inLava ? 0.68 : 0.22);
            this.applyMorphEffect(player, StatusEffects.FIRE_RESISTANCE, 0, 80);
            world.spawnParticles(ParticleTypes.FLAME, true, true, px, py, pz, inLava ? 28 : 14, 0.55, 0.45, 0.55, 0.035);
            world.spawnParticles(ParticleTypes.SMOKE, true, true, px, py, pz, 10, 0.35, 0.25, 0.35, 0.015);
            this.playMorphSound(player, SoundEvents.ENTITY_BLAZE_SHOOT, 0.6F, 1.3F);
            return true;
         case RANGED_SHOT:
         case SAND_SHOT: {
            RegistryEntry<StatusEffect> effect = ability == ShadowArcherMorphRegistry.Ability.SAND_SHOT ? StatusEffects.BLINDNESS : null;
            this.damageCone(player, 24.0, 5.0, ability == ShadowArcherMorphRegistry.Ability.SAND_SHOT ? 4.5F : 6.0F, effect, 0, 40, 0.15);
            world.spawnParticles(ParticleTypes.CRIT, true, true, px + look.x * 2.0, py + look.y * 2.0, pz + look.z * 2.0, 18, 0.25, 0.25, 0.25, 0.04);
            this.playMorphSound(player, SoundEvents.ENTITY_ARROW_SHOOT, 0.8F, 1.1F);
            return true;
         }
         case FIRE_BLAST:
            this.damageCone(player, 22.0, 17.0, 7.0F, StatusEffects.WEAKNESS, 0, 60, 0.45);
            world.spawnParticles(ParticleTypes.FLAME, true, true, px + look.x * 2.0, py + look.y * 2.0, pz + look.z * 2.0, 28, 0.8, 0.7, 0.8, 0.05);
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.7F, 1.2F);
            return true;
         case FIRE_BURST:
            this.damageRadius(player, 4.5, 6.0F, StatusEffects.WEAKNESS, 0, 40, 0.75);
            this.applyMorphEffect(player, StatusEffects.FIRE_RESISTANCE, 0, 100);
            world.spawnParticles(ParticleTypes.FLAME, true, true, px, py, pz, 35, 1.2, 0.8, 1.2, 0.04);
            this.playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.9F, 1.1F);
            return true;
         case BEAM:
            this.damageCone(player, 27.0, 4.0, 7.0F, StatusEffects.SLOWNESS, 0, 30, 0.15);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, px + look.x * 3.0, py + look.y * 3.0, pz + look.z * 3.0, 22, 0.55, 0.55, 0.55, 0.02);
            return true;
         case SNOWBURST:
            this.damageCone(player, 18.0, 22.0, 3.0F, StatusEffects.SLOWNESS, 1, 80, 0.25);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, px + look.x * 2.0, py + look.y * 2.0, pz + look.z * 2.0, 30, 0.8, 0.7, 0.8, 0.04);
            this.playMorphSound(player, SoundEvents.BLOCK_GLASS_BREAK, 0.6F, 1.4F);
            return true;
         case SWORD_SLASH:
         case INFECTION_BITE: {
            RegistryEntry<StatusEffect> effect = ability == ShadowArcherMorphRegistry.Ability.INFECTION_BITE ? StatusEffects.WEAKNESS : null;
            this.damageCone(player, 4.8, 62.0, ability == ShadowArcherMorphRegistry.Ability.INFECTION_BITE ? 6.0F : 7.0F, effect, 0, 60, 0.5);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, true, true, px + look.x, py, pz + look.z, 6, 0.5, 0.4, 0.5, 0.0);
            this.playMorphSound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8F, 1.0F);
            return true;
         }
         case POISON_BITE:
         case BEE_STING:
            this.damageCone(
               player,
               4.5,
               52.0,
               ability == ShadowArcherMorphRegistry.Ability.BEE_STING ? 5.0F : 6.0F,
               StatusEffects.POISON,
               0,
               ability == ShadowArcherMorphRegistry.Ability.BEE_STING ? 100 : 80,
               0.3
            );
            world.spawnParticles(ParticleTypes.CRIT, true, true, px + look.x, py + look.y, pz + look.z, 12, 0.4, 0.4, 0.4, 0.04);
            return true;
         case ROOT_PULSE:
            this.damageCone(player, 10.0, 38.0, 4.0F, StatusEffects.SLOWNESS, 1, 60, 0.1);
            world.spawnParticles(ParticleTypes.SMOKE, true, true, px + look.x * 2.0, py + look.y * 2.0, pz + look.z * 2.0, 20, 0.7, 0.5, 0.7, 0.02);
            return true;
         case DARK_BURST:
            this.damageRadius(player, 5.0, 5.5F, StatusEffects.BLINDNESS, 0, 40, 0.65);
            world.spawnParticles(ParticleTypes.PORTAL, true, true, px, py, pz, 30, 1.1, 0.9, 1.1, 0.06);
            return true;
         case PUFFER_BURST:
            this.damageRadius(player, 4.0, 2.5F, StatusEffects.POISON, 0, 100, 0.6);
            world.spawnParticles(ParticleTypes.BUBBLE, true, true, px, py, pz, 30, 0.8, 0.7, 0.8, 0.04);
            return true;
         case HOWL: {
            this.applyMorphEffect(player, StatusEffects.STRENGTH, 0, 100);
            this.applyMorphEffect(player, StatusEffects.SPEED, 0, 100);
            Box howlArea = player.getBoundingBox().expand(8.0);

            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, howlArea, e -> e instanceof ServerPlayerEntity && e != player)) {
               if (AllianceManager.get(world.getServer()).isAlly(player, target)) {
                  target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 100, 0));
                  target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0));
               }
            }

            world.spawnParticles(ParticleTypes.HEART, true, true, px, py, pz, 12, 0.9, 0.6, 0.9, 0.02);
            this.playMorphSound(player, SoundEvents.ENTITY_WOLF_SHAKE, 1.0F, 1.0F);
            return true;
         }
         case NONE:
            return false;
         default:
            return false;
      }
   }

   private void applyMorphReach(ServerPlayerEntity player, double bodyHeight) {
      double bonus = Math.max(0.0, Math.min(5.0, (bodyHeight - 1.8) * 0.8));
      bonus = Math.round(bonus * 4.0) / 4.0;
      Double previous = this.morphReachBonus.get(player.getUuid());
      if (previous == null || !(Math.abs(previous - bonus) < 1.0E-6)) {
         setReachModifiers(player, bonus);
         this.morphReachBonus.put(player.getUuid(), bonus);
      }
   }

   private void clearMorphReach(ServerPlayerEntity player) {
      if (this.morphReachBonus.remove(player.getUuid()) != null) {
         setReachModifiers(player, 0.0);
      }
   }

   private static void setReachModifiers(ServerPlayerEntity player, double bonus) {
      for (RegistryEntry<EntityAttribute> attribute : List.of(EntityAttributes.BLOCK_INTERACTION_RANGE, EntityAttributes.ENTITY_INTERACTION_RANGE)) {
         EntityAttributeInstance instance = player.getAttributeInstance(attribute);
         if (instance != null) {
            instance.removeModifier(MORPH_REACH_ID);
            if (bonus > 0.0) {
               instance.addTemporaryModifier(new EntityAttributeModifier(MORPH_REACH_ID, bonus, Operation.ADD_VALUE));
            }
         }
      }
   }

   public boolean isOwnDisguise(ServerPlayerEntity player, Entity entity) {
      Entity disguise = this.morphDisguises.get(player.getUuid());
      return disguise != null && disguise == entity;
   }

   public void redirectDisguiseClick(ServerPlayerEntity player, Hand hand, boolean attack) {
      ServerWorld disguise = player.getEntityWorld();
      if (disguise instanceof ServerWorld) {
         Entity var27 = this.morphDisguises.get(player.getUuid());
         Vec3d eye = player.getEyePos();
         Vec3d look = player.getRotationVec(1.0F);
         double entityReach = player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE);
         double blockReach = player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);
         double reach = Math.max(entityReach, blockReach);
         Vec3d end = eye.add(look.multiply(reach));
         BlockHitResult blockHit = disguise.raycast(new RaycastContext(eye, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
         double blockDistance = blockHit.getType() == Type.BLOCK ? blockHit.getPos().distanceTo(eye) : Double.MAX_VALUE;
         Entity best = null;
         double bestDistance = Double.MAX_VALUE;
         Box search = player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);

         for (Entity candidate : disguise.getOtherEntities(player, search, e -> e != var27 && e.isAlive() && !e.isSpectator() && e instanceof LivingEntity)) {
            Optional<Vec3d> hit = candidate.getBoundingBox().expand(candidate.getTargetingMargin()).raycast(eye, end);
            if (!hit.isEmpty()) {
               double distance = hit.get().distanceTo(eye);
               if (distance < bestDistance && distance <= entityReach) {
                  best = candidate;
                  bestDistance = distance;
               }
            }
         }

         boolean entityFirst = best != null && bestDistance <= blockDistance;
         if (attack) {
            if (entityFirst) {
               player.attack(best);
            }

            player.swingHand(hand, true);
         } else if (entityFirst) {
            player.interact(best, hand);
         } else if (blockHit.getType() == Type.BLOCK) {
            player.interactionManager.interactBlock(player, disguise, player.getStackInHand(hand), hand, blockHit);
         }
      }
   }

   private static boolean isTouchingWall(ServerWorld world, ServerPlayerEntity player) {
      double x = player.getX();
      double z = player.getZ();
      double reach = 0.42;

      for (double y : new double[]{player.getY() + 0.5, player.getY() + 1.4}) {
         for (double[] offset : new double[][]{{reach, 0.0}, {-reach, 0.0}, {0.0, reach}, {0.0, -reach}}) {
            BlockPos pos = BlockPos.ofFloored(x + offset[0], y, z + offset[1]);
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
               return true;
            }
         }
      }

      return false;
   }

   private static float flightSpeedFor(String id) {
      if (id.startsWith("ender_dragon")) {
         return 0.11F;
      } else if (id.startsWith("phantom")) {
         return 0.075F;
      } else if (id.startsWith("wither")) {
         return 0.065F;
      } else if (id.startsWith("vex") || id.startsWith("bat") || id.startsWith("parrot")) {
         return 0.06F;
      } else if (id.startsWith("bee") || id.startsWith("allay")) {
         return 0.055F;
      } else if (id.startsWith("happy_ghast")) {
         return 0.045F;
      } else {
         return id.startsWith("ghast") ? 0.04F : 0.05F;
      }
   }

   private void tickMorphTraits(ServerPlayerEntity player, ShadowArcherMorphRegistry.Profile profile) {
      Set<ShadowArcherMorphRegistry.Trait> traits = profile.traits();
      if (!player.isCreative() && !player.isSpectator()) {
         boolean flier = traits.contains(ShadowArcherMorphRegistry.Trait.FLIGHT);
         ShadowArcherPower.FlightSnapshot baseline = this.morphSavedFlight.get(player.getUuid());
         boolean changed = false;
         if (flier) {
            if (!player.getAbilities().allowFlying) {
               player.getAbilities().allowFlying = true;
               changed = true;
            }

            float speed = flightSpeedFor(profile.id());
            if (Math.abs(player.getAbilities().getFlySpeed() - speed) > 1.0E-4F) {
               player.getAbilities().setFlySpeed(speed);
               changed = true;
            }
         } else {
            if (baseline != null && !baseline.allowFlying() && player.getAbilities().allowFlying) {
               player.getAbilities().allowFlying = false;
               player.getAbilities().flying = false;
               changed = true;
            }

            if (Math.abs(player.getAbilities().getFlySpeed() - 0.05F) > 1.0E-4F) {
               player.getAbilities().setFlySpeed(0.05F);
               changed = true;
            }
         }

         if (changed) {
            player.sendAbilitiesUpdate();
         }
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE)) {
         this.applyMorphEffect(player, StatusEffects.FIRE_RESISTANCE, 0, 40);
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.AQUATIC)) {
         this.applyMorphEffect(player, StatusEffects.WATER_BREATHING, 0, 40);
         if (player.isTouchingWater()) {
            this.applyMorphEffect(player, StatusEffects.DOLPHINS_GRACE, 1, 40);
         }
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.SPEED)) {
         this.applyMorphEffect(player, StatusEffects.SPEED, 0, 40);
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.STRENGTH)) {
         this.applyMorphEffect(player, StatusEffects.STRENGTH, 0, 40);
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.JUMP)) {
         this.applyMorphEffect(player, StatusEffects.JUMP_BOOST, 0, 40);
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.RESISTANCE)) {
         this.applyMorphEffect(player, StatusEffects.RESISTANCE, 0, 40);
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.SLOW_FALLING)) {
         this.applyMorphEffect(player, StatusEffects.SLOW_FALLING, 0, 40);
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.NIGHT_VISION)) {
         this.applyMorphEffect(player, StatusEffects.NIGHT_VISION, 0, 300);
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.REGENERATION)) {
         this.applyMorphEffect(player, StatusEffects.REGENERATION, 0, 40);
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.POISON_RESISTANCE)) {
         player.removeStatusEffect(StatusEffects.POISON);
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.LAVA_ADAPTED) && player.isInLava()) {
         this.applyMorphEffect(player, StatusEffects.FIRE_RESISTANCE, 0, 40);
         this.applyMorphEffect(player, StatusEffects.SPEED, 1, 40);
         Vec3d velocity = player.getVelocity();
         if (!player.isSneaking() && velocity.y < 0.1) {
            player.setVelocity(velocity.x, 0.1, velocity.z);
            ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
         }
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.WALL_CLIMB) && !player.isOnGround()) {
         ServerWorld var10 = player.getEntityWorld();
         if (var10 instanceof ServerWorld && isTouchingWall(var10, player)) {
            player.fallDistance = 0.0;
         }
      }

      if (traits.contains(ShadowArcherMorphRegistry.Trait.BOUNCY) && player.isOnGround() && player.isSprinting()) {
         Vec3d velocity = player.getVelocity();
         if (player.fallDistance > 1.5) {
            player.setVelocity(velocity.x, Math.max(velocity.y, 0.45), velocity.z);
            ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
         }
      }
   }

   private void applyMorphEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier, int duration) {
      StatusEffectInstance current = player.getStatusEffect(effect);
      if (current == null || current.getDuration() < 12 && current.getAmplifier() <= amplifier) {
         player.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, false, false, true));
      }
   }

   private void damageCone(
      ServerPlayerEntity player,
      double range,
      double halfAngleDegrees,
      float damage,
      RegistryEntry<StatusEffect> effect,
      int amplifier,
      int duration,
      double knockback
   ) {
      ServerWorld world = player.getEntityWorld();
      Vec3d origin = new Vec3d(player.getX(), player.getY() + 1.0, player.getZ());
      Vec3d direction = player.getRotationVec(1.0F).normalize();
      double minDot = Math.cos(Math.toRadians(halfAngleDegrees));
      Box box = new Box(
         player.getX() - range, player.getY() - range, player.getZ() - range, player.getX() + range, player.getY() + range + 1.5, player.getZ() + range
      );
      AllianceManager alliances = AllianceManager.get(world.getServer());

      for (LivingEntity target : world.getEntitiesByClass(
         LivingEntity.class, box, targetx -> PowerCombatUtil.canHarm(player, targetx)
      )) {
         Vec3d toTarget = target.getBoundingBox().getCenter().subtract(origin);
         double distance = toTarget.length();
         if (!(distance > range)
            && !(distance < 0.01)
            && !(direction.dotProduct(toTarget.multiply(1.0 / distance)) < minDot)
            && target.damage(world, world.getDamageSources().playerAttack(player), damage)) {
            if (effect != null && duration > 0) {
               target.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier));
            }

            if (knockback > 0.0) {
               this.pushEntity(target, toTarget.normalize(), knockback, 0.15);
            }
         }
      }
   }

   private void damageRadius(
      ServerPlayerEntity player, double radius, float damage, RegistryEntry<StatusEffect> effect, int amplifier, int duration, double knockback
   ) {
      ServerWorld world = player.getEntityWorld();
      Box box = player.getBoundingBox().expand(radius);
      AllianceManager alliances = AllianceManager.get(world.getServer());

      for (LivingEntity target : world.getEntitiesByClass(
         LivingEntity.class, box, targetx -> PowerCombatUtil.canHarm(player, targetx)
      )) {
         double distance = target.getBoundingBox().getCenter().distanceTo(player.getEntityPos());
         if (!(distance > radius)) {
            float scaledDamage = (float)Math.max(1.0, damage * (1.0 - 0.35 * distance / radius));
            if (target.damage(world, world.getDamageSources().playerAttack(player), scaledDamage)) {
               if (effect != null && duration > 0) {
                  target.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier));
               }

               if (knockback > 0.0) {
                  Vec3d away = target.getEntityPos().subtract(player.getEntityPos()).normalize();
                  this.pushEntity(target, away, knockback, 0.25);
               }
            }
         }
      }
   }

   private void pushCone(ServerPlayerEntity player, double range, double halfAngleDegrees, double power, double vertical) {
      ServerWorld world = player.getEntityWorld();
      Vec3d look = player.getRotationVec(1.0F).normalize();
      Vec3d origin = new Vec3d(player.getX(), player.getY() + 1.0, player.getZ());
      Box box = new Box(
         player.getX() - range, player.getY() - range, player.getZ() - range, player.getX() + range, player.getY() + range + 1.5, player.getZ() + range
      );
      AllianceManager alliances = AllianceManager.get(world.getServer());
      double minDot = Math.cos(Math.toRadians(halfAngleDegrees));

      for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, e -> PowerCombatUtil.canHarm(player, e))) {
         Vec3d diff = target.getBoundingBox().getCenter().subtract(origin);
         double distance = diff.length();
         if (!(distance < 0.01) && !(distance > range) && !(look.dotProduct(diff.multiply(1.0 / distance)) < minDot)) {
            this.pushEntity(target, diff.normalize(), power, vertical);
         }
      }
   }

   private void pushEntity(LivingEntity target, Vec3d direction, double power, double vertical) {
      Vec3d flat = new Vec3d(direction.x, 0.0, direction.z).normalize();
      Vec3d current = target.getVelocity();
      target.setVelocity(current.x + flat.x * power, Math.max(current.y, vertical), current.z + flat.z * power);
      ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
   }

   private void applyDash(ServerPlayerEntity player, double power, double vertical) {
      Vec3d look = player.getRotationVec(1.0F).normalize();
      Vec3d current = player.getVelocity();
      player.setVelocity(current.x + look.x * power, Math.max(current.y, vertical), current.z + look.z * power);
      ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
   }

   private boolean safeMorphTeleport(ServerPlayerEntity player, double maxDistance) {
      ServerWorld world = player.getEntityWorld();
      Vec3d look = player.getRotationVec(1.0F).normalize();
      BlockPos current = player.getBlockPos();

      for (int step = (int)maxDistance; step >= 2; step--) {
         int x = MathHelper.floor(player.getX() + look.x * step);
         int y = current.getY();
         int z = MathHelper.floor(player.getZ() + look.z * step);
         BlockPos feet = new BlockPos(x, y, z);
         BlockPos floorPos = feet.down();
         BlockState floor = world.getBlockState(floorPos);
         if (world.isInBuildLimit(floorPos)
            && world.isInBuildLimit(feet)
            && world.isInBuildLimit(feet.up())
            && world.getWorldBorder().contains(feet)
            && world.getWorldBorder().contains(feet.add(1, 0, 1))
            && !floor.getCollisionShape(world, floorPos).isEmpty()
            && world.getFluidState(floorPos).isEmpty()
            && !floor.isOf(Blocks.MAGMA_BLOCK)
            && !floor.isOf(Blocks.CAMPFIRE)
            && !floor.isOf(Blocks.SOUL_CAMPFIRE)
            && !floor.isOf(Blocks.CACTUS)
            && !floor.isOf(Blocks.FIRE)
            && !floor.isOf(Blocks.SOUL_FIRE)
            && !floor.isOf(Blocks.SWEET_BERRY_BUSH)
            && !floor.isOf(Blocks.WITHER_ROSE)
            && !floor.isOf(Blocks.POINTED_DRIPSTONE)
            && world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
            && world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
            && world.getFluidState(feet).isEmpty()
            && world.getFluidState(feet.up()).isEmpty()) {
            Vec3d destination = new Vec3d(x + 0.5, y, z + 0.5);
            if (TargetingUtil.canFitAt(world, player, destination)
               && player.teleport(world, destination.x, destination.y, destination.z, Set.of(), player.getYaw(), player.getPitch(), false)) {
               return true;
            }
         }
      }

      return false;
   }

   private void playMorphSound(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
      player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
   }

   private void playMorphSound(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
      player.getEntityWorld().playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, volume, pitch);
   }

   private void cleanupTrackedArrows(MinecraftServer server) {
      Set<UUID> candidates = new HashSet<>(this.arrowOwners.keySet());
      candidates.addAll(this.arrowTypes.keySet());
      candidates.addAll(this.processedArrows);
      Set<UUID> live = new HashSet<>();

      for (UUID arrowId : candidates) {
         for (ServerWorld world : server.getWorlds()) {
            if (world.getEntity(arrowId) instanceof ArrowEntity) {
               live.add(arrowId);
               break;
            }
         }
      }

      this.arrowTypes.keySet().retainAll(live);
      this.arrowOwners.keySet().retainAll(live);
      this.processedArrows.retainAll(live);
      this.phaseArrowTravel.keySet().retainAll(live);
      this.arrowsThatHit.retainAll(live);
      this.arrowOrder.keySet().retainAll(live);
      this.railshotArrowLevel.keySet().retainAll(live);
      this.latestArrow.values().removeIf(id -> !live.contains(id) || this.arrowsThatHit.contains(id));
      long nowTick = server.getTicks();
      this.tracerMarks.values().removeIf(mark -> nowTick >= mark.expiresAtTick());
   }

   private void checkArrowCrafting(ServerPlayerEntity player) {
      Set<ArrowType> unlocked = this.unlockedArrows.computeIfAbsent(player.getUuid(), k -> new HashSet<>(Collections.singleton(ArrowType.SPLIT_SHOT)));

      for (ArrowType type : ArrowType.values()) {
         if (!unlocked.contains(type) && this.hasItems(player, Items.ARROW, 8) && this.hasItems(player, type.getCraftItem(), 1)) {
            this.consumeItems(player, Items.ARROW, 8);
            this.consumeItems(player, type.getCraftItem(), 1);
            unlocked.add(type);
            if (player.getEntityWorld().getServer() != null) {
               PowerManager.get(player.getEntityWorld().getServer()).recordShadowArcherArrowUnlock(player.getUuid(), type);
            }

            player.sendMessage(Text.literal("Unlocked " + type.getDisplayName() + "!"), false);
         }
      }
   }

   private boolean hasItems(ServerPlayerEntity player, Item item, int count) {
      int total = 0;

      for (int i = 0; i < player.getInventory().size(); i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (stack.isOf(item)) {
            total += stack.getCount();
         }
      }

      return total >= count;
   }

   private void consumeItems(ServerPlayerEntity player, Item item, int count) {
      int needed = count;

      for (int i = 0; i < player.getInventory().size() && needed > 0; i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (stack.isOf(item)) {
            int take = Math.min(stack.getCount(), needed);
            stack.decrement(take);
            needed -= take;
         }
      }
   }

   public static boolean registerSpawnedArrow(ServerWorld world, ArrowEntity arrow) {
      if (world != null && world.getServer() != null && arrow.getOwner() instanceof ServerPlayerEntity player) {
         PowerManager manager = PowerManager.get(world.getServer());
         if (manager.getPowerType(player.getUuid()) != PowerType.SHADOW_ARCHER) {
            return true;
         } else if (manager.getPowerInstance(PowerType.SHADOW_ARCHER) instanceof ShadowArcherPower archer && !archer.processedArrows.contains(arrow.getUuid())) {
            archer.rememberArrow(player, arrow);
            if (!specialArrowsEnabled(player)) {
               archer.trackOrdinaryArrow(player, arrow);
               return true;
            } else if (GraceManager.isActive(world.getServer())) {
               GraceManager.blockAbility(player);
               archer.trackOrdinaryArrow(player, arrow);
               return true;
            } else {
               ArrowType selected = archer.selectedArrow.getOrDefault(player.getUuid(), ArrowType.SPLIT_SHOT);
               if (selected == ArrowType.RAILSHOT) {
                  YPConfig cfg = YPConfig.getInstance();
                  int streak = Math.max(0, archer.railshotStreak.getOrDefault(player.getUuid(), 0));
                  int base = Math.max(1, cfg.shadow_archer_railshot_base_draw_ticks);
                  int maximum = Math.max(base, Math.min(20, cfg.shadow_archer_railshot_max_draw_ticks));
                  int required = Math.min(maximum, base + streak * Math.max(1, cfg.shadow_archer_railshot_draw_gain_ticks));
                  int held = archer.railshotHeldTicks.getOrDefault(player.getUuid(), 0);
                  if (held < required) {
                     player.sendMessage(Text.literal("Railshot draw incomplete: " + held + "/" + required + " ticks.").formatted(Formatting.RED), true);
                     archer.railshotHeldTicks.put(player.getUuid(), 0);
                     return false;
                  }

                  archer.railshotArrowLevel.put(arrow.getUuid(), streak);
                  archer.railshotStreak.put(player.getUuid(), Math.min(10, streak + 1));
                  archer.railshotHeldTicks.put(player.getUuid(), 0);
               }

               if (!EnergyManager.get(world.getServer()).trySpendDirect(player, YPConfig.getInstance().energy_cost_shadow_archer_arrow, "Shadow Archer arrow")) {
                  return false;
               } else {
                  archer.trackArrow(player, arrow);
                  return true;
               }
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private void trackOrdinaryArrow(ServerPlayerEntity player, ArrowEntity arrow) {
      UUID arrowId = arrow.getUuid();
      if (this.processedArrows.add(arrowId)) {
         this.rememberArrow(player, arrow);
         this.arrowOwners.put(arrowId, player.getUuid());
      }
   }

   private void trackArrow(ServerPlayerEntity player, ArrowEntity arrow) {
      if (this.processedArrows.add(arrow.getUuid())) {
         this.rememberArrow(player, arrow);
         ArrowType selected = this.selectedArrow.getOrDefault(player.getUuid(), ArrowType.SPLIT_SHOT);
         this.arrowTypes.put(arrow.getUuid(), selected);
         this.arrowOwners.put(arrow.getUuid(), player.getUuid());
         if (selected == ArrowType.SPLIT_SHOT) {
            double baseDamage = ((PersistentProjectileDamageAccessor)arrow).yppowers$getProjectileDamage();
            arrow.setDamage(baseDamage * 0.75);
            Vec3d velocity = arrow.getVelocity();

            for (int side : new int[]{-1, 1}) {
               ArrowEntity split = new ArrowEntity(EntityType.ARROW, player.getEntityWorld());
               split.setOwner(player);
               split.setPosition(arrow.getX(), arrow.getY(), arrow.getZ());
               double angle = Math.toRadians(15 * side);
               double x = velocity.x * Math.cos(angle) - velocity.z * Math.sin(angle);
               double z = velocity.x * Math.sin(angle) + velocity.z * Math.cos(angle);
               split.setVelocity(x, velocity.y, z, (float)velocity.length(), 0.1F);
               split.setDamage(baseDamage * 0.75);
               this.arrowTypes.put(split.getUuid(), ArrowType.SPLIT_SHOT);
               this.arrowOwners.put(split.getUuid(), player.getUuid());
               this.processedArrows.add(split.getUuid());
               player.getEntityWorld().spawnEntity(split);
            }
         } else if (selected == ArrowType.RAILSHOT) {
            int level = Math.max(0, Math.min(10, this.railshotArrowLevel.getOrDefault(arrow.getUuid(), 0)));
            this.railshotArrowLevel.remove(arrow.getUuid());
            double speed = 1.0 + level * Math.max(0.0, YPConfig.getInstance().shadow_archer_railshot_speed_multiplier_per_charge);
            double damageMultiplier = 1.0 + level * Math.max(0.0, YPConfig.getInstance().shadow_archer_railshot_damage_multiplier_per_charge);
            float baseDamage = (float)((PersistentProjectileDamageAccessor)arrow).yppowers$getProjectileDamage();
            arrow.setDamage(baseDamage * damageMultiplier);
            Vec3d velocity = arrow.getVelocity();
            if (velocity.lengthSquared() > 1.0E-6) {
               arrow.setVelocity(velocity.normalize().multiply(velocity.length() * speed));
            }

            ((PersistentProjectilePierceInvoker)arrow).yppowers$setPierceLevel((byte)Math.min(4, 1 + level / 3));
            ((ServerWorld)arrow.getEntityWorld())
               .spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, arrow.getX(), arrow.getY(), arrow.getZ(), 10, 0.1, 0.1, 0.1, 0.02);
         }
      }
   }

   private void processArrows(ServerPlayerEntity player) {
      ServerWorld world = player.getEntityWorld();
      if (GraceManager.isActive(world.getServer())) {
         for (ArrowEntity arrow : world.getEntitiesByClass(ArrowEntity.class, player.getBoundingBox().expand(160.0), a -> a.getOwner() == player)) {
            UUID arrowId = arrow.getUuid();
            if (!this.processedArrows.contains(arrowId)) {
               GraceManager.blockAbility(player);
            }

            this.processedArrows.add(arrowId);
            this.arrowTypes.put(arrowId, ArrowType.SPLIT_SHOT);
            this.arrowOwners.put(arrowId, player.getUuid());
         }
      } else {
         for (ArrowEntity arrow : new ArrayList<>(world.getEntitiesByClass(ArrowEntity.class, player.getBoundingBox().expand(160.0), a -> true))) {
            if (arrow.getOwner() == player && !this.processedArrows.contains(arrow.getUuid())) {
               this.trackArrow(player, arrow);
            }

            if (player.getUuid().equals(this.arrowOwners.get(arrow.getUuid()))) {
               ArrowType type = this.arrowTypes.get(arrow.getUuid());
               if (type == ArrowType.HOMING && !arrow.isOnGround() && !arrow.isRemoved()) {
                  Box box = arrow.getBoundingBox().expand(35.0);
                  AllianceManager alliances = AllianceManager.get(world.getServer());
                  List<LivingEntity> targets = world.getEntitiesByClass(
                     LivingEntity.class, box, targetx -> PowerCombatUtil.canHarm(player, targetx) && targetx.squaredDistanceTo(arrow) <= 1225.0
                  );
                  LivingEntity closest = null;
                  double closestDistance = Double.MAX_VALUE;

                  for (LivingEntity target : targets) {
                     double distance = arrow.squaredDistanceTo(target);
                     if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = target;
                     }
                  }

                  if (closest != null) {
                     Vec3d direction = closest.getBoundingBox().getCenter().subtract(arrow.getEntityPos()).normalize();
                     double speed = arrow.getVelocity().length();
                     arrow.setVelocity(arrow.getVelocity().lerp(direction.multiply(speed), 0.2));
                     ((EntityVelocityAccessor)arrow).invokeYppowersScheduleVelocityUpdate();
                  }
               }

               if (type == ArrowType.SPLIT_SHOT && !arrow.isOnGround() && !arrow.isRemoved()) {
                  ShadowArcherPower.TracerMark mark = this.tracerMarks.get(player.getUuid());
                  if (mark != null) {
                     if (world.getServer().getTicks() >= mark.expiresAtTick()) {
                        this.tracerMarks.remove(player.getUuid());
                     } else if (world.getEntity(mark.targetId()) instanceof LivingEntity targetx
                        && targetx.isAlive()
                        && !AllianceManager.get(world.getServer()).isAlly(player, targetx)) {
                        Vec3d direction = targetx.getBoundingBox().getCenter().subtract(arrow.getEntityPos()).normalize();
                        double speed = arrow.getVelocity().length();
                        if (speed > 1.0E-4) {
                           arrow.setVelocity(arrow.getVelocity().lerp(direction.multiply(speed), 0.12));
                           ((EntityVelocityAccessor)arrow).invokeYppowersScheduleVelocityUpdate();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean phaseThroughBlock(ProjectileEntity projectile, HitResult hitResult) {
      if (!(projectile instanceof ArrowEntity arrow && hitResult.getType() == Type.BLOCK && projectile.getOwner() instanceof ServerPlayerEntity player)) {
         return false;
      } else if (player.getEntityWorld().getServer() == null
         || PowerManager.get(player.getEntityWorld().getServer()).getPowerType(player.getUuid()) != PowerType.SHADOW_ARCHER
         || GraceManager.isActive(player.getEntityWorld().getServer())) {
         return false;
      } else if (PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.SHADOW_ARCHER) instanceof ShadowArcherPower archer) {
         if (!archer.processedArrows.contains(arrow.getUuid())) {
            archer.trackArrow(player, arrow);
         }

         if (archer.arrowTypes.get(arrow.getUuid()) != ArrowType.PHASE) {
            return false;
         } else {
            Vec3d velocity = arrow.getVelocity();
            double speed = velocity.length();
            if (speed < 1.0E-4) {
               return false;
            } else {
               double travelled = archer.phaseArrowTravel.getOrDefault(arrow.getUuid(), 0.0);
               double maxDistance = YPConfig.getInstance().shadow_archer_phase_max_distance;
               if (travelled >= maxDistance) {
                  archer.arrowTypes.remove(arrow.getUuid());
                  archer.arrowOwners.remove(arrow.getUuid());
                  archer.processedArrows.remove(arrow.getUuid());
                  archer.phaseArrowTravel.remove(arrow.getUuid());
                  arrow.discard();
                  return true;
               } else {
                  double step = Math.min(Math.max(0.8, speed * 1.05), maxDistance - travelled);
                  Vec3d direction = velocity.normalize();
                  Vec3d next = hitResult.getPos().add(direction.multiply(step));
                  arrow.setPosition(next.x, next.y, next.z);
                  archer.phaseArrowTravel.put(arrow.getUuid(), travelled + step);
                  ((EntityVelocityAccessor)arrow).invokeYppowersScheduleVelocityUpdate();
                  ServerWorld world = (ServerWorld)arrow.getEntityWorld();
                  world.spawnParticles(ParticleTypes.PORTAL, true, true, hitResult.getPos().x, hitResult.getPos().y, hitResult.getPos().z, 5, 0.12, 0.12, 0.12, 0.02);
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   public static boolean handleProjectileCollision(ProjectileEntity projectile, HitResult hitResult) {
      if (!(projectile instanceof ArrowEntity arrow && projectile.getOwner() instanceof ServerPlayerEntity player)) {
         return false;
      } else if (player.getEntityWorld().getServer() == null) {
         return false;
      } else {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (manager.getPowerType(player.getUuid()) != PowerType.SHADOW_ARCHER) {
            return false;
         } else if (!(manager.getPowerInstance(PowerType.SHADOW_ARCHER) instanceof ShadowArcherPower archer)) {
            return false;
         } else if (GraceManager.isActive(player.getEntityWorld().getServer())) {
            return false;
         } else {
            if (!archer.processedArrows.contains(arrow.getUuid())) {
               archer.trackArrow(player, arrow);
            }

            if (hitResult.getType() == Type.ENTITY) {
               archer.markArrowHit(arrow);
            }

            ArrowType type = archer.arrowTypes.get(arrow.getUuid());
            if (type != null && type != ArrowType.SPLIT_SHOT && type != ArrowType.HOMING && type != ArrowType.RAILSHOT && type != ArrowType.PHASE) {
               ServerWorld world = (ServerWorld)projectile.getEntityWorld();
               switch (type) {
                  case TNT:
                     ExplosionSourceType tntMode = TimeWarperDomainManager.mayModifyBlock(player, arrow.getBlockPos())
                        ? ExplosionSourceType.TNT
                        : ExplosionSourceType.NONE;
                     world.createExplosion(
                        player,
                        null,
                        null,
                        arrow.getX(),
                        arrow.getY(),
                        arrow.getZ(),
                        2.0F,
                        false,
                        tntMode,
                        ParticleTypes.EXPLOSION,
                        ParticleTypes.EXPLOSION_EMITTER,
                        WeightedPool.empty(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE
                     );
                     break;
                  case LIGHTNING:
                     Entity hit = hitResult.getType() == Type.ENTITY ? ((EntityHitResult)hitResult).getEntity() : null;
                     double x = hit == null ? arrow.getX() : hit.getX();
                     double y = hit == null ? arrow.getY() : hit.getY();
                     double z = hit == null ? arrow.getZ() : hit.getZ();

                     for (int i = 0; i < 2; i++) {
                        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
                        if (bolt != null) {
                           bolt.setCosmetic(true);
                           bolt.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
                           world.spawnEntity(bolt);
                        }
                     }

                     EpicVfx.cue(world, player.getId(), "shadow_archer", "lightning_hit", x, y, z, arrow.getVelocity(), 1.1F, 16, 0.0F);
                     break;
                  case ICE_PRISON:
                     Vec3d impact = hitResult.getPos();
                     double radius = 3.5;
                     Box area = new Box(impact.x - radius, impact.y - radius, impact.z - radius, impact.x + radius, impact.y + radius, impact.z + radius);
                     AllianceManager alliances = AllianceManager.get(world.getServer());
                     Set<LivingEntity> affected = new LinkedHashSet<>();
                     if (hitResult.getType() == Type.ENTITY
                        && ((EntityHitResult)hitResult).getEntity() instanceof LivingEntity living
                        && PowerCombatUtil.canHarm(player, living)) {
                        affected.add(living);
                     }

                     for (LivingEntity targetxx : world.getEntitiesByClass(
                        LivingEntity.class, area, targetxxx -> PowerCombatUtil.canHarm(player, targetxxx)
                     )) {
                        if (targetxx.getBoundingBox().getCenter().squaredDistanceTo(impact) <= radius * radius) {
                           affected.add(targetxx);
                        }
                     }

                     for (LivingEntity targetxxx : affected) {
                        targetxxx.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1));
                        targetxxx.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
                     }

                     world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, impact.x, impact.y, impact.z, 40, 1.0, 0.8, 1.0, 0.06);
                     world.spawnParticles(ParticleTypes.CLOUD, true, true, impact.x, impact.y, impact.z, 10, 0.45, 0.3, 0.45, 0.02);
                     EpicVfx.impact(world, impact.x, impact.y, impact.z, 0.9);
                     world.playSound(null, BlockPos.ofFloored(impact), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8F, 1.5F);
                     break;
                  case PINNING:
                     if (hitResult.getType() == Type.ENTITY
                        && ((EntityHitResult)hitResult).getEntity() instanceof LivingEntity target
                        && PowerCombatUtil.canHarm(player, target)) {
                        int duration = YPConfig.getInstance().shadow_archer_pinning_duration_ticks;
                        Vec3d direction = arrow.getVelocity().lengthSquared() > 1.0E-6 ? arrow.getVelocity().normalize() : player.getRotationVec(1.0F);
                        Vec3d start = target.getBoundingBox().getCenter();
                        Vec3d end = start.add(direction.multiply(1.75));
                        BlockHitResult anchor = world.raycast(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, target));
                        if (anchor.getType() == Type.BLOCK) {
                           target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 10, false, true, true));
                           target.setVelocity(Vec3d.ZERO);
                           world.spawnParticles(ParticleTypes.CRIT, true, true, target.getX(), target.getBodyY(0.55), target.getZ(), 18, 0.35, 0.45, 0.35, 0.02);
                           world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.45, 0.55, 0.45, 0.01);
                           world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.8F, 0.8F);
                        }
                     }
                     break;
                  case TRACER:
                     if (hitResult.getType() == Type.ENTITY
                        && ((EntityHitResult)hitResult).getEntity() instanceof LivingEntity target
                        && PowerCombatUtil.canHarm(player, target)) {
                        long expiry = world.getServer().getTicks() + YPConfig.getInstance().shadow_archer_tracer_duration_ticks;
                        archer.tracerMarks.put(player.getUuid(), new ShadowArcherPower.TracerMark(target.getUuid(), expiry));
                        target.addStatusEffect(
                           new StatusEffectInstance(
                              StatusEffects.GLOWING, Math.min(YPConfig.getInstance().shadow_archer_tracer_duration_ticks, 200), 0, false, true, true
                           )
                        );
                        world.spawnParticles(ParticleTypes.END_ROD, true, true, target.getX(), target.getBodyY(0.5), target.getZ(), 24, 0.45, 0.6, 0.45, 0.02);
                        world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 1.6F);
                     }
                     break;
                  case ECLIPSE: {
                     Vec3d eclipseImpact = hitResult.getPos();
                     double eclipseRadius = YPConfig.getInstance().shadow_archer_eclipse_radius;
                     int eclipseDuration = YPConfig.getInstance().shadow_archer_eclipse_duration_ticks;
                     Box eclipseArea = new Box(eclipseImpact.x - eclipseRadius, eclipseImpact.y - eclipseRadius, eclipseImpact.z - eclipseRadius, eclipseImpact.x + eclipseRadius, eclipseImpact.y + eclipseRadius, eclipseImpact.z + eclipseRadius);
                     AllianceManager eclipseAlliances = AllianceManager.get(world.getServer());

                     for (LivingEntity targetx : world.getEntitiesByClass(
                        LivingEntity.class, eclipseArea, entity -> PowerCombatUtil.canHarm(player, entity)
                     )) {
                        if (targetx.getBoundingBox().getCenter().squaredDistanceTo(eclipseImpact) <= eclipseRadius * eclipseRadius) {
                           targetx.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, eclipseDuration, 0, false, true, true));
                           targetx.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, eclipseDuration, 0, false, true, true));
                        }
                     }

                     player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, eclipseDuration, 0, false, true, true));
                     EpicVfx.darkOrb(world, eclipseImpact.x, eclipseImpact.y, eclipseImpact.z, Math.min(12.0, Math.max(1.5, eclipseRadius * 0.75)), 60);
                     EpicVfx.cue(world, player.getId(), "shadow_archer", "black_hole", eclipseImpact.x, eclipseImpact.y, eclipseImpact.z, Vec3d.ZERO, 1.2F, 24, 0.0F);
                     world.spawnParticles(ParticleTypes.LARGE_SMOKE, true, true, eclipseImpact.x, eclipseImpact.y, eclipseImpact.z, 14, eclipseRadius * 0.25, eclipseRadius * 0.25, eclipseRadius * 0.25, 0.01);
                     world.playSound(null, BlockPos.ofFloored(eclipseImpact), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.6F, 0.55F);
                     break;
                  }
                  default:
                     return false;
               }

               archer.arrowTypes.remove(arrow.getUuid());
               archer.arrowOwners.remove(arrow.getUuid());
               archer.processedArrows.remove(arrow.getUuid());
               archer.phaseArrowTravel.remove(arrow.getUuid());
               arrow.discard();
               return true;
            } else {
               return false;
            }
         }
      }
   }

   @Override
   public String hudStatusChip(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() == null) return "";
      ArrowType arrow = this.selectedArrow.getOrDefault(player.getUuid(), ArrowType.SPLIT_SHOT);
      return arrow.getDisplayName();
   }

   @Override
   public List<HudPill> hudPills(ServerPlayerEntity player, double energy) {
      if (player.getEntityWorld().getServer() == null) return List.of();
      YPConfig cfg = YPConfig.getInstance();
      UUID id = player.getUuid();
      boolean arrowsOn = specialArrowsEnabled(player);
      return List.of(
         HudPill.ability(AbilityKey.TERTIARY, "Morphs", this.morphCooldowns.getOrDefault(id, 0), cfg.shadow_archer_morph_cooldown_ticks, true),
         HudPill.ability(AbilityKey.QUATERNARY, "Arrow", 0, 1, true),
         HudPill.ability(AbilityKey.SHADOW_ARCHER_ARROW_SWAP, "Swap", this.arrowSwapCooldowns.getOrDefault(id, 0), cfg.shadow_archer_arrow_swap_cooldown_ticks, energy >= cfg.shadow_archer_arrow_swap_energy_cost),
         HudPill.toggle(AbilityKey.SHADOW_ARCHER_SPECIAL_ARROWS, "Arrows", arrowsOn)
      );
   }

   private record FlightSnapshot(boolean allowFlying, boolean flying) {
   }

   private record MorphViewState(boolean morphed, int disguiseEntityId, float eyeHeight, float entityHeight) {
   }

   private record TracerMark(UUID targetId, long expiresAtTick) {
   }
}
