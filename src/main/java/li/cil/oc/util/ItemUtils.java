package li.cil.oc.util;

import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Items;
import li.cil.oc.common.Tier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.io.*;
import java.util.*;

/** Original stack helpers, using registry-aware 1.21 stack serialization. */
public final class ItemUtils {
    private ItemUtils() {}
    public static Optional<String> getDisplayName(CompoundTag tag) {
        CompoundTag display=tag.getCompound("display");
        return display.contains("Name",Tag.TAG_STRING)?Optional.of(display.getString("Name")):Optional.empty();
    }
    public static void setDisplayName(CompoundTag tag,String name) {
        CompoundTag display=tag.getCompound("display");display.putString("Name",name);tag.put("display",display);
    }
    public static CompoundTag customData(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(); }
    public static void saveCustomData(ItemStack stack,CompoundTag data) { stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data)); }
    public static String caseNameWithTierSuffix(String name,int tier){return name+(tier==Tier.Four?"creative":Integer.toString(tier+1));}
    public static int caseTier(ItemStack stack) {
        var info=Items.get(stack);if(info==null)return Tier.None;String name=info.name();
        for(String prefix:List.of("case","microcontrollercase","dronecase","server","tabletcase")){
            if(name.equals(prefix+"creative"))return Tier.Four;
            for(int tier=0;tier<3;tier++)if(name.equals(prefix+(tier+1)))return tier;
        }
        return Tier.None;
    }
    public static CompoundTag loadTag(byte[] bytes)throws IOException{
        try(var stream=new ByteArrayInputStream(bytes)){return NbtIo.readCompressed(stream,NbtAccounter.create(16L*1024*1024));}
    }
    public static byte[] saveTag(CompoundTag tag)throws IOException{
        var output=new ByteArrayOutputStream();NbtIo.writeCompressed(tag,output);return output.toByteArray();
    }
    public static byte[] saveStack(ItemStack stack,HolderLookup.Provider registries)throws IOException{return saveTag((CompoundTag) stack.saveOptional(registries));}
    public static ItemStack[] getIngredients(ItemStack stack,Level level){
        List<ItemStack> selected=null;int outputCount=0;Random random=new Random();
        for(RecipeHolder<?> holder:level.getRecipeManager().getRecipes()){
            if(!(holder.value() instanceof CraftingRecipe recipe))continue;
            ItemStack output=recipe.getResultItem(level.registryAccess());
            if(output.isEmpty()||!ItemStack.isSameItem(output,stack))continue;
            int count=output.getCount();List<ItemStack> inputs=new ArrayList<>();boolean blacklisted=false;
            for(Ingredient ingredient:recipe.getIngredients()){
                ItemStack[] options=ingredient.getItems();if(options.length==0)continue;
                ItemStack choice=options[random.nextInt(options.length)];
                if(choice.isEmpty()||choice.getCount()/count==0||choice.getItem() instanceof BucketItem)continue;
                if(isBlacklisted(choice)){blacklisted=true;break;}inputs.add(choice.copy());
            }
            if(!blacklisted){selected=inputs;outputCount=count;break;}
        }
        if(selected==null||outputCount==0)return new ItemStack[0];
        for(ItemStack input:selected)if(ItemStack.isSameItem(input,stack))return new ItemStack[0];
        List<ItemStack> merged=new ArrayList<>();
        for(ItemStack input:selected){ItemStack same=merged.stream().filter(s->ItemStack.isSameItem(s,input)).findFirst().orElse(null);if(same==null)merged.add(input.copy());else same.grow(input.getCount());}
        List<ItemStack> result=new ArrayList<>();
        for(ItemStack input:merged){int count=Math.max(1,input.getCount()/outputCount);input.setCount(1);for(int i=0;i<count;i++)result.add(input.copy());}
        return result.toArray(ItemStack[]::new);
    }
    private static boolean isBlacklisted(ItemStack stack){
        var config=Settings.get().config;String path="disassembler.inputBlacklist";
        if(!config.hasPath(path))return false;
        var id=stack.getItem() instanceof BlockItem block?BuiltInRegistries.BLOCK.getKey(block.getBlock()):BuiltInRegistries.ITEM.getKey(stack.getItem());
        return config.getStringList(path).contains(id.toString());
    }
}
