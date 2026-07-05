package com.jmane2026.easyoregeneration;

import com.jmane2026.easyoregeneration.config.EasyOreGenConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

public class WaterGeneratorBlockEntity extends BlockEntity {
    private int timer = 0;
    private long amount = 0;
    private final long capacity = 8000; // 8 Buckets
    private final FluidResource fluid = FluidResource.of(Fluids.WATER);
    private boolean upgraded = false;

    private record FluidSnapshot(long amount, boolean upgraded) {}

    private final SnapshotJournal<FluidSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected FluidSnapshot createSnapshot() {
            return new FluidSnapshot(amount, upgraded);
        }

        @Override
        protected void revertToSnapshot(FluidSnapshot snapshot) {
            amount = snapshot.amount();
            upgraded = snapshot.upgraded();
        }

        @Override
        protected void onRootCommit(FluidSnapshot originalState) {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    private final ResourceHandler<FluidResource> fluidHandler = new ResourceHandler<>() {
        @Override
        public int size() { return 1; }

        @Override
        public FluidResource getResource(int slot) { return fluid; }

        @Override
        public long getAmountAsLong(int slot) { return amount; }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0; // Extraction only
        }

        @Override
        public int extract(int index, FluidResource resource, int amountToExtract, TransactionContext transaction) {
            if (amount <= 0 || amountToExtract <= 0 || !fluid.equals(resource)) return 0;
            int toExtract = (int) Math.min(amountToExtract, amount);
            if (toExtract > 0) {
                journal.updateSnapshots(transaction);
                amount -= toExtract;
            }
            return toExtract;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) { return capacity; }

        @Override
        public boolean isValid(int index, FluidResource resource) { return fluid.equals(resource); }
    };

    public ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        // Allow discovery (null) or front face extraction
        if (side == null || side == Direction.UP) {
            return fluidHandler;
        }
        return null;
    }

    public WaterGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(EasyOreGeneration.WATER_GENERATOR_BE.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;

        timer++;
        int configuredSpeed = EasyOreGenConfig.GENERATION_SPEED.get();
        int actualSpeed = upgraded ? Math.max(1, configuredSpeed / 2) : configuredSpeed;

        if (timer >= actualSpeed) {
            timer = 0;
            generateFluid();
        }

        exportToAbove();
    }

    private void generateFluid() {
        // Mimic ore quantity but convert to Millibuckets (e.g. 1 unit = 100mb or 1000mb)
        long quantity = EasyOreGenConfig.GENERATION_QUANTITY.get() * 200L;
        
        // Try to push directly to the container above first
        BlockPos targetPos = worldPosition.above();
        ResourceHandler<FluidResource> targetHandler = level.getCapability(Capabilities.Fluid.BLOCK, targetPos, Direction.DOWN);

        long remaining = quantity;
        if (targetHandler != null) {
            try (Transaction transaction = Transaction.open(null)) {
                long inserted = targetHandler.insert(fluid, (int) remaining, transaction);
                if (inserted > 0) {
                    transaction.commit();
                    remaining -= inserted;
                }
            } catch (Exception ignored) {}
        }

        // Store leftover in internal tank
        if (remaining > 0) {
            amount = Math.min(capacity, amount + remaining);
            setChanged();
        }
    }

    private void exportToAbove() {
        if (amount <= 0) return;

        BlockPos targetPos = worldPosition.above();
        ResourceHandler<FluidResource> targetHandler = level.getCapability(Capabilities.Fluid.BLOCK, targetPos, Direction.DOWN);

        if (targetHandler != null) {
            try (Transaction transaction = Transaction.open(null)) {
                long inserted = targetHandler.insert(fluid, (int) amount, transaction);
                if (inserted > 0) {
                    amount -= inserted;
                    transaction.commit();
                    setChanged();
                }
            } catch (Exception ignored) {}
        }
    }

    public boolean isUpgraded() {
        return upgraded;
    }

    public boolean applyUpgrade() {
        if (!upgraded) {
            this.upgraded = true;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("fluid_amount", this.amount);
        output.putBoolean("upgraded", this.upgraded);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.amount = input.getLongOr("fluid_amount", 0);
        this.upgraded = input.getBooleanOr("upgraded", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("fluid_amount", this.amount);
        tag.putBoolean("upgraded", this.upgraded);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}