package li.cil.oc.server.component;

import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Executes the normal recipe/result-slot path, including crafting hooks and container remainders. */
public final class UpgradeCrafting extends UpgradeBase {
    private final Robot robot;
    public UpgradeCrafting(EnvironmentHost host){super(host,"crafting","Assembly controller","MultiCombinator-9S",false);robot=(Robot)host;((Component)node()).setVisibility(Visibility.Network);}
    private int parentSlot(int slot){return slot/3*4+slot%3;}
    @Callback(doc="function([count:number]):number -- Tries to craft the specified number of items in the top left area of the inventory.") public Object[] craft(Context context,Arguments args){
        int wanted=Math.max(0,Math.min(64,args.optInteger(0,64))),crafted=0;
        var menu=new AbstractContainerMenu(null,-1){@Override public boolean stillValid(Player player){return true;}@Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}};
        var grid=new TransientCraftingContainer(menu,3,3);for(int i=0;i<9;i++)grid.setItem(i,parentSlot(i)<robot.mainInventory().getContainerSize()?robot.mainInventory().getItem(parentSlot(i)):ItemStack.EMPTY);
        var initial=host.world().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid.asCraftInput(),host.world());
        if(initial.isEmpty())return new Object[]{false,0};
        while(crafted<wanted){var recipe=host.world().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid.asCraftInput(),host.world());if(recipe.isEmpty()||recipe.get().value()!=initial.get().value())break;ItemStack result=recipe.get().value().assemble(grid.asCraftInput(),host.world().registryAccess());if(result.isEmpty())break;var output=new ResultContainer();output.setRecipeUsed(recipe.get());output.setItem(0,result);var slot=new ResultSlot(robot.player(),grid,output,0,0,0);ItemStack taken=slot.remove(1);crafted+=Math.max(1,taken.getCount());slot.onTake(robot.player(),taken);for(int i=0;i<9;i++)if(parentSlot(i)<robot.mainInventory().getContainerSize())robot.mainInventory().setItem(parentSlot(i),grid.getItem(i));InventoryUtils.addToPlayerInventory(taken,robot.player(),true);for(int i=0;i<9;i++)grid.setItem(i,parentSlot(i)<robot.mainInventory().getContainerSize()?robot.mainInventory().getItem(parentSlot(i)):ItemStack.EMPTY);}
        changed();return new Object[]{crafted>0,crafted};
    }
}
