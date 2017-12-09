package com.yogpc.qp;

import com.yogpc.qp.tile.TilePump;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public class ProxyCommon {
    public void openPumpGui(World worldIn, EntityPlayer playerIn, EnumFacing facing, TilePump pump) {
        if (!worldIn.isRemote) pump.S_OpenGUI(facing, playerIn);
    }

    public EntityPlayer getPacketPlayer(final INetHandler inh) {
        if (inh instanceof NetHandlerPlayServer)
            return ((NetHandlerPlayServer) inh).playerEntity;
        return null;
    }

    public World getPacketWorld(final INetHandler inh) {
        if (inh instanceof NetHandlerPlayServer)
            return ((NetHandlerPlayServer) inh).playerEntity.getEntityWorld();
        return null;
    }

    public void removeEntity(final Entity e) {
        e.world.removeEntity(e);
    }

    public World getClientWorld() {
        return null;
    }

    public void registerTextures() {
    }
}
