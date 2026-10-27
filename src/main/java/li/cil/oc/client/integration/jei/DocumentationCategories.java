package li.cil.oc.client.integration.jei;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Items;
import li.cil.oc.api.Manual;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** Manual usage and real EnvironmentProvider callback pages from the original JEI integration. */
final class DocumentationCategories {
    static final RecipeType<ManualPage> MANUAL = RecipeType.create("opencomputers", "manual", ManualPage.class);
    static final RecipeType<CallbackPage> CALLBACKS = RecipeType.create("opencomputers", "api", CallbackPage.class);
    private static final Pattern DOC = Pattern.compile("(?s)^function(\\(.*?\\).*?)(?: -- |; )(.*)$");
    private static final int WIDTH = 160, HEIGHT = 125, LINES = 12;

    record ManualPage(ItemStack stack, String path) {}
    record CallbackPage(ItemStack stack, List<FormattedCharSequence> lines) {}

    private DocumentationCategories() {}

    static List<ManualPage> manuals(Collection<ItemStack> ingredients) {
        List<ManualPage> result = new ArrayList<>();
        for (ItemStack stack : ingredients) {
            String path = Manual.pathFor(stack);
            if (path != null) result.add(new ManualPage(stack.copy(), path));
        }
        return result;
    }

    static List<CallbackPage> callbacks(Collection<ItemStack> ingredients) {
        List<CallbackPage> result = new ArrayList<>();
        // Cache class analysis, but retain every item subtype and every provider for that item.
        Map<Class<?>, List<String>> cache = new LinkedHashMap<>();
        var font = Minecraft.getInstance().font;
        for (ItemStack stack : ingredients) {
            var environments = Driver.environmentsFor(stack);
            if (environments == null || environments.isEmpty()) continue;
            List<String> docs = new ArrayList<>();
            for (Class<?> type : environments) if (type != null) docs.addAll(cache.computeIfAbsent(type, DocumentationCategories::documentation));
            docs.sort(String::compareTo);
            List<FormattedCharSequence> page = new ArrayList<>();
            for (String doc : docs) {
                var matcher = DOC.matcher(doc.substring(doc.indexOf('\n') + 1));
                String name = doc.substring(0, doc.indexOf('\n'));
                String raw = doc.substring(doc.indexOf('\n') + 1);
                List<FormattedCharSequence> block = new ArrayList<>();
                if (raw.isEmpty()) block.addAll(font.split(Component.literal(name).withStyle(ChatFormatting.BLACK), WIDTH - 8));
                else {
                    String signature = matcher.matches() ? name + matcher.group(1) : name;
                    String body = matcher.matches() ? matcher.group(2) : raw;
                    block.addAll(font.split(Component.literal(signature).withStyle(ChatFormatting.BLACK), WIDTH - 8));
                    block.addAll(font.split(Component.literal("  " + body).withStyle(ChatFormatting.DARK_GRAY), WIDTH - 8));
                }
                if (!page.isEmpty() && page.size() + 1 + block.size() > LINES) {
                    result.add(new CallbackPage(stack.copy(), List.copyOf(page))); page.clear();
                }
                if (!page.isEmpty()) page.add(FormattedCharSequence.EMPTY);
                for (var line : block) {
                    if (page.size() == LINES) { result.add(new CallbackPage(stack.copy(), List.copyOf(page))); page.clear(); }
                    page.add(line);
                }
            }
            if (!page.isEmpty()) result.add(new CallbackPage(stack.copy(), List.copyOf(page)));
        }
        return result;
    }

    private static List<String> documentation(Class<?> type) {
        Map<String, Callback> callbacks = new TreeMap<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Callback callback = method.getAnnotation(Callback.class);
                if (callback == null || !Modifier.isPublic(method.getModifiers()) || method.getReturnType() != Object[].class
                        || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{Context.class, Arguments.class})) continue;
                String name = callback.value().isBlank() ? method.getName() : callback.value();
                callbacks.putIfAbsent(name, callback);
            }
        }
        return callbacks.entrySet().stream().map(entry -> entry.getKey() + "\n" + entry.getValue().doc()).toList();
    }

    private abstract static class Category<T> implements IRecipeCategory<T> {
        private final RecipeType<T> type;
        private final Component title;
        private final IDrawable icon;
        Category(RecipeType<T> type, String title, IDrawable icon) { this.type = type; this.title = Component.translatable(title); this.icon = icon; }
        @Override public RecipeType<T> getRecipeType() { return type; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return WIDTH; }
        @Override public int getHeight() { return HEIGHT; }
    }

    static final class ManualCategory extends Category<ManualPage> {
        ManualCategory(IGuiHelper gui) { super(MANUAL, "jei.opencomputers.manual", gui.createDrawableItemStack(Items.get("manual").createItemStack(1))); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, ManualPage recipe, IFocusGroup focuses) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(recipe.stack());
        }
        @Override public void draw(ManualPage recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            boolean hover = mouseX >= 30 && mouseX < 130 && mouseY >= 10 && mouseY < 30;
            graphics.fill(30, 10, 130, 30, hover ? 0xFF909090 : 0xFF686868);
            graphics.fill(31, 11, 129, 29, hover ? 0xFFB0B0B0 : 0xFF8B8B8B);
            graphics.drawCenteredString(Minecraft.getInstance().font, Component.translatable("nei.usage.oc.Manual"), 80, 16, 0xFFFFFF);
        }
        @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, ManualPage recipe, IFocusGroup focuses) {
            builder.addGuiEventListener(new IJeiGuiEventListener() {
                @Override public ScreenRectangle getArea() { return new ScreenRectangle(30, 10, 100, 20); }
                @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (button != 0 || minecraft.player == null) return false;
                    minecraft.player.closeContainer();
                    Manual.openFor(minecraft.player);
                    Manual.navigate(recipe.path());
                    return true;
                }
            });
        }
    }

    static final class CallbackCategory extends Category<CallbackPage> {
        CallbackCategory(IGuiHelper gui) { super(CALLBACKS, "jei.opencomputers.api", new AnimatedTabletIcon()); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, CallbackPage recipe, IFocusGroup focuses) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(recipe.stack());
        }
        @Override public void draw(CallbackPage recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            for (int index = 0; index < recipe.lines().size(); index++) graphics.drawString(font, recipe.lines().get(index), 4, 4 + index * (font.lineHeight + 1), 0x333333, false);
        }
    }

    private static final class AnimatedTabletIcon implements IDrawable {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/items/tablet_on.png");
        @Override public int getWidth() { return 16; }
        @Override public int getHeight() { return 16; }
        @Override public void draw(GuiGraphics graphics, int x, int y) {
            int frame = (int) (net.minecraft.Util.getMillis() / 1000 % 2);
            graphics.blit(TEXTURE, x, y, 0, frame * 16, 16, 16, 16, 32);
        }
    }
}
