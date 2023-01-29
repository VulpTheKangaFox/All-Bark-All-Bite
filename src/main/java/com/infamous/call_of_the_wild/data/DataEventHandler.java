package com.infamous.call_of_the_wild.data;

import com.infamous.call_of_the_wild.AllBarkAllBite;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = AllBarkAllBite.MODID)
public class DataEventHandler {

    @SubscribeEvent
    static void onGatherData(GatherDataEvent event){
        boolean isClient = event.includeClient();
        boolean isServer = event.includeServer();

        DataGenerator generator = event.getGenerator();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        generator.addProvider(isClient, ABABLangProvider.create(generator));
        generator.addProvider(isClient, ABABItemModelProvider.create(generator, existingFileHelper));

        generator.addProvider(isServer, ABABEntityTypeTagProvider.create(generator, existingFileHelper));

        ABABBlockTagProvider blockTagProvider = new ABABBlockTagProvider(generator, existingFileHelper);
        generator.addProvider(isServer, blockTagProvider);
        generator.addProvider(isServer, ABABInstrumentTagsProvider.create(generator, existingFileHelper));
        generator.addProvider(isServer, ABABItemTagProvider.create(generator, blockTagProvider, existingFileHelper));
        generator.addProvider(isServer, ABABGameEventTagsProvider.create(generator, existingFileHelper));
        generator.addProvider(isServer, ABABStructureTagsProvider.create(generator, existingFileHelper));

        generator.addProvider(isServer,ABABLootTableProvider.create(generator));
    }
}
