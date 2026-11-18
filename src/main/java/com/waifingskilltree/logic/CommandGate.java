package com.waifingskilltree.logic;

import com.waifingskilltree.WaifingSkilltreeConfig;
import com.waifingskilltree.capability.PlayerSkills;
import com.waifingskilltree.data.SkillNode;
import com.waifingskilltree.data.SkillTree;
import com.waifingskilltree.data.TreeStorage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Locks commands behind nodes. Every entry of every node's {@code unlock_commands} becomes "gated": players
 * below the bypass permission level can only use it once they hold a node that unlocks it. Unlocked commands
 * run with {@code unlockedCommandLevel} permission so that e.g. /give works for a player who earned it.
 */
public final class CommandGate {
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

    private CommandGate() {}

    public static String normalize(String c) {
        String s = c.trim().toLowerCase(Locale.ROOT);
        while (s.startsWith("/")) s = s.substring(1);
        return s.replaceAll("\\s+", " ");
    }

    private static boolean matches(String line, String entry) {
        return !entry.isEmpty() && (line.equals(entry) || line.startsWith(entry + " "));
    }

    private static boolean anyMatch(Set<String> entries, String line) {
        for (String e : entries) if (matches(line, e)) return true;
        return false;
    }

    private static Set<String> gatedEntries() {
        Set<String> out = new HashSet<>();
        for (SkillTree t : TreeStorage.all().values())
            for (SkillNode n : t.nodes.values())
                for (String c : n.unlockCommands) out.add(normalize(c));
        return out;
    }

    public static void onCommand(CommandEvent e) {
        if (BUSY.get()) return;
        CommandSourceStack src = e.getParseResults().getContext().getSource();
        if (!(src.getEntity() instanceof ServerPlayer p)) return;
        if (src.hasPermission(WaifingSkilltreeConfig.COMMAND_GATE_BYPASS_LEVEL.get())) return;

        String line = normalize(e.getParseResults().getReader().getString());
        Set<String> gated = gatedEntries();
        if (gated.isEmpty() || !anyMatch(gated, line)) return;

        PlayerSkills s = SkillManager.get(p);
        Set<String> mine = s == null ? Set.of() : SkillManager.unlockedCommands(s);
        if (!anyMatch(mine, line)) {
            e.setCanceled(true);
            p.displayClientMessage(Component.translatable("waifingskilltree.msg.command_locked"), false);
            return;
        }
        int level = WaifingSkilltreeConfig.UNLOCKED_COMMAND_LEVEL.get();
        if (level == 0 || src.hasPermission(level)) return;
        e.setCanceled(true);
        BUSY.set(true);
        try {
            p.server.getCommands().performPrefixedCommand(src.withPermission(level), line);
        } finally {
            BUSY.set(false);
        }
    }
}
