package com.jmane2026.easyoregeneration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class RandomOreGeneratorBlockEntity extends BlockEntity {
    private int timer = 0;
    private static final int DELAY = 100; // Check every 5 seconds (20 ticks = 1s)

    private static final List<Block> ORES = List.of(
            Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
            Blocks.COAL_ORE, Blocks.COPPER_ORE, Blocks.LAPIS_ORE
    );

    public RandomOreGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(EasyOreGeneration.ORE_GEN_TYPE.get(), pos, state);
    }

    public void tick(Level level, BlockPos pos) {
        timer++;
        if (timer >= DELAY) {
            timer = 0;
            tryGenerate(level, pos);
        }
    }

    public void tryGenerate(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir()) {

            TagKey<Block> oreTag = TagKey.create(Registries.BLOCK, Identifier.parse("c:ores"));

            var registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);

            var oreHolders = registry.get(oreTag);

            if (oreHolders.isPresent()) {
                var tagContents = oreHolders.get();
                if (tagContents.size() > 0) {
                    int randomIndex = level.getRandom().nextInt(tagContents.size());
                    Holder<Block> randomOreHolder = tagContents.get(randomIndex);

                    level.setBlockAndUpdate(above, randomOreHolder.value().defaultBlockState());
                    this.timer = 0;
                    return;
                }
            }

            level.setBlockAndUpdate(above, Blocks.IRON_ORE.defaultBlockState());
        }
    }
}