package com.infamous.call_of_the_wild.common.registry;

import com.infamous.call_of_the_wild.CallOfTheWild;
import com.infamous.call_of_the_wild.common.entity.EntityVariant;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public class COTWEntityDataSerializers {

    public static final DeferredRegister<EntityDataSerializer<?>> ENTITY_DATA_SERIALIZERS = DeferredRegister.create(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, CallOfTheWild.MODID);

    public static final RegistryObject<EntityDataSerializer<EntityVariant>> DOG_VARIANT = ENTITY_DATA_SERIALIZERS.register("dog_variant", () -> forgeId(COTWDogVariants.DOG_VARIANT_REGISTRY.get()));

    private static <T> EntityDataSerializer<T> forgeId(IForgeRegistry<T> registry) {
        return EntityDataSerializer.simple((fbb, t) -> fbb.writeRegistryIdUnsafe(registry, t), (fbb) -> fbb.readRegistryIdUnsafe(registry));
    }
}