package li.cil.oc.common.init;

import java.util.List;
import java.util.function.Supplier;
import li.cil.oc.common.block.RaidBlock;
import li.cil.oc.common.block.entity.RaidBlockEntity;
import li.cil.oc.common.item.data.RaidData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RaidDevices {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"opencomputers");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"opencomputers");
    public static final Supplier<RaidBlock> BLOCK=BLOCKS.register("raid",()->new RaidBlock(BlockBehaviour.Properties.of().strength(2,5).sound(SoundType.METAL)));
    public static final Supplier<BlockEntityType<RaidBlockEntity>> TYPE=TYPES.register("raid",()->BlockEntityType.Builder.of((pos,state)->new RaidBlockEntity(RaidDevices.TYPE.get(),pos,state),BLOCK.get()).build(null));
    public static final Supplier<BlockItem> ITEM=ITEMS.register("raid",()->new BlockItem(BLOCK.get(),new Item.Properties()){
        @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);for(String line:Component.translatable("oc:tooltip.raid").getString().split("\\[nl\\]"))tooltip.add(Component.literal(line));if(flag.isAdvanced()&&context.registries()!=null)for(ItemStack disk:new RaidData(stack,context.registries()).disks)if(!disk.isEmpty())tooltip.add(Component.literal("- ").append(disk.getHoverName()));}
        @Override public int getMaxStackSize(ItemStack stack){var tag=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);return tag!=null&&!tag.copyTag().getList("oc:disks",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()?1:super.getMaxStackSize(stack);}
    });
    public static final List<Supplier<? extends Block>> ALL_BLOCKS=List.of(BLOCK);
    public static final List<Supplier<? extends Item>> ALL_ITEMS=List.of(ITEM);
    static{Items.INSTANCE.registerBlock("raid",BLOCK,ITEM);}
    private RaidDevices(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);li.cil.oc.common.menu.RaidMenu.register(bus);bus.addListener(li.cil.oc.common.network.RaidNetworking::register);bus.addListener(RaidDevices::capabilities);if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)li.cil.oc.client.RaidClient.register(bus);}
    private static void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event){event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,TYPE.get(),(raid,side)->new net.neoforged.neoforge.items.wrapper.InvWrapper(raid));}
}
