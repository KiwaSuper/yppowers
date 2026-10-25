package com.yppowers.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** Registration for YP Powers' custom entities. */
public final class YPEntities {
    public static final RegistryKey<EntityType<?>> THOR_MACE_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of("yppowers", "thor_mace"));

    public static final EntityType<ThorMaceEntity> THOR_MACE =
            EntityType.Builder.<ThorMaceEntity>create(ThorMaceEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f)
                    .maxTrackingRange(4)
                    .trackingTickInterval(10)
                    .disableSummon()
                    .disableSaving()
                    .build(THOR_MACE_KEY);

    private YPEntities() {}

    public static void register() {
        Registry.register(Registries.ENTITY_TYPE, THOR_MACE_KEY, THOR_MACE);
    }
}
