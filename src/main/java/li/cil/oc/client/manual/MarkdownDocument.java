package li.cil.oc.client.manual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.manual.ImageRenderer;
import li.cil.oc.api.manual.InteractiveImageRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Layout for the Markdown subset used by the original OC manual, including addon image callbacks. */
public final class MarkdownDocument {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern WORD = Pattern.compile("\\s+|\\S+");
    private static final Style CODE = Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("uniform")).withColor(0xBFCBFF);
    private final List<Element> elements = new ArrayList<>();
    private final Map<String, Boolean> validLinks = new HashMap<>();
    private final Map<String, Integer> anchors = new HashMap<>();
    private final ManualRegistry manual;
    private final String path;
    private final Font font;
    private final int width;
    private float x, y, rowHeight, indentation;
    private final float normalHeight;

    private record Span(String text, Style style, String link, ImageRenderer image, String tooltip) {
        Span withLink(String href) { return new Span(text, style, href, image, tooltip); }
    }

    public final class Hit {
        private final Element element;
        private final int imageX, imageY;
        private Hit(Element element, int imageX, int imageY) { this.element = element; this.imageX = imageX; this.imageY = imageY; }
        public String tooltip() {
            if (element.image instanceof InteractiveImageRenderer interactive) {
                try { return interactive.getTooltip(element.tooltip); }
                catch (Throwable error) { OpenComputers.LOGGER.warn("Manual image tooltip failed", error); return null; }
            }
            return element.image != null ? element.tooltip : element.link;
        }
        /** The image gets first refusal on linked images, as specified by InteractiveImageRenderer. */
        public String click() {
            if (element.image instanceof InteractiveImageRenderer interactive) {
                try { if (interactive.onMouseClick(imageX, imageY)) return null; }
                catch (Throwable error) { OpenComputers.LOGGER.warn("Manual image click failed", error); }
            }
            return element.link;
        }
    }

    private static final class Element {
        float x, y, width, height, scale;
        Component text;
        String link, tooltip;
        ImageRenderer image;
        boolean failed;
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }

    public MarkdownDocument(ManualRegistry manual, String path, Iterable<String> lines, Font font, int width) {
        this.manual = manual; this.path = path; this.font = font; this.width = width;
        this.normalHeight = font.lineHeight + 1;
        rowHeight = normalHeight;
        boolean codeBlock = false;
        for (String line : lines) {
            if (line.stripLeading().startsWith("```")) { codeBlock = !codeBlock; continue; }
            float scale = 1;
            Style style = codeBlock ? CODE : Style.EMPTY;
            if (!codeBlock) {
                var heading = HEADING.matcher(line);
                if (heading.matches()) {
                    scale = Math.max(2, 5 - heading.group(1).length()) / 2F;
                    style = style.withUnderlined(true);
                    line = heading.group(2);
                    String anchor = line.replaceAll("[`*_~]", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} _-]", "").replace(' ', '-');
                    anchors.putIfAbsent(anchor, (int) y);
                }
            }
            int spaces = 0;
            while (spaces < line.length() && Character.isWhitespace(line.charAt(spaces))) spaces++;
            indentation = Math.min(width / 3F, font.width(" ".repeat(spaces)) * scale);
            if (!codeBlock && (line.startsWith("- ", spaces) || line.startsWith("* ", spaces)))
                indentation = Math.min(width / 3F, indentation + font.width(line.substring(spaces, spaces + 2)) * scale);
            rowHeight = normalHeight * scale;
            List<Span> spans = codeBlock ? List.of(new Span(line, style, null, null, null)) : parse(line, style);
            for (Span span : spans) {
                if (span.image != null) appendImage(span); else appendText(span, scale);
            }
            newLine(0);
        }
    }

    public int height() { return Math.max(0, (int) Math.ceil(y)); }
    public int anchorOffset(String anchor) { return anchors.getOrDefault(anchor.toLowerCase(Locale.ROOT), 0); }

    private void newLine(float indent) {
        y += Math.max(rowHeight, normalHeight);
        x = indent; rowHeight = normalHeight;
    }

    private void appendText(Span span, float scale) {
        var matcher = WORD.matcher(span.text);
        while (matcher.find()) {
            String word = matcher.group();
            float wordWidth = measure(word, span.style) * scale;
            if (!word.isBlank() && x > indentation && x + wordWidth > width) newLine(indentation);
            // Preserve explicit leading indentation but avoid carrying trailing whitespace onto a wrapped row.
            if (word.isBlank()) {
                if (x + wordWidth <= width) x += wordWidth;
                continue;
            }
            while (!word.isEmpty()) {
                int end = fittingEnd(word, span.style, (width - x) / scale);
                if (end == 0 && x > indentation) { newLine(indentation); continue; }
                if (end == 0) end = Character.charCount(word.codePointAt(0));
                String part = word.substring(0, end);
                var element = new Element();
                element.x = x; element.y = y; element.width = measure(part, span.style) * scale;
                element.height = normalHeight * scale; element.scale = scale;
                element.text = Component.literal(part).setStyle(span.style);
                element.link = span.link;
                elements.add(element);
                x += element.width; rowHeight = Math.max(rowHeight, element.height);
                word = word.substring(end);
                if (!word.isEmpty()) newLine(indentation);
            }
        }
    }

    private float measure(String value, Style style) { return font.width(Component.literal(value).setStyle(style)); }

    private int fittingEnd(String value, Style style, float available) {
        int end = 0;
        while (end < value.length()) {
            int next = end + Character.charCount(value.codePointAt(end));
            if (measure(value.substring(0, next), style) > available) break;
            end = next;
        }
        return end;
    }

    private void appendImage(Span span) {
        try {
            int naturalWidth = span.image.getWidth(), naturalHeight = span.image.getHeight();
            if (naturalWidth <= 0 || naturalHeight <= 0) return;
            if (x > 0) newLine(0);
            var element = new Element();
            element.scale = Math.min(1, (float) width / naturalWidth);
            element.width = naturalWidth * element.scale; element.height = naturalHeight * element.scale;
            element.x = (width - element.width) / 2; element.y = y + 2;
            element.image = span.image; element.link = span.link; element.tooltip = span.tooltip;
            elements.add(element);
            y += element.height + 4; x = 0; rowHeight = 0;
        } catch (Throwable error) { OpenComputers.LOGGER.warn("Manual image dimensions failed", error); }
    }

    public Hit hit(double mouseX, double mouseY) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            Element element = elements.get(i);
            if ((element.link != null || element.image != null) && element.contains(mouseX, mouseY))
                return new Hit(element, (int) Math.floor((mouseX - element.x) / element.scale), (int) Math.floor((mouseY - element.y) / element.scale));
        }
        return null;
    }

    public Hit render(GuiGraphics graphics, int left, int top, int height, int offset, double mouseX, double mouseY) {
        double localX = mouseX - left, localY = mouseY - top + offset;
        Hit hovered = mouseY >= top && mouseY < top + height && mouseX >= left && mouseX < left + width ? hit(localX, localY) : null;
        graphics.enableScissor(left, top, left + width, top + height);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(left, top - offset, 0);
            for (Element element : elements) {
                if (element.y + element.height < offset || element.y > offset + height) continue;
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(element.x, element.y, 0);
                    graphics.pose().scale(element.scale, element.scale, 1);
                    if (element.image != null) {
                        if (!element.failed) {
                            try {
                                element.image.render(graphics, (int) Math.floor((localX - element.x) / element.scale), (int) Math.floor((localY - element.y) / element.scale));
                            } catch (Throwable error) {
                                element.failed = true;
                                OpenComputers.LOGGER.warn("Manual image rendering failed", error);
                            }
                        }
                        if (element.failed) graphics.drawString(font, "?", 0, 0, 0xFF7777, false);
                    } else {
                        Component text = element.text;
                        int color = 0xDDDDDD;
                        if (element.link != null) {
                            boolean valid = validLinks.computeIfAbsent(element.link, href -> {
                                if (external(href) || href.startsWith("#")) return true;
                                int hash = href.indexOf('#');
                                return manual.contentFor(ManualRegistry.makeRelative(hash < 0 ? href : href.substring(0, hash), path)) != null;
                            });
                            color = valid ? 0x66CC66 : 0xCC6666;
                            if (hovered != null && Objects.equals(hovered.element.link, element.link)) color = valid ? 0xAAFFAA : 0xFFAAAA;
                            text = text.copy().withStyle(style -> style.withColor(colorForLink(valid, hovered != null && Objects.equals(hovered.element.link, element.link))));
                        }
                        graphics.drawString(font, text, 0, 0, color, false);
                    }
                } finally { graphics.pose().popPose(); }
            }
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
        return hovered;
    }

    private static int colorForLink(boolean valid, boolean hovered) { return valid ? (hovered ? 0xAAFFAA : 0x66CC66) : (hovered ? 0xFFAAAA : 0xCC6666); }
    public static boolean external(String href) { return href.startsWith("https://") || href.startsWith("http://"); }

    private List<Span> parse(String source, Style style) {
        var result = new ArrayList<Span>();
        var plain = new StringBuilder();
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < source.length()) { plain.append(source.charAt(i + 1)); i += 2; continue; }
            boolean image = c == '!' && i + 1 < source.length() && source.charAt(i + 1) == '[';
            if (image || c == '[') {
                int start = i + (image ? 1 : 0);
                int end = closing(source, start, '[', ']');
                if (end >= 0 && end + 1 < source.length() && source.charAt(end + 1) == '(') {
                    int urlEnd = closing(source, end + 1, '(', ')');
                    if (urlEnd >= 0) {
                        flush(result, plain, style);
                        String label = source.substring(start + 1, end);
                        String href = source.substring(end + 2, urlEnd).trim();
                        if (href.startsWith("<") && href.endsWith(">")) href = href.substring(1, href.length() - 1);
                        if (image) {
                            ImageRenderer renderer = li.cil.oc.api.Manual.imageFor(href);
                            if (renderer != null) result.add(new Span(null, style, null, renderer, label));
                            else result.add(new Span("No renderer found for: " + href, style.withColor(0xFF7777), null, null, null));
                        } else {
                            for (Span child : parse(label, style)) result.add(child.withLink(href));
                        }
                        i = urlEnd + 1; continue;
                    }
                }
            }
            String marker = source.startsWith("**", i) ? "**" : source.startsWith("__", i) ? "__" : source.startsWith("~~", i) ? "~~" : c == '*' || c == '_' || c == '`' ? String.valueOf(c) : null;
            if (marker != null) {
                int end = source.indexOf(marker, i + marker.length());
                if (end > i + marker.length()) {
                    flush(result, plain, style);
                    String inside = source.substring(i + marker.length(), end);
                    Style next = switch (marker) {
                        case "**", "__" -> style.withBold(true);
                        case "~~" -> style.withStrikethrough(true);
                        case "`" -> style.applyTo(CODE);
                        default -> style.withItalic(true);
                    };
                    if (marker.equals("`")) result.add(new Span(inside, next, null, null, null)); else result.addAll(parse(inside, next));
                    i = end + marker.length(); continue;
                }
            }
            plain.append(c); i++;
        }
        flush(result, plain, style);
        return result;
    }

    private static void flush(List<Span> result, StringBuilder plain, Style style) {
        if (!plain.isEmpty()) { result.add(new Span(plain.toString(), style, null, null, null)); plain.setLength(0); }
    }

    private static int closing(String source, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == open) depth++;
            else if (c == close && --depth == 0) return i;
        }
        return -1;
    }
}
