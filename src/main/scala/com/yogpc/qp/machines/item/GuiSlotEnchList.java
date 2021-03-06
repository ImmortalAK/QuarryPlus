package com.yogpc.qp.machines.item;

import com.yogpc.qp.machines.workbench.BlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.list.ExtendedList;

public class GuiSlotEnchList extends ExtendedList<GuiSlotEnchList.Entry> {

    private final GuiEnchList parent;

    public GuiSlotEnchList(Minecraft mcIn, int widthIn, int heightIn, int topIn, int bottomIn, int slotHeightIn, GuiEnchList parent) {
        super(mcIn, widthIn, heightIn, topIn, bottomIn, slotHeightIn);
        this.parent = parent;
        refreshList();
    }

    public void refreshList() {
        this.clearEntries();
        parent.buildModList(this::addEntry, Entry::new);
    }

    public class Entry extends ExtendedList.AbstractListEntry<Entry> {

        private final BlockData data;

        public Entry(BlockData data) {
            this.data = data;
        }

        public BlockData getData() {
            return data;
        }

        @Override
        @SuppressWarnings("IntegerDivisionInFloatingPointContext")
        public void render(int entryIdx, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean p_render_8_, float partialTicks) {
            String name = data.getDisplayText().getFormattedText();
            Minecraft.getInstance().fontRenderer.drawStringWithShadow(name,
                (GuiSlotEnchList.this.parent.width * 3 / 5 - Minecraft.getInstance().fontRenderer.getStringWidth(name)) / 2, top + 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double p_mouseClicked_1_, double p_mouseClicked_3_, int p_mouseClicked_5_) {
            GuiSlotEnchList.this.setSelected(this);
            return false;
        }
    }
}
