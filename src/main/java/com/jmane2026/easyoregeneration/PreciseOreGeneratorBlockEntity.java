package com.jmane2026.easyoregeneration;

import com.jmane2026.easyoregeneration.config.EasyOreGenConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler.*;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;

public class PreciseOreGeneratorBlockEntity extends BlockEntity {
    private int timer = 0;

    private ItemStack internalSlot = ItemStack.EMPTY;

    public final ItemStackResourceHandler inventory = new ItemStackResourceHandler() {
        @Override
        protected ItemStack getStack() {
            return internalSlot;
        }

        @Override
        protected void setStack(ItemStack stack) {
            internalSlot = stack;
            setChanged();
        }
    };

    public PreciseOreGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(EasyOreGeneration.PRECISE_ORE_GENERATOR_BE.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;

        timer++;

        int configuredSpeed = EasyOreGenConfig.GENERATION_SPEED.get();
        if (timer >= configuredSpeed) {
            timer = 0;
            processExtraction();
        }

        exportInternalInventory();

        checkPlayerLooking();
    }

    private void processExtraction() {
        BlockPos abovePos = worldPosition.above();
        BlockState aboveState = level.getBlockState(abovePos);

        if (aboveState.is(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores")))) {
            int configuredQuantity = EasyOreGenConfig.GENERATION_QUANTITY.get();
            ItemStack itemsToProcess = new ItemStack(aboveState.getBlock().asItem(), configuredQuantity);
            ItemResource resource = ItemResource.of(itemsToProcess);

            int amountRemaining = itemsToProcess.getCount();

            Direction facing = getBlockState().getValue(PreciseOreGeneratorBlock.FACING);
            BlockPos outputPos = worldPosition.relative(facing);
            ResourceHandler<ItemResource> targetHandler = level.getCapability(Capabilities.Item.BLOCK, outputPos, facing.getOpposite());

            if (targetHandler != null) {
                try (Transaction transaction = Transaction.open(null)) {
                    long insertedExternally = targetHandler.insert(resource, amountRemaining, transaction);
                    if (insertedExternally > 0) {
                        transaction.commit();
                        amountRemaining -= (int) insertedExternally;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (amountRemaining > 0) {
                try (Transaction transaction = Transaction.open(null)) {
                    long insertedInternally = inventory.insert(resource, amountRemaining, transaction);
                    if (insertedInternally > 0) {
                        transaction.commit();
                        amountRemaining -= (int) insertedInternally;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void exportInternalInventory() {
        if (internalSlot.isEmpty()) return;

        ItemResource storedResource = ItemResource.of(internalSlot);
        int amountStored = internalSlot.getCount();

        Direction facing = getBlockState().getValue(PreciseOreGeneratorBlock.FACING);
        BlockPos outputPos = worldPosition.relative(facing);

        ResourceHandler<ItemResource> targetHandler = level.getCapability(Capabilities.Item.BLOCK, outputPos, facing.getOpposite());

        if (targetHandler != null) {
            try (Transaction transaction = Transaction.open(null)) {
                long inserted = targetHandler.insert(storedResource, amountStored, transaction);

                if (inserted > 0) {
                    inventory.extract(0, storedResource, (int) inserted, transaction);
                    transaction.commit();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!internalSlot.isEmpty()) {
            output.store("StoredItem", ItemStack.CODEC, internalSlot);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        this.internalSlot = input.read("StoredItem", ItemStack.CODEC)
                .orElse(ItemStack.EMPTY);
    }

    private void checkPlayerLooking() {
        List<? extends Player> players = level.players();

        for (Player player : players) {
            if (player instanceof ServerPlayer serverPlayer) {
                BlockHitResult hit = (BlockHitResult) player.pick(6.0D, 0.0F, false);

                if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(worldPosition)) {
                    if (!internalSlot.isEmpty()) {
                        String message = String.format("§e%dx %s",
                                internalSlot.getCount(),
                                internalSlot.getHoverName().getString());
                        serverPlayer.sendSystemMessage(Component.literal(message), true);
                    }
                }
            }
        }
    }
}