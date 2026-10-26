package li.cil.oc.common.init;

import li.cil.oc.api.API;
import li.cil.oc.common.block.*;
import li.cil.oc.common.block.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;
import java.util.function.Supplier;

/** Registrations backed by migrated block implementations. */
public final class Blocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"opencomputers");
    private static final DeferredRegister<Item> BLOCK_ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"opencomputers");
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"opencomputers");
    public static final List<Supplier<CaseBlock>> CASES=new ArrayList<>();
    public static final List<Supplier<BlockEntityType<CaseBlockEntity>>> CASE_TYPES=new ArrayList<>();
    public static final List<Supplier<ScreenBlock>> SCREENS=new ArrayList<>();
    public static final List<Supplier<BlockEntityType<ScreenBlockEntity>>> SCREEN_TYPES=new ArrayList<>();
    private static final List<Supplier<? extends Item>> DISPLAY_ITEMS=new ArrayList<>();
    public static final Supplier<CableBlock> CABLE;
    public static final Supplier<BlockEntityType<CableBlockEntity>> CABLE_TYPE;
    public static final Supplier<KeyboardBlock> KEYBOARD;
    public static final Supplier<BlockEntityType<KeyboardBlockEntity>> KEYBOARD_TYPE;
    public static final Supplier<CreativeModeTab> TAB;
    private Blocks(){}
    private static BlockBehaviour.Properties metal(){return BlockBehaviour.Properties.of().strength(2,5).sound(SoundType.METAL).noOcclusion();}
    static {
        for(int i=0;i<4;i++){
            int tier=i;String id="case"+(tier==3?"creative":tier+1);
            Supplier<CaseBlock> block=BLOCKS.register(id,()->new CaseBlock(tier,metal(),()->CASE_TYPES.get(tier).get()));CASES.add(block);
            CASE_TYPES.add(TYPES.register(id,()->BlockEntityType.Builder.of((pos,state)->new CaseBlockEntity(CASE_TYPES.get(tier).get(),pos,state,tier),block.get()).build(null)));
            var item=BLOCK_ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties().rarity(tier==0?Rarity.COMMON:tier==1?Rarity.UNCOMMON:tier==2?Rarity.RARE:Rarity.EPIC)));
            DISPLAY_ITEMS.add(item);Items.INSTANCE.registerBlock(id,block,item);
        }
        for(int i=0;i<3;i++){
            int tier=i;String id="screen"+(tier+1);
            Supplier<ScreenBlock> block=BLOCKS.register(id,()->new ScreenBlock(tier,metal(),()->SCREEN_TYPES.get(tier).get()));SCREENS.add(block);
            SCREEN_TYPES.add(TYPES.register(id,()->BlockEntityType.Builder.of((pos,state)->new ScreenBlockEntity(SCREEN_TYPES.get(tier).get(),pos,state,tier),block.get()).build(null)));
            var item=BLOCK_ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties().rarity(tier==0?Rarity.COMMON:tier==1?Rarity.UNCOMMON:Rarity.RARE)));
            DISPLAY_ITEMS.add(item);Items.INSTANCE.registerBlock(id,block,item);
        }
        CABLE=BLOCKS.register("cable",()->new CableBlock(metal(),()->Blocks.CABLE_TYPE.get()));
        CABLE_TYPE=TYPES.register("cable",()->BlockEntityType.Builder.of((pos,state)->new CableBlockEntity(Blocks.CABLE_TYPE.get(),pos,state),CABLE.get()).build(null));
        var cableItem=BLOCK_ITEMS.register("cable",()->new BlockItem(CABLE.get(),new Item.Properties()));DISPLAY_ITEMS.add(cableItem);Items.INSTANCE.registerBlock("cable",CABLE,cableItem);
        KEYBOARD=BLOCKS.register("keyboard",()->new KeyboardBlock(metal(),()->Blocks.KEYBOARD_TYPE.get()));
        KEYBOARD_TYPE=TYPES.register("keyboard",()->BlockEntityType.Builder.of((pos,state)->new KeyboardBlockEntity(Blocks.KEYBOARD_TYPE.get(),pos,state),KEYBOARD.get()).build(null));
        var keyboardItem=BLOCK_ITEMS.register("keyboard",()->new BlockItem(KEYBOARD.get(),new Item.Properties()));DISPLAY_ITEMS.add(keyboardItem);Items.INSTANCE.registerBlock("keyboard",KEYBOARD,keyboardItem);
        TAB=TABS.register("main",()->CreativeModeTab.builder().title(Component.literal("OpenComputer-Reloaded")).icon(()->new ItemStack(CASES.get(0).get()))
            .displayItems((parameters,output)->{
                Set<Item> shownBlocks=Collections.newSetFromMap(new IdentityHashMap<>());
                for(var item:DISPLAY_ITEMS){output.accept(item.get());shownBlocks.add(item.get());}
                for(var stack:Items.INSTANCE.registeredItems(parameters.holders()))if(!stack.isEmpty()&&(!(stack.getItem() instanceof BlockItem)||shownBlocks.add(stack.getItem())))output.accept(stack);
            }).build());
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);BLOCK_ITEMS.register(bus);TYPES.register(bus);TABS.register(bus);}
    public static void initialize(){li.cil.oc.api.CreativeTab.instance=TAB.get();}
}
