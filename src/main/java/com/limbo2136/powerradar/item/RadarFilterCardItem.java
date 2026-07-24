package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.network.AllowlistCardOpenPayload;
import com.limbo2136.powerradar.network.TargetingCardOpenPayload;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class RadarFilterCardItem extends Item {
    private static final String PLAYER_PREFIX = "P\t";
    private static final String SABLE_PREFIX = "S\t";
    private static final String SABLE_QUERY_PREFIX = "Q\t";
    private static final int MAX_ALLOWLIST_NAMES = 1024;
    private static final int MAX_NAME_LENGTH = 64;

    public record AllowlistData(
            List<String> playerNames,
            List<String> sableNames
    ) {
        public AllowlistData {
            playerNames = List.copyOf(playerNames);
            sableNames = List.copyOf(sableNames);
        }

        public List<String> encodedLines() {
            ArrayList<String> lines = new ArrayList<>(this.playerNames.size() + this.sableNames.size());
            this.playerNames.forEach(name -> lines.add(PLAYER_PREFIX + name));
            this.sableNames.forEach(name -> lines.add(SABLE_QUERY_PREFIX + name));
            return List.copyOf(lines);
        }
    }

    public enum Kind {
        TARGETING,
        DISPLAY,
        ALLOWLIST
    }

    private final Kind kind;

    public RadarFilterCardItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    public static int filterMask(ItemStack stack, int fallback) {
        Integer value = stack.get(ModDataComponents.RADAR_FILTER_MASK.get());
        return value == null ? fallback : RadarDetectionFilters.sanitize(value);
    }

    public static String allowlist(ItemStack stack) {
        String value = stack.get(ModDataComponents.RADAR_ALLOWLIST.get());
        return value == null ? "" : value;
    }

    public static List<String> allowlistNames(ItemStack stack) {
        return allowlistData(stack).playerNames();
    }

    public static AllowlistData allowlistData(ItemStack stack) {
        return decodeAllowlistLines(
                Arrays.asList(allowlist(stack).split("\\n")),
                allowlistSableMode(stack));
    }

    public static AllowlistData decodeAllowlistLines(List<String> lines, boolean legacySableMode) {
        LinkedHashMap<String, String> players = new LinkedHashMap<>();
        LinkedHashMap<String, String> sables = new LinkedHashMap<>();
        for (String rawLine : lines == null ? List.<String>of() : lines) {
            if (rawLine == null || rawLine.isBlank()) {
                continue;
            }
            if (rawLine.startsWith(PLAYER_PREFIX)) {
                putName(players, rawLine.substring(PLAYER_PREFIX.length()));
                continue;
            }
            if (rawLine.startsWith(SABLE_QUERY_PREFIX)) {
                putName(sables, rawLine.substring(SABLE_QUERY_PREFIX.length()));
                continue;
            }
            if (rawLine.startsWith(SABLE_PREFIX)) {
                // Старый формат связывал имя с UUID. UUID намеренно отбрасывается:
                // после переименования табличкой список должен искать именно текущее имя Sable.
                String[] parts = rawLine.split("\\t", 3);
                if (parts.length != 3) {
                    continue;
                }
                putName(sables, parts[2]);
                continue;
            }
            if (legacySableMode) {
                putName(sables, rawLine);
            } else {
                putName(players, rawLine);
            }
        }
        return new AllowlistData(List.copyOf(players.values()), List.copyOf(sables.values()));
    }

    private static void putName(LinkedHashMap<String, String> target, String rawName) {
        String name = sanitizeName(rawName);
        if (!name.isEmpty() && target.size() < MAX_ALLOWLIST_NAMES) {
            target.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
    }

    private static String sanitizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String name = rawName.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        return name.length() <= MAX_NAME_LENGTH
                ? name
                : name.substring(0, MAX_NAME_LENGTH).trim();
    }

    public static boolean allowlistSableMode(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.ALLOWLIST_SABLE_MODE.get()));
    }

    public static int cardOption(ItemStack stack, int fallback) {
        Integer value = stack.get(ModDataComponents.TARGETING_CARD_OPTION.get());
        return value == null ? fallback : value == 0 ? 0 : 1;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.kind == Kind.TARGETING || this.kind == Kind.DISPLAY) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                boolean displayCard = this.kind == Kind.DISPLAY;
                PacketDistributor.sendToPlayer(serverPlayer, new TargetingCardOpenPayload(
                        hand,
                        displayCard ? 1 : 0,
                        filterMask(stack, 0),
                        cardOption(stack, displayCard ? 0 : 1)));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (this.kind == Kind.ALLOWLIST) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                List<String> onlinePlayers = serverPlayer.getServer().getPlayerList().getPlayers().stream()
                        .map(online -> online.getGameProfile().getName())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
                PacketDistributor.sendToPlayer(serverPlayer, new AllowlistCardOpenPayload(
                        hand,
                        allowlistSableMode(stack),
                        cardOption(stack, 1),
                        onlinePlayers,
                        allowlistData(stack).encodedLines()));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

}
