package li.cil.oc.server.component;

import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Executes the normal recipe/result-slot path, including crafting hooks and container remainders. */
public final class UpgradeCrafting extends UpgradeBase {
    private final Robot robot;
    public UpgradeCrafting(EnvironmentHost host){super(host,"crafting","Assembly controller","MultiCombinator-9S",false);robot=(Robot)host;}
    private int parentSlot(int slot){return slot/3*4+slot%3;}
    @Callback public Object[] craft(Context context,Arguments args){
        int wanted=Math.max(0,Math.min(64,args.optInteger(0,64))),crafted=0;if(robot.mainInventory().getContainerSize()<11)return new Object[]{false,0};
        var menu=new AbstractContainerMenu(null,-1){@Override public boolean stillValid(Player player){return true;}@Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}};
        var grid=new TransientCraftingContainer(menu,3,3);for(int i=0;i<9;i++)grid.setItem(i,robot.mainInventory().getItem(parentSlot(i)).copy());
        var initial=host.world().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid.asCraftInput(),host.world());
        if(initial.isEmpty())return new Object[]{false,0};
        while(crafted<wanted){var recipe=host.world().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid.asCraftInput(),host.world());if(recipe.isEmpty()||!recipe.get().id().equals(initial.get().id()))break;ItemStack result=recipe.get().value().assemble(grid.asCraftInput(),host.world().registryAccess());if(result.isEmpty())break;var output=new ResultContainer();output.setRecipeUsed(recipe.get());output.setItem(0,result);var slot=new ResultSlot(robot.player(),grid,output,0,0,0);ItemStack taken=slot.remove(result.getCount());slot.onTake(robot.player(),taken);crafted+=Math.max(1,taken.getCount());for(int i=0;i<9;i++)robot.mainInventory().setItem(parentSlot(i),grid.getItem(i));InventoryUtils.addToPlayerInventory(taken,robot.player(),true);for(int i=0;i<9;i++)grid.setItem(i,robot.mainInventory().getItem(parentSlot(i)).copy());}
        changed();return new Object[]{crafted>0,crafted};
    }
}
