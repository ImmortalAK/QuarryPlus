package com.yogpc.qp.machines.quarry;

import java.util.Optional;

import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.compat.BuildcraftHelper;
import com.yogpc.qp.machines.TranslationKeys;
import com.yogpc.qp.machines.base.QPBlock;
import com.yogpc.qp.utils.Holder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import scala.Symbol;

import static net.minecraft.state.properties.BlockStateProperties.FACING;

public class BlockSolidQuarry extends QPBlock {
    public static final Symbol SYMBOL = Symbol.apply("SolidFuelQuarry");

    public BlockSolidQuarry() {
        super(Block.Properties.create(Material.IRON)
            .hardnessAndResistance(1.5f, 10f)
            .sound(SoundType.STONE), QuarryPlus.Names.solidquarry, BlockItem::new);
        setDefaultState(getStateContainer().getBaseState().with(FACING, Direction.NORTH).with(QPBlock.WORKING(), false));
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        if (!worldIn.isRemote) {
            Direction facing = placer.getHorizontalFacing().getOpposite();
            worldIn.setBlockState(pos, state.with(FACING, facing), 2);
            Optional.ofNullable((TileSolidQuarry) worldIn.getTileEntity(pos)).ifPresent(t -> {
                t.G_ReInit();
                TileSolidQuarry.requestTicket.accept(t);
            });
        }
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, QPBlock.WORKING());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean b) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, b);
        if (!worldIn.isRemote) {
            Optional.ofNullable((TileSolidQuarry) worldIn.getTileEntity(pos)).ifPresent(TileSolidQuarry::G_renew_powerConfigure);
        }
    }

    @Override
    public boolean onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity playerIn,
                                    Hand hand, BlockRayTraceResult hit) {
        if (super.onBlockActivated(state, worldIn, pos, playerIn, hand, hit)) return true;
        ItemStack stack = playerIn.getHeldItem(hand);
        if (BuildcraftHelper.isWrench(playerIn, hand, stack, hit)) {
            Optional.ofNullable((TileSolidQuarry) worldIn.getTileEntity(pos)).ifPresent(TileSolidQuarry::G_ReInit);
            playerIn.sendStatusMessage(new TranslationTextComponent(TranslationKeys.QUARRY_RESTART), false);
            return true;
        }
        if (!playerIn.isSneaking()) {
            if (!worldIn.isRemote) {
                Optional.ofNullable((TileSolidQuarry) worldIn.getTileEntity(pos)).ifPresent(t ->
                    NetworkHooks.openGui(((ServerPlayerEntity) playerIn), t, pos)
                );
            }
            return true;
        }
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(IBlockReader worldIn) {
        return Holder.solidQuarryType().create();
    }
}
