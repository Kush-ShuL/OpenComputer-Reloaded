package li.cil.oc.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Original solid dyed chamelium, with color stored in modern block state data. */
public final class ChameliumBlock extends Block {
    public static final EnumProperty<DyeColor> COLOR=EnumProperty.create("color",DyeColor.class);
    private static final int[] RGB={0xf0f0f0,0xeb8844,0xc354cd,0x6689d3,0xffff66,0x66ff66,0xd88198,0x666666,0xababab,0x66ffff,0x7b2fbe,0x6666ff,0x51301a,0x339911,0xb3312c,0x444444};
    public ChameliumBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(COLOR,DyeColor.BLACK));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(COLOR);}
    public static int color(DyeColor dye){return RGB[dye.getId()];}
    @EventBusSubscriber(modid="opencomputers",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class ClientColors {
        @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block event){
            var info=li.cil.oc.common.init.Items.INSTANCE.get("chameliumblock");
            if(info!=null)event.register((state,world,pos,index)->color(state.getValue(COLOR)),info.block());
        }
        @SubscribeEvent public static void items(RegisterColorHandlersEvent.Item event){
            var info=li.cil.oc.common.init.Items.INSTANCE.get("chameliumblock");
            if(info!=null)event.register((stack,index)->{
                String name=stack.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).properties().get("color");
                return color(DyeColor.byName(name,DyeColor.BLACK));
            },info.block().asItem());
        }
    }
}
