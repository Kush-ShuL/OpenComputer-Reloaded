package li.cil.oc.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import li.cil.oc.OpenComputers;
import li.cil.oc.client.manual.ManualRegistry;
import li.cil.oc.client.manual.MarkdownDocument;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

/** The original 256x192 manual window with provider-backed interactive Markdown pages. */
public final class ManualScreen extends Screen {
    private static final int BOOK_WIDTH = 256, BOOK_HEIGHT = 192;
    private static final int PAGE_WIDTH = 230, PAGE_HEIGHT = 176;
    private static final int SCROLL_X = 244, SCROLL_Y = 6, SCROLL_HEIGHT = 180, THUMB_HEIGHT = 13;
    private static final int TAB_X = -23, TAB_Y = 7, TAB_WIDTH = 23, TAB_HEIGHT = 26, TAB_COUNT = 7;
    private final ManualRegistry manual;
    private final Set<ManualRegistry.Tab> failedTabs = new HashSet<>();
    private MarkdownDocument document;
    private int left, top, firstTab;
    private boolean dragging;

    public ManualScreen(ManualRegistry manual) {
        super(Component.translatableWithFallback("gui.opencomputers.manual", "OpenComputers Manual"));
        this.manual = manual;
    }

    public ManualRegistry registry() { return manual; }

    @Override protected void init() {
        left = (width - BOOK_WIDTH) / 2; top = (height - BOOK_HEIGHT) / 2;
        refreshPage();
    }

    public void refreshPage() {
        if (font == null) return;
        dragging = false;
        String path = manual.current().path();
        int hash = path.indexOf('#');
        String page = hash < 0 ? path : path.substring(0, hash);
        var content = manual.contentFor(page);
        if (content == null) content = List.of("Document not found: " + page);
        document = new MarkdownDocument(manual, page, content, font, PAGE_WIDTH);
        scrollTo(manual.current().offset());
        if (hash >= 0 && manual.current().offset() == 0) scrollTo(document.anchorOffset(path.substring(hash + 1)));
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        graphics.blit(ManualRegistry.texture("manual"), left, top, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, BOOK_WIDTH, BOOK_HEIGHT);
        List<ManualRegistry.Tab> tabs = manual.tabs();
        firstTab = Math.clamp(firstTab, 0, Math.max(0, tabs.size() - TAB_COUNT));
        String tabTooltip = null;
        for (int row = 0; row < TAB_COUNT && row + firstTab < tabs.size(); row++) {
            var tab = tabs.get(row + firstTab);
            int x = left + TAB_X, y = top + TAB_Y + row * (TAB_HEIGHT - 1);
            boolean hovered = inside(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);
            graphics.blit(ManualRegistry.texture("manual_tab"), x, y, 0, hovered ? TAB_HEIGHT : 0, TAB_WIDTH, TAB_HEIGHT, TAB_WIDTH, TAB_HEIGHT * 2);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x + 5, y + 5, 0);
                if (!failedTabs.contains(tab)) {
                    try { tab.renderer().render(graphics); }
                    catch (Throwable error) { failedTabs.add(tab); OpenComputers.LOGGER.warn("Manual tab renderer failed", error); }
                }
            } finally { graphics.pose().popPose(); }
            if (hovered) {
                tabTooltip = tab.tooltip();
                if (tabs.size() > TAB_COUNT) {
                    String hint = Component.translatableWithFallback("gui.opencomputers.manual.more_tabs", "Scroll to see more tabs").getString();
                    tabTooltip = (tabTooltip == null ? "" : localize(tabTooltip) + "\n") + hint;
                }
            }
        }

        int thumbY = top + SCROLL_Y + (maxOffset() == 0 ? 0 : (SCROLL_HEIGHT - THUMB_HEIGHT) * offset() / maxOffset());
        boolean scrollHovered = inside(mouseX, mouseY, left + SCROLL_X, thumbY, 6, THUMB_HEIGHT);
        graphics.blit(ManualRegistry.texture("button_scroll"), left + SCROLL_X, thumbY, 0,
            maxOffset() > 0 && (dragging || scrollHovered) ? THUMB_HEIGHT : 0, 6, THUMB_HEIGHT, 6, THUMB_HEIGHT * 2);

        MarkdownDocument.Hit hit = document == null ? null : document.render(graphics, left + 8, top + 8, PAGE_HEIGHT, offset(), mouseX, mouseY);
        if (!dragging) {
            if (tabTooltip != null) tooltip(graphics, tabTooltip, mouseX, mouseY);
            else if (hit != null) tooltip(graphics, hit.tooltip(), mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String localize(String tooltip) { return I18n.exists(tooltip) ? I18n.get(tooltip) : tooltip; }

    private void tooltip(GuiGraphics graphics, String text, int mouseX, int mouseY) {
        if (text == null || text.isEmpty()) return;
        var lines = new ArrayList<Component>();
        for (String line : localize(text).replace("[nl]", "\n").split("\n")) lines.add(Component.literal(line));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) { goBack(); return true; }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (maxOffset() > 0 && overScroll(mouseX, mouseY)) { dragging = true; scrollFromMouse(mouseY); return true; }
        var tabs = manual.tabs();
        for (int row = 0; row < TAB_COUNT && firstTab + row < tabs.size(); row++) {
            if (inside(mouseX, mouseY, left + TAB_X, top + TAB_Y + row * (TAB_HEIGHT - 1), TAB_WIDTH, TAB_HEIGHT)) {
                li.cil.oc.api.Manual.navigate(tabs.get(firstTab + row).path());
                return true;
            }
        }
        if (document != null && inside(mouseX, mouseY, left + 8, top + 8, PAGE_WIDTH, PAGE_HEIGHT)) {
            var hit = document.hit(mouseX - left - 8, mouseY - top - 8 + offset());
            if (hit != null) { follow(hit.click()); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void follow(String href) {
        if (href == null || href.isEmpty()) return;
        if (MarkdownDocument.external(href)) {
            handleComponentClicked(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, href)));
        } else if (href.startsWith("#")) {
            scrollTo(document.anchorOffset(href.substring(1)));
        } else {
            String current = manual.current().path();
            int hash = current.indexOf('#');
            li.cil.oc.api.Manual.navigate(ManualRegistry.makeRelative(href, hash < 0 ? current : current.substring(0, hash)));
        }
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) { scrollFromMouse(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, left + TAB_X, top, TAB_WIDTH, BOOK_HEIGHT) && manual.tabs().size() > TAB_COUNT) {
            firstTab = Math.clamp(firstTab - (int) Math.signum(scrollY), 0, manual.tabs().size() - TAB_COUNT);
        } else scrollTo(offset() - (int) (scrollY * (font.lineHeight + 1) * 3));
        return true;
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) { onClose(); return true; }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE || minecraft.options.keyJump.matches(keyCode, scanCode)) { goBack(); return true; }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { scrollTo(offset() + PAGE_HEIGHT - font.lineHeight); return true; }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) { scrollTo(offset() - PAGE_HEIGHT + font.lineHeight); return true; }
        if (keyCode == GLFW.GLFW_KEY_HOME) { scrollTo(0); return true; }
        if (keyCode == GLFW.GLFW_KEY_END) { scrollTo(maxOffset()); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void goBack() { if (!manual.back()) onClose(); }
    private int offset() { return manual.current().offset(); }
    private int maxOffset() { return document == null ? 0 : Math.max(0, document.height() - PAGE_HEIGHT); }
    private void scrollTo(int position) { manual.current().offset(Math.clamp(position, 0, maxOffset())); }
    private void scrollFromMouse(double mouseY) {
        scrollTo((int) Math.round((mouseY - top - SCROLL_Y - THUMB_HEIGHT / 2D) * maxOffset() / (SCROLL_HEIGHT - THUMB_HEIGHT)));
    }
    private boolean overScroll(double mouseX, double mouseY) { return inside(mouseX, mouseY, left + SCROLL_X, top + SCROLL_Y, 6, SCROLL_HEIGHT); }
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
