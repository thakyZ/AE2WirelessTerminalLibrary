package de.mari_023.fabric.ae2wtlib.wpt;

import alexiil.mc.lib.attributes.item.FixedItemInv;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.tiles.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.core.Api;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.networking.WirelessBlockEntity;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import de.mari_023.fabric.ae2wtlib.FixedViewCellInventory;
import de.mari_023.fabric.ae2wtlib.IInfinityBoosterCardHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class WPTGuiObject implements IPortableCell, IActionHost, IInventorySlotAware, IAEAppEngInventory/*, IViewCellStorage*/ {

    private final ItemStack effectiveItem;
    private final IWirelessTermHandler wth;
    private final PlayerEntity myPlayer;
    private IGrid targetGrid;
    private IStorageGrid sg;
    private IMEMonitor<IAEItemStack> itemStorage;
    private IWirelessAccessPoint myWap;
    private double sqRange = Double.MAX_VALUE;
    private double myRange = Double.MAX_VALUE;
    private final int inventorySlot;
    private final FixedViewCellInventory fixedViewCellInventory = new FixedViewCellInventory();
    private boolean craftingMode = true;
    private boolean substitute = false;
    private final AppEngInternalInventory crafting = new AppEngInternalInventory(this, 9);
    private final AppEngInternalInventory output = new AppEngInternalInventory(this, 3);
    private final AppEngInternalInventory pattern = new AppEngInternalInventory(this, 2);

    public WPTGuiObject(final IWirelessTermHandler wh, final ItemStack is, final PlayerEntity ep, int inventorySlot) {
        String encryptionKey = wh.getEncryptionKey(is);
        effectiveItem = is;
        myPlayer = ep;
        wth = wh;
        this.inventorySlot = inventorySlot;

        ILocatable obj = null;

        try {
            final long encKey = Long.parseLong(encryptionKey);
            obj = Api.instance().registries().locatable().getLocatableBy(encKey);
        } catch(final NumberFormatException ignored) {}

        if(obj instanceof IActionHost) {
            final IGridNode n = ((IActionHost) obj).getActionableNode();
            if(n != null) {
                targetGrid = n.getGrid();
                sg = targetGrid.getCache(IStorageGrid.class);
                itemStorage = sg.getInventory(Api.instance().storage().getStorageChannel(IItemStorageChannel.class));
            }
        }
    }

    public double getRange() {
        return myRange;
    }

    public IStorageGrid getIStorageGrid() {
        return sg;
    }

    @Override
    public <T extends IAEStack<T>> IMEMonitor<T> getInventory(IStorageChannel<T> channel) {
        return sg.getInventory(channel);
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver<IAEItemStack> l, final Object verificationToken) {
        if(itemStorage != null) {
            itemStorage.addListener(l, verificationToken);
        }
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver<IAEItemStack> l) {
        if(itemStorage != null) {
            itemStorage.removeListener(l);
        }
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out) {
        if(itemStorage != null) {
            return itemStorage.getAvailableItems(out);
        }
        return out;
    }

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        if(itemStorage != null) {
            return itemStorage.getStorageList();
        }
        return null;
    }

    @Override
    public AccessRestriction getAccess() {
        if(itemStorage != null) {
            return itemStorage.getAccess();
        }
        return AccessRestriction.NO_ACCESS;
    }

    @Override
    public boolean isPrioritized(final IAEItemStack input) {
        if(itemStorage != null) {
            return itemStorage.isPrioritized(input);
        }
        return false;
    }

    @Override
    public boolean canAccept(final IAEItemStack input) {
        if(itemStorage != null) {
            return itemStorage.canAccept(input);
        }
        return false;
    }

    @Override
    public int getPriority() {
        if(itemStorage != null) {
            return itemStorage.getPriority();
        }
        return 0;
    }

    @Override
    public int getSlot() {
        if(itemStorage != null) {
            return itemStorage.getSlot();
        }
        return 0;
    }

    @Override
    public boolean validForPass(final int i) {
        return itemStorage.validForPass(i);
    }

    @Override
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable type, final IActionSource src) {
        if(itemStorage != null) {
            return itemStorage.injectItems(input, type, src);
        }
        return input;
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final IActionSource src) {
        if(itemStorage != null) {
            return itemStorage.extractItems(request, mode, src);
        }
        return null;
    }

    @Override
    public IStorageChannel getChannel() {
        if(itemStorage != null) {
            return itemStorage.getChannel();
        }
        return Api.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @Override
    public double extractAEPower(final double amt, final Actionable mode, final PowerMultiplier usePowerMultiplier) {
        if(wth != null && effectiveItem != null) {
            if(mode == Actionable.SIMULATE) {
                return wth.hasPower(myPlayer, amt, effectiveItem) ? amt : 0;
            }
            return wth.usePower(myPlayer, amt, effectiveItem) ? amt : 0;
        }
        return 0.0;
    }

    @Override
    public ItemStack getItemStack() {
        return effectiveItem;
    }

    @Override
    public IConfigManager getConfigManager() {
        return wth.getConfigManager(effectiveItem);
    }

    @Override
    public IGridNode getActionableNode() {
        rangeCheck();
        if(myWap != null) {
            return myWap.getActionableNode();
        }
        return null;
    }

    public boolean rangeCheck() {
        if(((IInfinityBoosterCardHolder) effectiveItem.getItem()).hasBoosterCard(effectiveItem)) return true;
        sqRange = myRange = Double.MAX_VALUE;

        if(targetGrid != null && itemStorage != null) {
            if(myWap != null) {
                if(myWap.getGrid() == targetGrid) {
                    return testWap(myWap);
                }
                return false;
            }

            final IMachineSet tw = targetGrid.getMachines(WirelessBlockEntity.class);

            myWap = null;

            for(final IGridNode n : tw) {
                final IWirelessAccessPoint wap = (IWirelessAccessPoint) n.getMachine();
                if(testWap(wap)) {
                    myWap = wap;
                }
            }

            return myWap != null;
        }
        return false;
    }

    private boolean testWap(final IWirelessAccessPoint wap) {
        double rangeLimit = wap.getRange();
        rangeLimit *= rangeLimit;

        final DimensionalCoord dc = wap.getLocation();

        if(dc.getWorld() == myPlayer.world) {
            final double offX = dc.x - myPlayer.getX();
            final double offY = dc.y - myPlayer.getY();
            final double offZ = dc.z - myPlayer.getZ();

            final double r = offX * offX + offY * offY + offZ * offZ;
            if(r < rangeLimit && sqRange > r) {
                if(wap.isActive()) {
                    sqRange = r;
                    myRange = Math.sqrt(r);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getInventorySlot() {
        return inventorySlot;
    }

    public boolean isCraftingRecipe() {
        return craftingMode;
    }

    public FixedItemInv getInventoryByName(final String name) {
        if(name.equals("crafting")) {
            return crafting;
        }

        if(name.equals("output")) {
            return output;
        }

        if(name.equals("pattern")) {
            return pattern;
        }

        System.out.println("need Inventory " + name);
        return null;
    }

    @Override
    public void saveChanges() {
        markForSave();
    }

    @Override
    public void onChangeInventory(FixedItemInv inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {
        if(inv == pattern && slot == 1) {
            final ItemStack is = pattern.getInvStack(1);
            final ICraftingPatternDetails details = Api.instance().crafting().decodePattern(is, myPlayer.world, false);
            if(details != null) {
                setCraftingRecipe(details.isCraftable());
                setSubstitution(details.canSubstitute());

                for(int x = 0; x < crafting.getSlotCount() && x < details.getSparseInputs().length; x++) {
                    final IAEItemStack item = details.getSparseInputs()[x];
                    crafting.forceSetInvStack(x, item == null ? ItemStack.EMPTY : item.createItemStack());
                }

                for(int x = 0; x < output.getSlotCount() && x < details.getSparseOutputs().length; x++) {
                    final IAEItemStack item = details.getSparseOutputs()[x];
                    output.forceSetInvStack(x, item == null ? ItemStack.EMPTY : item.createItemStack());
                }
            }
        } else if(inv == crafting) {
            fixCraftingRecipes();
        }

        markForSave();
    }

    public void setCraftingRecipe(final boolean craftingMode) {
        this.craftingMode = craftingMode;
        fixCraftingRecipes();
    }

    public void setSubstitution(final boolean canSubstitute) {
        this.substitute = canSubstitute;
    }

    private void fixCraftingRecipes() {
        if (craftingMode) {
            for (int x = 0; x < crafting.getSlotCount(); x++) {
                final ItemStack is = crafting.getInvStack(x);
                if (!is.isEmpty()) {
                    is.setCount(1);
                }
            }
        }
    }

    private void markForSave() {
        //TODO actually mark for safe
    }

    /*@Override
    public FixedViewCellInventory getViewCellStorage() {
        return fixedViewCellInventory;
    }*/
}