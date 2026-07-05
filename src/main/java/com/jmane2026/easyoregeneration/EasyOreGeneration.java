package com.jmane2026.easyoregeneration;

import com.jmane2026.easyoregeneration.config.EasyOreGenConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(EasyOreGeneration.MODID)
public class EasyOreGeneration {
    public static final String MODID = "easyoregeneration";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<RandomOreGeneratorBlock> RANDOM_ORE_GENERATOR = BLOCKS.register("random_ore_generator",
            registryName -> new RandomOreGeneratorBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, registryName)).strength(3.0f)));

    public static final DeferredBlock<PreciseOreGeneratorBlock> PRECISE_ORE_GENERATOR = BLOCKS.register("precise_ore_generator",
            registryName -> new PreciseOreGeneratorBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, registryName)).strength(3.0f)));

    public static final DeferredBlock<WaterGeneratorBlock> WATER_GENERATOR = BLOCKS.register("water_generator",
            registryName -> new WaterGeneratorBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, registryName)).strength(3.0f)));

    public static final DeferredBlock<LavaGeneratorBlock> LAVA_GENERATOR = BLOCKS.register("lava_generator",
            registryName -> new LavaGeneratorBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, registryName)).strength(3.0f)));

    public static final Supplier<BlockEntityType<RandomOreGeneratorBlockEntity>> ORE_GEN_TYPE = BLOCK_ENTITIES.register(
            "random_ore_generator_be",
            () -> new BlockEntityType<>(
                    RandomOreGeneratorBlockEntity::new,
                    false,
                    RANDOM_ORE_GENERATOR.get()
            )
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PreciseOreGeneratorBlockEntity>> PRECISE_ORE_GENERATOR_BE =
            BLOCK_ENTITIES.register("precise_ore_generator",
                    () -> new BlockEntityType<>(
                            PreciseOreGeneratorBlockEntity::new,
                            false,
                            PRECISE_ORE_GENERATOR.get()
                    )
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterGeneratorBlockEntity>> WATER_GENERATOR_BE =
            BLOCK_ENTITIES.register("water_generator",
                    () -> new BlockEntityType<>(
                            WaterGeneratorBlockEntity::new,
                            false,
                            WATER_GENERATOR.get()
                    )
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LavaGeneratorBlockEntity>> LAVA_GENERATOR_BE =
            BLOCK_ENTITIES.register("lava_generator",
                    () -> new BlockEntityType<>(
                            LavaGeneratorBlockEntity::new,
                            false,
                            LAVA_GENERATOR.get()
                    )
            );

    public static final DeferredItem<Item> RANDOM_ORE_GENERATOR_ITEM = ITEMS.register("random_ore_generator",
            registryName -> new BlockItem(RANDOM_ORE_GENERATOR.get(), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> PRECISE_ORE_GENERATOR_ITEM = ITEMS.register("precise_ore_generator",
            registryName -> new BlockItem(PRECISE_ORE_GENERATOR.get(), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> UPGRADE_CARD = ITEMS.register("upgrade_card",
            registryName -> new Item(new Item.Properties().stacksTo(64).setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> WATER_GENERATOR_ITEM = ITEMS.register("water_generator",
            registryName -> new BlockItem(WATER_GENERATOR.get(), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> LAVA_GENERATOR_ITEM = ITEMS.register("lava_generator",
            registryName -> new BlockItem(LAVA_GENERATOR.get(), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final Supplier<CreativeModeTab> EASY_ORE_GEN_TAB = CREATIVE_MODE_TABS.register("easy_ore_generation_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.easy_ore_generation_tab"))
                    .icon(() -> RANDOM_ORE_GENERATOR_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(RANDOM_ORE_GENERATOR_ITEM.get());
                        output.accept(PRECISE_ORE_GENERATOR_ITEM.get());
                        output.accept(WATER_GENERATOR_ITEM.get());
                        output.accept(LAVA_GENERATOR_ITEM.get());
                        output.accept(UPGRADE_CARD.get());
                    })
                    .build()
    );

    public EasyOreGeneration(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modContainer.registerConfig(ModConfig.Type.COMMON, EasyOreGenConfig.SPEC);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                PRECISE_ORE_GENERATOR_BE.get(),
                (be, side) -> be.getItemHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                WATER_GENERATOR_BE.get(),
                (be, side) -> be.getFluidHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                LAVA_GENERATOR_BE.get(),
                (be, side) -> be.getFluidHandler(side)
        );
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }
}
