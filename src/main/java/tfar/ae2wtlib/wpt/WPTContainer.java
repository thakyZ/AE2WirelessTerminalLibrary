package tfar.ae2wtlib.wpt;

import alexiil.mc.lib.attributes.item.FixedItemInv;
import alexiil.mc.lib.attributes.item.compat.FixedInventoryVanillaWrapper;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.ICraftingHelper;
import appeng.api.definitions.IDefinitions;
import appeng.api.networking.IGridNode;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerLocator;
import appeng.container.ContainerNull;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.MEMonitorableContainer;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.*;
import appeng.core.Api;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.IContainerCraftingPacket;
import appeng.items.storage.ViewCellItem;
import appeng.me.helpers.MachineSource;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.inv.WrapperCursorItemHandler;
import appeng.util.item.AEItemStack;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.CraftResultInventory;
import net.minecraft.inventory.container.CraftingResultSlot;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.items.IItemHandler;
import tfar.ae2wtlib.Config;
import tfar.ae2wtlib.terminal.FixedWTInv;
import tfar.ae2wtlib.terminal.IWTInvHolder;
import tfar.ae2wtlib.terminal.ItemWT;
import tfar.ae2wtlib.wut.ItemWUT;
import tfar.ae2wtlib.util.ContainerHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Util;
import net.minecraft.world.World;

import java.awt.event.ContainerListener;

public class WPTContainer extends MEMonitorableContainer implements IAEAppEngInventory, IOptionalSlotHost, IContainerCraftingPacket, IWTInvHolder {

    public static ContainerType<?> TYPE;

    public static final ContainerHelper<WPTContainer, WPTGuiObject> helper = new ContainerHelper<>(WPTContainer::new, WPTGuiObject.class);

    public static WPTContainer fromNetwork(int windowId, PlayerInventory inv, PacketBuffer buf) {
        return helper.fromNetwork(windowId, inv, buf);
    }

    private final FakeCraftingMatrixSlot[] craftingSlots = new FakeCraftingMatrixSlot[9];
    private final OptionalFakeSlot[] outputSlots = new OptionalFakeSlot[3];
    private ICraftingRecipe currentRecipe;
    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);
    private final AppEngInternalInventory crafting;
    private final WirelessPatternTermSlot craftSlot;
    private final RestrictedInputSlot patternSlotIN;
    private final RestrictedInputSlot patternSlotOUT;
    private final ICraftingHelper craftingHelper = Api.INSTANCE.crafting();

    public static boolean open(PlayerEntity player, ContainerLocator locator) {
        return helper.open(player, locator);
    }

    private final WPTGuiObject wptGUIObject;

    @GuiSync(97)
    public boolean craftingMode;
    @GuiSync(96)
    public boolean substitute;

    public WPTContainer(int id, final PlayerInventory ip, final WPTGuiObject gui) {
        super(TYPE, id, ip, gui, true);
        wptGUIObject = gui;

        final int slotIndex = ((IInventorySlotAware) wptGUIObject).getInventorySlot();
        lockPlayerInventorySlot(slotIndex);

        final AppEngInternalInventory patternInv = getPatternTerminal().getInventoryByName("pattern");
        final AppEngInternalInventory output = getPatternTerminal().getInventoryByName("output");

        final FixedWTInv fixedWPTInv = new FixedWTInv(getPlayerInv(), wptGUIObject.getItemStack(), this);

        crafting = getPatternTerminal().getInventoryByName("crafting");

        for(int y = 0; y < 3; y++) {
            for(int x = 0; x < 3; x++)
                addSlot(craftingSlots[x + y * 3] = new FakeCraftingMatrixSlot(crafting, x + y * 3, 18 + x * 18, -76 + y * 18));
        }

        addSlot(craftSlot = new WirelessPatternTermSlot(ip.player, getActionSource(), getPowerSource(), gui, crafting, patternInv, cOut, 110, -76 + 18, this, 2, this));
        craftSlot.setIIcon(-1);

        for(int y = 0; y < 3; y++) {
            addSlot(outputSlots[y] = new PatternOutputsSlot(output, this, y, 110, -76 + y * 18, 0, 0, 1));
            outputSlots[y].setRenderDisabled(false);
            outputSlots[y].setIIcon(-1);
        }

        addSlot(new AppEngSlot(fixedWPTInv, FixedWTInv.INFINITY_BOOSTER_CARD, 80, -20));

        addSlot(patternSlotIN = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.BLANK_PATTERN,
                patternInv, 0, 147, -72 - 9, getPlayerInventory()));
        addSlot(patternSlotOUT = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.ENCODED_PATTERN,
                patternInv, 1, 147, -72 + 34, getPlayerInventory()));

        if(isClient()) {//FIXME set craftingMode and substitute serverside
            craftingMode = ItemWT.getBoolean(wptGUIObject.getItemStack(), "craftingMode");
            substitute = ItemWT.getBoolean(wptGUIObject.getItemStack(), "substitute");

            PacketBuffer buf = PacketByteBufs.create();
            buf.writeString("PatternTerminal.CraftMode");
            int i;
            if(craftingMode) i = 1;
            else i = 0;
            buf.writeByte(i);
            ClientPlayNetworking.send(new Identifier("ae2wtlib", "general"), buf);
            buf = PacketByteBufs.create();
            buf.writeString("PatternTerminal.Substitute");
            if(substitute) i = 1;
            else i = 0;
            buf.writeByte(i);
            ClientPlayNetworking.send(new Identifier("ae2wtlib", "general"), buf);
        }
    }

    private int ticks = 0;

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if(!wptGUIObject.rangeCheck()) {
            if(isValidContainer()) {
                getPlayerInv().player.sendSystemMessage(PlayerMessages.OutOfRange.get(), Util.NIL_UUID);
                ((ServerPlayerEntity) getPlayerInv().player).closeHandledScreen();
            }
            setValidContainer(false);
        } else {
            double powerMultiplier = Config.getPowerMultiplier(wptGUIObject.getRange(), wptGUIObject.isOutOfRange());
            ticks++;
            if(ticks > 10) {
                wptGUIObject.extractAEPower((powerMultiplier) * ticks, Actionable.MODULATE, PowerMultiplier.CONFIG);
                ticks = 0;
            }

            if(wptGUIObject.extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.ONE) == 0) {
                if(isValidContainer()) {
                    getPlayerInv().player.sendSystemMessage(PlayerMessages.DeviceNotPowered.get(), Util.DUMMY_UUID);
                    ((ServerPlayerEntity) getPlayerInv().player).closeContainer();
                }
                setValidContainer(false);
            }
        }

        if(isCraftingMode() != getPatternTerminal().isCraftingRecipe()) {
            setCraftingMode(getPatternTerminal().isCraftingRecipe());
            updateOrderOfOutputSlots();
        }

        if(substitute != getPatternTerminal().isSubstitution()) {
            substitute = getPatternTerminal().isSubstitution();
            ItemWT.setBoolean(wptGUIObject.getItemStack(), substitute, "substitute");
        }
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        super.onUpdate(field, oldValue, newValue);

        if(field.equals("craftingMode")) {
            getAndUpdateOutput();
            updateOrderOfOutputSlots();
        }
    }

    @Override
    public void onSlotChange(final Slot s) {
        if(s == patternSlotOUT && isServer()) {
            for(final ContainerListener listener : getListeners()) {
                for(int i = 0; i < slots.size(); i++) {
                    Slot slot = slots.get(i);
                    if(slot instanceof OptionalFakeSlot || slot instanceof FakeCraftingMatrixSlot)
                        listener.onSlotUpdate(this, i, slot.getStack());
                }
                if(listener instanceof ServerPlayerEntity)
                    ((ServerPlayerEntity) listener).skipPacketSlotUpdates = false;
            }
            detectAndSendChanges();
        }

        if(s == craftSlot && isClient()) getAndUpdateOutput();

        if(isClient() && isCraftingMode()) {
            for(Slot slot : craftingSlots) if(s == slot) getAndUpdateOutput();
            for(Slot slot : outputSlots) if(s == slot) getAndUpdateOutput();
        }
    }

    private void setSlotX(Slot s, int x) {
        ((SlotMixin) s).setX(x);
    }

    private void updateOrderOfOutputSlots() {
        if(!isCraftingMode()) {
            setSlotX(craftSlot, -9000);

            for(int y = 0; y < 3; y++) setSlotX(outputSlots[y], outputSlots[y].getX());
        } else {
            setSlotX(craftSlot, craftSlot.getX());

            for(int y = 0; y < 3; y++) setSlotX(outputSlots[y], -9000);
        }
    }

    /**
     * Callback for when the crafting matrix is changed.
     */

    @Override
    public boolean canInteractWith(PlayerEntity player) {
        return true;
    }

    @Override
    public void saveChanges() {}

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {}

    public void encode() {
        ItemStack output = patternSlotOUT.getStack();

        final ItemStack[] in = getInputs();
        final ItemStack[] out = getOutputs();

        // if there is no input, this would be silly.
        if(in == null || out == null || isCraftingMode() && currentRecipe == null) return;

        // first check the output slots, should either be null, or a pattern
        if(!output.isEmpty() && !craftingHelper.isEncodedPattern(output))
            return; //if nothing is there we should snag a new pattern.
        else if(output.isEmpty()) {
            output = patternSlotIN.getStack();
            if(output.isEmpty() || !isPattern(output)) return; // no blanks.

            // remove one, and clear the input slot.
            output.setCount(output.getCount() - 1);
            if(output.getCount() == 0) patternSlotIN.setStack(ItemStack.EMPTY);

            // let the crafting helper create a new encoded pattern
            output = null;
        }

        if(isCraftingMode())
            output = craftingHelper.encodeCraftingPattern(output, currentRecipe, in, out[0], isSubstitute());
        else output = craftingHelper.encodeProcessingPattern(output, in, out);
        patternSlotOUT.setStack(output);
    }

    private ItemStack[] getInputs() {
        final ItemStack[] input = new ItemStack[9];
        boolean hasValue = false;

        for(int x = 0; x < craftingSlots.length; x++) {
            input[x] = craftingSlots[x].getStack();
            if(!input[x].isEmpty()) hasValue = true;
        }

        if(hasValue) return input;
        return null;
    }

    private ItemStack[] getOutputs() {
        if(isCraftingMode()) {
            final ItemStack out = getAndUpdateOutput();
            if(!out.isEmpty() && out.getCount() > 0) return new ItemStack[]{out};
        } else {
            boolean hasValue = false;
            final ItemStack[] list = new ItemStack[3];

            for(int i = 0; i < outputSlots.length; i++) {
                final ItemStack out = outputSlots[i].getStack();
                list[i] = out;
                if(!out.isEmpty()) hasValue = true;
            }
            if(hasValue) return list;
        }

        return null;
    }

    @Override
    public FixedItemInv getInventoryByName(final String name) {
        if(name.equals("player")) return new FixedInventoryVanillaWrapper(getPlayerInventory());
        return getPatternTerminal().getInventoryByName(name);
    }

    private boolean isPattern(final ItemStack output) {
        if(output.isEmpty()) return false;

        final IDefinitions definitions = Api.instance().definitions();
        return definitions.materials().blankPattern().isSameAs(output);
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        if(idx == 1) return isServer() ? !getPatternTerminal().isCraftingRecipe() : !isCraftingMode();
        else if(idx == 2) return isServer() ? getPatternTerminal().isCraftingRecipe() : isCraftingMode();
        else return false;
    }

    public void craftOrGetItem(final IAEItemStack slotItem, final boolean shift, final IAEItemStack[] pattern) {

        if(slotItem != null && getCellInventory() != null) {
            final IAEItemStack out = slotItem.copy();
            InventoryAdaptor inv = new AppEngInternalInventory(new WrapperCursorItemHandler(getPlayerInv().player.inventory));
            final InventoryAdaptor playerInv = InventoryAdaptor.getAdaptor(getPlayerInv().player);

            if(shift) inv = playerInv;

            if(!inv.simulateAdd(out.createItemStack()).isEmpty()) return;

            final IAEItemStack extracted = Platform.poweredExtraction(getPowerSource(), getCellInventory(), out, getActionSource());
            final PlayerEntity p = getPlayerInv().player;

            if(extracted != null) {
                inv.addItems(extracted.createItemStack());
                if(p instanceof ServerPlayerEntity) updateHeld((ServerPlayerEntity) p);
                detectAndSendChanges();
                return;
            }

            final CraftingInventory craftingInventory = new CraftingInventory(new ContainerNull(), 3, 3);
            final CraftingInventory real = new CraftingInventory(new ContainerNull(), 3, 3);

            for(int x = 0; x < 9; x++)
                craftingInventory.setInventorySlotContents(x, pattern[x] == null ? ItemStack.EMPTY : pattern[x].createItemStack());

            final IRecipe<CraftingInventory> r = p.world.getRecipeManager().getRecipe(IRecipeType.CRAFTING, craftingInventory, p.world).orElse(null);

            if(r == null) return;

            final IMEMonitor<IAEItemStack> storage = getPatternTerminal()
                    .getInventory(Api.instance().storage().getStorageChannel(IItemStorageChannel.class));
            final IItemList<IAEItemStack> all = storage.getStorageList();

            final ItemStack is = r.getCraftingResult(craftingInventory);

            for(int x = 0; x < craftingInventory.getSizeInventory(); x++) {
                if(!craftingInventory.getStackInSlot(x).isEmpty()) {
                    final ItemStack pulled = Platform.extractItemsByRecipe(getPowerSource(), getActionSource(), storage, p.world, r, is, craftingInventory, craftingInventory.getStack(x), x, all, Actionable.MODULATE, ViewCellItem.createFilter(getViewCells()));
                    real.setInventorySlotContents(x, pulled);
                }
            }

            final IRecipe<CraftingInventory> rr = p.world.getRecipeManager()
                    .getRecipe(IRecipeType.CRAFTING, real, p.world).orElse(null);

            if(rr == r && Platform.itemComparisons().isSameItem(rr.getCraftingResult(real), is)) {
                final CraftResultInventory craftingResult = new CraftResultInventory();
                craftingResult.setRecipeUsed(rr);

                final CraftingResultSlot sc = new CraftingResultSlot(p, real, craftingResult, 0, 0, 0);
                sc.onTake(p, is);

                for(int x = 0; x < real.getSizeInventory(); x++) {
                    final ItemStack failed = playerInv.addItems(real.getStackInSlot(x));

                    if(!failed.isEmpty()) p.dropItem(failed, false);
                }

                inv.addItems(is);
                if(p instanceof ServerPlayerEntity) updateHeld((ServerPlayerEntity) p);
                detectAndSendChanges();
            } else {
                for(int x = 0; x < real.getSizeInventory(); x++) {
                    final ItemStack failed = real.getStackInSlot(x);
                    if(!failed.isEmpty()) getCellInventory().injectItems(AEItemStack.fromItemStack(failed),
                            Actionable.MODULATE, new MachineSource(getPatternTerminal()));
                }
            }
        }
    }

    private ItemStack getAndUpdateOutput() {
        final World world = getPlayerInv().player.world;
        final CraftingInventory ic = new CraftingInventory(this, 3, 3);

        for(int x = 0; x < ic.getSizeInventory(); x++) ic.setInventorySlotContents(x, crafting.getStackInSlot(x));

        if(currentRecipe == null || !currentRecipe.matches(ic, world))
            currentRecipe = world.getRecipeManager().getFirstMatch(IRecipeType.CRAFTING, ic, world).orElse(null);

        final ItemStack is;

        if(currentRecipe == null) is = ItemStack.EMPTY;
        else is = currentRecipe.getCraftingResult(ic);

        cOut.forceSetInvStack(0, is);
        return is;
    }

    @Override
    public boolean useRealItems() {
        return false;
    }

    public WPTGuiObject getPatternTerminal() {
        return wptGUIObject;
    }

    private boolean isSubstitute() {
        return substitute;
    }

    public boolean isCraftingMode() {
        return craftingMode;
    }

    private void setCraftingMode(final boolean craftingMode) {
        if(craftingMode != this.craftingMode) {
            this.craftingMode = craftingMode;
            ItemWT.setBoolean(wptGUIObject.getItemStack(), craftingMode, "craftingMode");
        }
    }

    public void clear() {
        for(final Slot s : craftingSlots) s.setStack(ItemStack.EMPTY);
        for(final Slot s : outputSlots) s.setStack(ItemStack.EMPTY);

        sendContentUpdates();
        getAndUpdateOutput();
    }

    @Override
    public IGridNode getNetworkNode() {
        return wptGUIObject.getActionableNode();
    }

    public boolean isWUT() {
        return wptGUIObject.getItemStack().getItem() instanceof ItemWUT;
    }

    @Override
    public ItemStack[] getViewCells() {
        return wptGUIObject.getViewCellStorage().getViewCells();
    }
}