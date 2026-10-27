package li.cil.oc.server.component;

import com.mojang.authlib.GameProfile;
import li.cil.oc.Settings;
import li.cil.oc.api.event.SignChangeEvent;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Original single-sided sign access maps consistently to the modern sign's front text. */
public abstract class UpgradeSign extends UpgradeBase {
    private UpgradeSign(EnvironmentHost host, Visibility visibility) {
        super(host, "sign", "Sign upgrade", "Labelizer Deluxe", true);
        ((li.cil.oc.api.network.Component) node()).setVisibility(visibility);
    }

    protected SignBlockEntity findSign(Direction side) {
        if (host.world().getBlockEntity(position()) instanceof SignBlockEntity sign) return sign;
        return host.world().getBlockEntity(position().relative(side)) instanceof SignBlockEntity sign ? sign : null;
    }

    private String text(SignBlockEntity sign) {
        return Arrays.stream(sign.getFrontText().getMessages(false)).map(Component::getString).collect(Collectors.joining("\n"));
    }

    protected Object[] getValue(SignBlockEntity sign) {
        return sign == null ? new Object[]{null, "no sign"} : new Object[]{text(sign)};
    }

    protected Object[] setValue(SignBlockEntity sign, String value) {
        if (sign == null) return new Object[]{null, "no sign"};

        String[] lines = lines(value);
        if (!canChangeSign(sign, lines)) return new Object[]{null, "not allowed"};

        SignText updated = sign.getFrontText();
        for (int i = 0; i < 4; i++) updated = updated.setMessage(i, Component.literal(lines[i]));
        sign.setText(updated, true);
        NeoForge.EVENT_BUS.post(new SignChangeEvent.Post(sign, lines));
        // The original setValue returns each text component's string representation.
        return new Object[]{Arrays.stream(sign.getFrontText().getMessages(false)).map(Object::toString).collect(Collectors.joining("\n"))};
    }

    private static String[] lines(String value) {
        // Scala StringLike.lines uses LF/FF and strips CR only as part of CRLF.
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char separator = value.charAt(i);
            if (separator != '\n' && separator != '\f') continue;
            int end = i;
            if (separator == '\n' && end > start && value.charAt(end - 1) == '\r') end--;
            lines.add(value.substring(start, Math.min(start + 15, end)));
            start = i + 1;
        }
        if (start < value.length()) lines.add(value.substring(start, Math.min(start + 15, value.length())));
        while (lines.size() < 4) lines.add("");
        return lines.toArray(String[]::new);
    }

    private boolean canChangeSign(SignBlockEntity sign, String[] lines) {
        Player player = host instanceof li.cil.oc.api.internal.Robot robot ? robot.player()
            : FakePlayerFactory.get((ServerLevel) host.world(), new GameProfile(UUID.fromString(Settings.get().fakePlayerUuid), Settings.get().fakePlayerName));
        BlockPos position = sign.getBlockPos();
        if (sign.isWaxed() || !host.world().mayInteract(player, position)) return false;

        var breaking = new BlockEvent.BreakEvent(host.world(), position, sign.getBlockState(), player);
        NeoForge.EVENT_BUS.post(breaking);
        if (breaking.isCanceled()) return false;
        var changing = new SignChangeEvent.Pre(sign, lines);
        NeoForge.EVENT_BUS.post(changing);
        return !changing.isCanceled();
    }

    @Override public void onMessage(Message message) {
        super.onMessage(message);
        TabletUseData use = TabletUseData.from(message);
        if (use != null && host.world().getBlockEntity(use.position()) instanceof SignBlockEntity sign)
            use.result().putString("signText", text(sign));
    }

    public static final class InRotatable extends UpgradeSign {
        private final Rotatable rotatable;

        public InRotatable(EnvironmentHost host) {
            super(host, Visibility.Neighbors);
            rotatable = (Rotatable) host;
        }

        @Callback(doc = "function():string -- Get the text on the sign in front of the host.")
        public Object[] getValue(Context context, Arguments args) {
            return getValue(findSign(rotatable.facing()));
        }

        @Callback(doc = "function(value:string):string -- Set the text on the sign in front of the host.")
        public Object[] setValue(Context context, Arguments args) {
            return setValue(findSign(rotatable.facing()), args.checkString(0));
        }
    }

    public static final class InAdapter extends UpgradeSign {
        public InAdapter(EnvironmentHost host) {
            super(host, Visibility.Network);
        }

        private Direction checkSide(Arguments args) {
            int side = args.checkInteger(0);
            if (side < 0 || side > 5) throw new IllegalArgumentException("invalid side");
            return Direction.from3DDataValue(side);
        }

        @Callback(doc = "function(side:number):string -- Get the text on the sign on the specified side of the adapter.")
        public Object[] getValue(Context context, Arguments args) {
            return getValue(findSign(checkSide(args)));
        }

        @Callback(doc = "function(side:number, value:string):string -- Set the text on the sign on the specified side of the adapter.")
        public Object[] setValue(Context context, Arguments args) {
            return setValue(findSign(checkSide(args)), args.checkString(1));
        }
    }
}
