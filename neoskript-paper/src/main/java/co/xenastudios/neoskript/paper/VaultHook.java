/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

/**
 * Skript's Vault hook ({@code ch.njol.skript.hooks.VaultHook}): money and balances with an economy plugin, chat
 * prefixes and suffixes with a chat plugin, and groups with a permissions plugin, each only if Vault has a
 * provider for it once the server has started. Only loaded when Vault is installed.
 */
final class VaultHook {

    static final String NO_GROUP_SUPPORT = "The permissions plugin you are using does not support groups.";

    private static volatile Economy economy;
    private static volatile Chat chat;
    private static volatile Permission permission;

    private VaultHook() {}

    static Economy economy() {
        return economy;
    }

    /**
     * Takes Vault's economy, chat and permission providers, as Skript's {@code VaultHook.init}: whether there is
     * any of them.
     */
    static boolean hook(ServicesManager services) {
        return hook(provider(services, Economy.class), provider(services, Chat.class),
                provider(services, Permission.class));
    }

    /** Hooks into these providers, any of which may be {@code null}; whether there is any. */
    static boolean hook(Economy economy, Chat chat, Permission permission) {
        VaultHook.economy = economy;
        VaultHook.chat = chat;
        VaultHook.permission = permission;
        return economy != null || chat != null || permission != null;
    }

    private static <T> T provider(ServicesManager services, Class<T> service) {
        RegisteredServiceProvider<T> registration = services.getRegistration(service);
        return registration == null ? null : registration.getProvider();
    }

    /**
     * Registers the syntax of each service, as Skript loads its hook's packages. Vault's providers may only be
     * there once the server has started, so it's all registered, and an element whose service has no provider
     * fails to parse.
     */
    static void register(Registrar registrar) {
        registerEconomy(registrar);
        registerChat(registrar);
        registerPermission(registrar);
    }

    /** Fails to parse an element of a service without a provider, which Skript wouldn't have. */
    private static void require(Object provider, String element, String plugin) {
        if (provider == null) {
            throw new SyntaxError("The " + element + " expression requires Vault and " + plugin + " that supports Vault");
        }
    }

    private static void registerEconomy(Registrar registrar) {
        registrar.type("money", "money", Money.class);
        registrar.literalParser("money", Money::parse);
        registrar.parser("money", Money::parse);
        registrar.text(Money.class, Money::toString);
        registrar.variableNameText(Money.class, Money::variableName);
        registrar.converter(Money.class, Double.class, Money::getAmount);
        registrar.arithmeticDefault(Money.class, () -> new Money(0));
        registrar.expression("ExprBalance", "money", info -> {
            require(economy, "money", "an economy plugin");
            return new Balance();
        },
                Patterns.property("(money|balance|[bank] account)", "offlineplayers"));
    }

    /** Skript's ExprBalance: how much money players have, which can be set, added to, taken from and cleared. */
    private static final class Balance implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> balances = new ArrayList<>();
            for (Object owner : Values.array(arguments[0])) {
                if (owner instanceof OfflinePlayer player) {
                    balances.add(balance(player));
                }
            }
            return balances.size() == 1 ? balances.getFirst() : balances.toArray();
        }

        @SuppressWarnings("deprecation")
        private static Money balance(OfflinePlayer player) {
            try {
                return new Money(economy.getBalance(player));
            } catch (Exception e) {
                // Economies from before Vault took offline players.
                return new Money(economy.getBalance(player.getName()));
            }
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.REMOVE_ALL ? null : new Class<?>[] {Money.class, Number.class};
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Economy economy = VaultHook.economy;
            if (delta == null || mode == Mode.DELETE || mode == Mode.RESET) {
                for (Object owner : Values.array(arguments[0])) {
                    if (owner instanceof OfflinePlayer player) {
                        economy.withdrawPlayer(player, economy.getBalance(player));
                    }
                }
                return;
            }
            if (delta.length == 0) {
                return;
            }
            // Money is a number, so either is its amount.
            double money = ((Number) delta[0]).doubleValue();
            for (Object owner : Values.array(arguments[0])) {
                if (!(owner instanceof OfflinePlayer player)) {
                    continue;
                }
                switch (mode) {
                    case SET -> {
                        double balance = economy.getBalance(player);
                        if (balance < money) {
                            economy.depositPlayer(player, money - balance);
                        } else if (balance > money) {
                            economy.withdrawPlayer(player, balance - money);
                        }
                    }
                    case ADD -> economy.depositPlayer(player, money);
                    case REMOVE -> economy.withdrawPlayer(player, money);
                    default -> {}
                }
            }
        }
    }

    private static void registerChat(Registrar registrar) {
        registrar.expression("ExprPrefixSuffix", "string", info -> {
            require(chat, "prefix/suffix", "a chat plugin");
            return new PrefixSuffix(Events.mark(info) == 1);
        },
                Patterns.property("[chat] (1:prefix|2:suffix)", "players"));
    }

    /** Skript's ExprPrefixSuffix: players' chat prefix or suffix, which can be set and cleared. */
    private record PrefixSuffix(boolean prefix) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object owner : Values.array(arguments[0])) {
                if (owner instanceof Player player) {
                    String value = prefix ? chat.getPlayerPrefix(player) : chat.getPlayerSuffix(player);
                    if (value != null) {
                        values.add(value);
                    }
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {String.class};
                case RESET, DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Object[] owners = Values.array(arguments[0]);
            String value = mode == Mode.SET && delta != null && delta.length > 0 ? (String) delta[0] : null;
            if (mode == Mode.SET && value == null) {
                return;
            }
            Chat chat = VaultHook.chat;
            // Off the server thread, as Skript does it: some chat plugins refuse Vault requests on it.
            CompletableFuture.runAsync(() -> {
                for (Object owner : owners) {
                    if (owner instanceof Player player) {
                        if (prefix) {
                            chat.setPlayerPrefix(player, value);
                        } else {
                            chat.setPlayerSuffix(player, value);
                        }
                    }
                }
            }).join();
        }
    }

    private static void registerPermission(Registrar registrar) {
        registrar.expression("ExprGroup", "string", info -> {
            require(permission, "group", "a permission plugin");
            if (!permission.hasGroupSupport()) {
                throw new SyntaxError(NO_GROUP_SUPPORT);
            }
            return new Group(!info.hasTag("plural"));
        }, Patterns.property("group[plural:s]", "offlineplayers"));
        registrar.expression("ExprAllGroups", "string", info -> {
            require(permission, "all groups", "a permission plugin");
            if (!permission.hasGroupSupport()) {
                throw new SyntaxError(NO_GROUP_SUPPORT);
            }
            return (context, arguments) -> {
                String[] groups = permission.getGroups();
                return groups == null ? new Object[0] : groups.clone();
            };
        }, "all groups");
    }

    /** Skript's ExprGroup: players' primary group, or all their groups, which can be changed. */
    private record Group(boolean primary) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            Object[] owners = Values.array(arguments[0]);
            Permission permission = VaultHook.permission;
            // Off the server thread, as Skript does it: LuckPerms refuses Vault requests on it.
            Object[] groups = CompletableFuture.supplyAsync(() -> {
                List<Object> found = new ArrayList<>();
                for (Object owner : owners) {
                    if (owner instanceof OfflinePlayer player) {
                        if (primary) {
                            found.add(permission.getPrimaryGroup(null, player));
                        } else {
                            Collections.addAll(found, (Object[]) permission.getPlayerGroups(null, player));
                        }
                    }
                }
                found.removeIf(java.util.Objects::isNull);
                return found.toArray();
            }).join();
            return groups.length == 1 ? groups[0] : groups;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case ADD, REMOVE, SET -> new Class<?>[] {String.class};
                case DELETE, RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Permission api = VaultHook.permission;
            Object[] values = delta == null ? new Object[0] : delta;
            for (Object owner : Values.array(arguments[0])) {
                if (!(owner instanceof OfflinePlayer player)) {
                    continue;
                }
                switch (mode) {
                    case ADD -> {
                        for (Object group : values) {
                            api.playerAddGroup(null, player, (String) group);
                        }
                    }
                    case REMOVE -> {
                        for (Object group : values) {
                            api.playerRemoveGroup(null, player, (String) group);
                        }
                    }
                    case RESET, DELETE, SET -> {
                        for (String group : api.getPlayerGroups(null, player)) {
                            api.playerRemoveGroup(null, player, group);
                        }
                        if (mode == Mode.SET) {
                            for (Object group : values) {
                                api.playerAddGroup(null, player, (String) group);
                            }
                        }
                    }
                    default -> {}
                }
            }
        }
    }
}
