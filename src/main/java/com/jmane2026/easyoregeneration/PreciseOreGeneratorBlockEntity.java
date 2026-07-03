package com.jmane2026.easyoregeneration;

import com.jmane2026.easyoregeneration.config.EasyOreGenConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PreciseOreGeneratorBlockEntity extends BlockEntity {
    private int timer = 0;

    private ItemStack storedItem = ItemStack.EMPTY;
    private int count = 0;

    private record PreciseSnapshot(ItemStack item, int count) {}

    private final SnapshotJournal<PreciseSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected PreciseSnapshot createSnapshot() {
            return new PreciseSnapshot(storedItem.copy(), count);
        }

        @Override
        protected void revertToSnapshot(PreciseSnapshot snapshot) {
            storedItem = snapshot.item();
            count = snapshot.count();
        }

        @Override
        protected void onRootCommit(PreciseSnapshot originalState) {
            markUpdated();
        }
    };

    private final ResourceHandler<ItemResource> itemHandler = new ResourceHandler<>() {
        @Override
        public int size() { return 1; }

        @Override
        public ItemResource getResource(int slot) {
            return ItemResource.of(storedItem);
        }

        @Override
        public long getAmountAsLong(int slot) {
            return count;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (storedItem.isEmpty() || count <= 0 || amount <= 0 || !ItemResource.of(storedItem).equals(resource)) return 0;

            int toExtract = Math.min(amount, count);
            if (toExtract > 0) {
                journal.updateSnapshots(transaction);
                count -= toExtract;
                if (count <= 0) storedItem = ItemStack.EMPTY;
            }
            return toExtract;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return 64;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            if (resource.isEmpty()) return false;
            return count <= 0 || ItemResource.of(storedItem).equals(resource);
        }
    };

    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        Direction facing = getBlockState().getValue(PreciseOreGeneratorBlock.FACING);
        if (side == null || side == facing) {
            return itemHandler;
        }
        return null;
    }

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
            BlockPos targetPos = worldPosition.relative(facing);
            ResourceHandler<ItemResource> targetHandler = level.getCapability(Capabilities.Item.BLOCK, targetPos, facing.getOpposite());

            if (targetHandler != null) {
                try (Transaction transaction = Transaction.open(null)) {
                    long insertedExternally = targetHandler.insert(resource, amountRemaining, transaction);
                    if (insertedExternally > 0) {
                        transaction.commit();
                        amountRemaining -= (int) insertedExternally;
                    }
                } catch (Exception ignored) {}
            }

            if (amountRemaining > 0) {
                try (Transaction transaction = Transaction.open(null)) {
                    long insertedInternally = insertInternal(resource, amountRemaining, transaction);
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

    private int insertInternal(ItemResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) return 0;

        boolean canInsert = count <= 0 || ItemResource.of(storedItem).equals(resource);
        if (!canInsert) return 0;

        int space = 64 - count;
        int toTake = Math.min(amount, space);

        if (toTake > 0) {
            journal.updateSnapshots(transaction);
            if (count <= 0) storedItem = resource.toStack(1);
            count += toTake;
        }
        return toTake;
    }

    private void exportInternalInventory() {
        if (storedItem.isEmpty() || count <= 0) return;

        ItemResource storedResource = ItemResource.of(storedItem);
        int amountStored = count;

        Direction facing = getBlockState().getValue(PreciseOreGeneratorBlock.FACING);
        BlockPos targetPos = worldPosition.relative(facing);
        ResourceHandler<ItemResource> targetHandler = level.getCapability(Capabilities.Item.BLOCK, targetPos, facing.getOpposite());

        if (targetHandler != null) {
            try (Transaction transaction = Transaction.open(null)) {
                long inserted = targetHandler.insert(storedResource, amountStored, transaction);
                if (inserted > 0) {
                    itemHandler.extract(0, storedResource, (int) inserted, transaction);
                    transaction.commit();
                }
            } catch (Exception ignored) {}
        }
    }

    private void markUpdated() {
        this.setChanged();

        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("stored_item", ItemStack.OPTIONAL_CODEC, this.storedItem);
        output.putInt("count", this.count);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.storedItem = input.read("stored_item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.count = input.getIntOr("count", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.storedItem)
                .ifSuccess(result -> tag.put("stored_item", result));
        tag.putInt("count", this.count);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void checkPlayerLooking() {
        List<? extends Player> players = level.players();

        for (Player player : players) {
            if (player instanceof ServerPlayer serverPlayer) {
                HitResult hit = player.pick(6.0D, 0.0F, false);

                if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                    if (blockHit.getBlockPos().equals(worldPosition)) {
                        if (count > 0 && !storedItem.isEmpty()) {
                            String message = String.format("§e%dx %s",
                                    count,
                                    storedItem.getHoverName().getString());
                            serverPlayer.sendSystemMessage(Component.literal(message), true);
                        }
                    }
                }
            }
        }
    }
}