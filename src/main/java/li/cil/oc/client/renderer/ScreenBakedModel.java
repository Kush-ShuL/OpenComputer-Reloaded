package li.cil.oc.client.renderer;

import li.cil.oc.common.block.ScreenBlock;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Original smart screen shell: all six world faces and rectangular multiblock edge textures. */
public final class ScreenBakedModel extends BakedModelWrapper<BakedModel> {
    public record Geometry(int width,int height,int x,int y,Direction pitch,Direction yaw) {
        public Direction facing(){return pitch==Direction.NORTH?yaw:pitch;}
        public Direction up(){return pitch==Direction.UP?yaw.getOpposite():pitch==Direction.DOWN?yaw:Direction.UP;}
        public Direction right(){Direction up=up(),front=facing();return Direction.fromDelta(up.getStepY()*front.getStepZ()-up.getStepZ()*front.getStepY(),up.getStepZ()*front.getStepX()-up.getStepX()*front.getStepZ(),up.getStepX()*front.getStepY()-up.getStepY()*front.getStepX());}
        public Direction local(Direction side){if(side==facing())return Direction.SOUTH;if(side==facing().getOpposite())return Direction.NORTH;if(side==up())return Direction.UP;if(side==up().getOpposite())return Direction.DOWN;if(side==right())return Direction.EAST;return Direction.WEST;}
    }
    public static final ModelProperty<Geometry> GEOMETRY=new ModelProperty<>();
    private record QuadKey(Direction side,String texture,int rotation) {}
    private static final Map<QuadKey,BakedQuad> QUADS=new ConcurrentHashMap<>();
    // Upstream SmartBlockModelBase uses these vertex/UV planes, rather than rotating a fallback cube.
    private static final float[][][] CUBE={{{0,0,1},{0,0,0},{1,0,0},{1,0,1}},{{0,1,0},{0,1,1},{1,1,1},{1,1,0}},{{1,1,0},{1,0,0},{0,0,0},{0,1,0}},{{0,1,1},{0,0,1},{1,0,1},{1,1,1}},{{0,1,0},{0,0,0},{0,0,1},{0,1,1}},{{1,1,1},{1,0,1},{1,0,0},{1,1,0}}};
    private static final int[][][] PLANES={{{1,0,0},{0,0,-1}},{{1,0,0},{0,0,1}},{{-1,0,0},{0,-1,0}},{{1,0,0},{0,-1,0}},{{0,0,1},{0,-1,0}},{{0,0,-1},{0,-1,0}}};
    public ScreenBakedModel(BakedModel original){super(original);}
    public static void clearCache(){QUADS.clear();}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType renderType){
        if(side==null)return List.of();
        Geometry geometry=data.get(GEOMETRY);
        if(geometry==null)geometry=new Geometry(1,1,0,0,state==null?Direction.NORTH:state.getValue(ScreenBlock.PITCH),state==null?Direction.NORTH:state.getValue(ScreenBlock.YAW));
        int px=part(geometry.x,geometry.width-1),py=part(geometry.y,geometry.height-1),pitch=geometry.pitch==Direction.NORTH?0:1;
        Direction local=geometry.local(side);
        if((side==Direction.DOWN||geometry.facing()==Direction.DOWN)&&side!=geometry.facing()){px=2-px;py=2-py;}
        String texture;
        if(geometry.width==1&&geometry.height==1)texture=local==Direction.SOUTH?ScreenTextureTable.SingleFront[pitch]:ScreenTextureTable.Single[side.get3DDataValue()];
        else if(geometry.width==1)texture=local==Direction.SOUTH?ScreenTextureTable.VerticalFront[pitch][py]:ScreenTextureTable.Vertical[pitch][py][local.get3DDataValue()];
        else if(geometry.height==1)texture=local==Direction.SOUTH?ScreenTextureTable.HorizontalFront[pitch][px]:ScreenTextureTable.Horizontal[pitch][px][local.get3DDataValue()];
        else texture=local==Direction.SOUTH?ScreenTextureTable.MultiFront[pitch][py][px]:ScreenTextureTable.Multi[pitch][py][px][local.get3DDataValue()];
        int rotation=side==Direction.UP?geometry.yaw.get2DDataValue():side==Direction.DOWN?-geometry.yaw.get2DDataValue():0;
        return List.of(QUADS.computeIfAbsent(new QuadKey(side,texture,Math.floorMod(rotation,4)),ScreenBakedModel::bake));
    }
    private static int part(int coordinate,int maximum){return coordinate==0?2:coordinate==maximum?0:1;}
    private static BakedQuad bake(QuadKey key){
        TextureAtlasSprite sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath("opencomputers","blocks/screen/"+key.texture));
        int[] raw=new int[4*8];float[][] face=CUBE[key.side.get3DDataValue()];int[][] planes=PLANES[key.side.get3DDataValue()];
        for(int i=0;i<4;i++){
            float[] position=face[i];float u=dot(position,planes[0]),v=dot(position,planes[1]);
            if(planes[0][0]+planes[0][1]+planes[0][2]<0)u+=1;if(planes[1][0]+planes[1][1]+planes[1][2]<0)v+=1;
            for(int turn=0;turn<key.rotation;turn++){float old=u;u=v;v=1-old;}
            int offset=i*8;raw[offset]=Float.floatToRawIntBits(position[0]);raw[offset+1]=Float.floatToRawIntBits(position[1]);raw[offset+2]=Float.floatToRawIntBits(position[2]);raw[offset+3]=-1;
            raw[offset+4]=Float.floatToRawIntBits(sprite.getU0()+(sprite.getU1()-sprite.getU0())*u);raw[offset+5]=Float.floatToRawIntBits(sprite.getV0()+(sprite.getV1()-sprite.getV0())*v);
            raw[offset+7]=((key.side.getStepX()*127)&255)|(((key.side.getStepY()*127)&255)<<8)|(((key.side.getStepZ()*127)&255)<<16);
        }
        return new BakedQuad(raw,0,key.side,sprite,true);
    }
    private static float dot(float[] value,int[] axis){return value[0]*axis[0]+value[1]*axis[1]+value[2]*axis[2];}
}
