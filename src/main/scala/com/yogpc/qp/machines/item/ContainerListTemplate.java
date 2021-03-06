package com.yogpc.qp.machines.item;

import com.yogpc.qp.machines.base.SlotCanTake;
import com.yogpc.qp.machines.base.SlotTile;
import com.yogpc.qp.utils.Holder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class ContainerListTemplate extends Container {
    public final IInventory craftMatrix = new Inventory(1) {
        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) {
            return stack.getItem() instanceof BlockItem;
        }
    };

    public ContainerListTemplate(int id, PlayerEntity player, BlockPos pos) {
        super(Holder.templateContainerType(), id);
        addSlot(new SlotTile(this.craftMatrix, 0, 141, 90));
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(player.inventory, j + i * 9 + 9, 8 + j * 18, 135 + i * 18));
            }
        }
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new SlotCanTake(player.inventory, k, 8 + k * 18, 193, k != player.inventory.currentItem));
        }
    }

    @Override
    public void onContainerClosed(PlayerEntity playerIn) {
        super.onContainerClosed(playerIn);
        playerIn.inventory.placeItemBackInInventory(playerIn.world, craftMatrix.getStackInSlot(0));
    }

    @Override
    public ItemStack transferStackInSlot(PlayerEntity playerIn, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(PlayerEntity playerIn) {
        return true;
    }
}
