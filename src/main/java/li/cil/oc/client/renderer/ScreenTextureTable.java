package li.cil.oc.client.renderer;

/** Exact texture selection tables from upstream client/Textures.scala. */
final class ScreenTextureTable {
    private ScreenTextureTable() {}
    static final String[] Single={"b","b","b2","b2","b2","b2"};
    static final String[] SingleFront={"f","f2"};
    static final String[][][] Horizontal={{{"bht","bhb","bht2","bht2","b2","b2"},{"bhm","bhm","bhm2","bhm2","b","b"},{"bhb","bht","bhb2","bhb2","b2","b2"}},{{"bhb2","bht2","bht","bhb","b2","b2"},{"bhm2","bhm2","bhm","bhm","b","b"},{"bht2","bhb2","bhb","bht","b2","b2"}}};
    static final String[][] HorizontalFront={{"fhb2","fhm2","fht2"},{"fhb","fhm","fht"}};
    static final String[][][] Vertical={{{"b","b","bvt","bvt","bvt","bvt"},{"b","b","bvm","bvm","bvm","bvm"},{"b","b","bvb2","bvb2","bvb2","bvb2"}},{{"b2","b2","bvt","bvt","bht2","bhb2"},{"b","b","bvm","bvm","bhm2","bhm2"},{"b2","b2","bvb","bvb","bhb2","bht2"}}};
    static final String[][] VerticalFront={{"fvt","fvm","fvb2"},{"fvt","fvm","fvb"}};
    static final String[][][][] Multi={{{{"bht","bhb","btl","btr","bvb","bvt"},{"bhm","bhm","btm","btm","b","b"},{"bhb","bht","btr","btl","bvt","bvb"}},{{"b","b","bml","bmr","bvm","bvm"},{"b","b","bmm","bmm","b","b"},{"b","b","bmr","bml","bvm","bvt"}},{{"bht","bhb","bbl2","bbr2","bvt","bvb2"},{"bhm","bhm","bbm2","bbm2","b","b"},{"bhb","bht","bbr2","bbl2","bvb2","bvt"}}},{{{"bhb2","bht2","btl","btr","bht2","bhb2"},{"bhm2","bhm2","btm","btm","b","b"},{"bht2","bhb2","btr","btl","bht2","bhb2"}},{{"b","b","bml","bml","bhm2","bhm2"},{"b","b","bmm","bmm","b","b"},{"b","b","bmr","bmr","bhm2","bhm2"}},{{"bhb2","bht2","bbl","bbr","bhb2","bht2"},{"bhm2","bhm2","bbm","bbm","b","b"},{"bht2","bhb2","bbr","bbl","bhb2","bht2"}}}};
    static final String[][][] MultiFront={{{"ftr","ftm","ftl"},{"fmr","fmm","fml"},{"fbr2","fbm2","fbl2"}},{{"ftr","ftm","ftl"},{"fmr","fmm","fml"},{"fbr","fbm","fbl"}}};
}
