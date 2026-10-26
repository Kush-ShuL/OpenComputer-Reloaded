package li.cil.oc.client.gui;

import org.lwjgl.glfw.GLFW;

/** Preserve the LWJGL2 keyboard codes exposed by OpenOS keyboard.codes. */
final class LegacyKeyCodes {
    private LegacyKeyCodes() {}
    static int of(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> 1; case GLFW.GLFW_KEY_1 -> 2; case GLFW.GLFW_KEY_2 -> 3; case GLFW.GLFW_KEY_3 -> 4;
            case GLFW.GLFW_KEY_4 -> 5; case GLFW.GLFW_KEY_5 -> 6; case GLFW.GLFW_KEY_6 -> 7; case GLFW.GLFW_KEY_7 -> 8;
            case GLFW.GLFW_KEY_8 -> 9; case GLFW.GLFW_KEY_9 -> 10; case GLFW.GLFW_KEY_0 -> 11; case GLFW.GLFW_KEY_MINUS -> 12;
            case GLFW.GLFW_KEY_EQUAL -> 13; case GLFW.GLFW_KEY_BACKSPACE -> 14; case GLFW.GLFW_KEY_TAB -> 15;
            case GLFW.GLFW_KEY_Q -> 16; case GLFW.GLFW_KEY_W -> 17; case GLFW.GLFW_KEY_E -> 18; case GLFW.GLFW_KEY_R -> 19;
            case GLFW.GLFW_KEY_T -> 20; case GLFW.GLFW_KEY_Y -> 21; case GLFW.GLFW_KEY_U -> 22; case GLFW.GLFW_KEY_I -> 23;
            case GLFW.GLFW_KEY_O -> 24; case GLFW.GLFW_KEY_P -> 25; case GLFW.GLFW_KEY_LEFT_BRACKET -> 26; case GLFW.GLFW_KEY_RIGHT_BRACKET -> 27;
            case GLFW.GLFW_KEY_ENTER -> 28; case GLFW.GLFW_KEY_LEFT_CONTROL -> 29; case GLFW.GLFW_KEY_A -> 30; case GLFW.GLFW_KEY_S -> 31;
            case GLFW.GLFW_KEY_D -> 32; case GLFW.GLFW_KEY_F -> 33; case GLFW.GLFW_KEY_G -> 34; case GLFW.GLFW_KEY_H -> 35;
            case GLFW.GLFW_KEY_J -> 36; case GLFW.GLFW_KEY_K -> 37; case GLFW.GLFW_KEY_L -> 38; case GLFW.GLFW_KEY_SEMICOLON -> 39;
            case GLFW.GLFW_KEY_APOSTROPHE -> 40; case GLFW.GLFW_KEY_GRAVE_ACCENT -> 41; case GLFW.GLFW_KEY_LEFT_SHIFT -> 42; case GLFW.GLFW_KEY_BACKSLASH -> 43;
            case GLFW.GLFW_KEY_Z -> 44; case GLFW.GLFW_KEY_X -> 45; case GLFW.GLFW_KEY_C -> 46; case GLFW.GLFW_KEY_V -> 47;
            case GLFW.GLFW_KEY_B -> 48; case GLFW.GLFW_KEY_N -> 49; case GLFW.GLFW_KEY_M -> 50; case GLFW.GLFW_KEY_COMMA -> 51;
            case GLFW.GLFW_KEY_PERIOD -> 52; case GLFW.GLFW_KEY_SLASH -> 53; case GLFW.GLFW_KEY_RIGHT_SHIFT -> 54; case GLFW.GLFW_KEY_KP_MULTIPLY -> 55;
            case GLFW.GLFW_KEY_LEFT_ALT -> 56; case GLFW.GLFW_KEY_SPACE -> 57; case GLFW.GLFW_KEY_CAPS_LOCK -> 58;
            case GLFW.GLFW_KEY_F1 -> 59; case GLFW.GLFW_KEY_F2 -> 60; case GLFW.GLFW_KEY_F3 -> 61; case GLFW.GLFW_KEY_F4 -> 62;
            case GLFW.GLFW_KEY_F5 -> 63; case GLFW.GLFW_KEY_F6 -> 64; case GLFW.GLFW_KEY_F7 -> 65; case GLFW.GLFW_KEY_F8 -> 66;
            case GLFW.GLFW_KEY_F9 -> 67; case GLFW.GLFW_KEY_F10 -> 68; case GLFW.GLFW_KEY_NUM_LOCK -> 69; case GLFW.GLFW_KEY_SCROLL_LOCK -> 70;
            case GLFW.GLFW_KEY_KP_7 -> 71; case GLFW.GLFW_KEY_KP_8 -> 72; case GLFW.GLFW_KEY_KP_9 -> 73; case GLFW.GLFW_KEY_KP_SUBTRACT -> 74;
            case GLFW.GLFW_KEY_KP_4 -> 75; case GLFW.GLFW_KEY_KP_5 -> 76; case GLFW.GLFW_KEY_KP_6 -> 77; case GLFW.GLFW_KEY_KP_ADD -> 78;
            case GLFW.GLFW_KEY_KP_1 -> 79; case GLFW.GLFW_KEY_KP_2 -> 80; case GLFW.GLFW_KEY_KP_3 -> 81; case GLFW.GLFW_KEY_KP_0 -> 82;
            case GLFW.GLFW_KEY_KP_DECIMAL -> 83; case GLFW.GLFW_KEY_F11 -> 87; case GLFW.GLFW_KEY_F12 -> 88; case GLFW.GLFW_KEY_KP_ENTER -> 156;
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> 157; case GLFW.GLFW_KEY_KP_DIVIDE -> 181; case GLFW.GLFW_KEY_PRINT_SCREEN -> 183; case GLFW.GLFW_KEY_RIGHT_ALT -> 184;
            case GLFW.GLFW_KEY_PAUSE -> 197; case GLFW.GLFW_KEY_HOME -> 199; case GLFW.GLFW_KEY_UP -> 200; case GLFW.GLFW_KEY_PAGE_UP -> 201;
            case GLFW.GLFW_KEY_LEFT -> 203; case GLFW.GLFW_KEY_RIGHT -> 205; case GLFW.GLFW_KEY_END -> 207; case GLFW.GLFW_KEY_DOWN -> 208;
            case GLFW.GLFW_KEY_PAGE_DOWN -> 209; case GLFW.GLFW_KEY_INSERT -> 210; case GLFW.GLFW_KEY_DELETE -> 211;
            case GLFW.GLFW_KEY_LEFT_SUPER -> 219; case GLFW.GLFW_KEY_RIGHT_SUPER -> 220; case GLFW.GLFW_KEY_MENU -> 221;
            default -> 0;
        };
    }
    static boolean printable(int key) { return key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_GRAVE_ACCENT || key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_EQUAL; }
    static char controlCharacter(int key) {
        return switch (key) { case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> '\r'; case GLFW.GLFW_KEY_TAB -> '\t'; case GLFW.GLFW_KEY_BACKSPACE -> '\b'; default -> 0; };
    }
}
