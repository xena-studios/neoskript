/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import java.util.regex.Pattern;
import net.milkbowl.vault.economy.Economy;

/**
 * Skript's {@code money} ({@code ch.njol.skript.hooks.economy.classes.Money}): an amount of the economy's
 * currency, written with its name or symbol ({@code 10 dollars}, {@code $5}) and shown as the economy formats it.
 *
 * <p>It's a number, as Skript converts money to one wherever a number goes, so it compares and orders against
 * numbers ({@code player's balance >= 100}) and does arithmetic as one. Only used while Vault's economy is hooked.
 */
final class Money extends Number implements Comparable<Money> {

    /** Skript's {@code JavaClasses.DECIMAL_PATTERN}, which an amount has to match. */
    private static final Pattern DECIMAL = Pattern.compile(
            "(?<num>-?\\d+(_\\d+)*(?>\\.\\d+(_\\d+)*)?%?(?:[eE][+-]?\\d+)?)(?: (?:in )?(?:(?<rad>rad(?:ian)?)|deg(?:ree)?)s?)?");

    private final double amount;

    Money(double amount) {
        this.amount = amount;
    }

    double getAmount() {
        return amount;
    }

    /**
     * An amount written with the currency's plural or singular name before or after it, as Skript's
     * {@code Money.parse}; {@code null} if it isn't one, or there's no economy.
     */
    static Money parse(String text) {
        Economy economy = VaultHook.economy();
        if (economy == null) {
            return null;
        }
        String singular = economy.currencyNameSingular();
        String plural = economy.currencyNamePlural();
        if (plural != null && !plural.isEmpty()) {
            Money money = parse(text, plural);
            if (money != null) {
                return money;
            }
        }
        if (singular != null && !singular.isEmpty()) {
            return parse(text, singular);
        }
        return null;
    }

    private static Money parse(String text, String currency) {
        if (text.regionMatches(true, text.length() - currency.length(), currency, 0, currency.length())
                && text.length() >= currency.length()) {
            Double amount = parseDouble(text.substring(0, text.length() - currency.length()).strip());
            return amount == null ? null : new Money(amount);
        }
        if (text.regionMatches(true, 0, currency, 0, currency.length())) {
            Double amount = parseDouble(text.substring(currency.length()).strip());
            return amount == null ? null : new Money(amount);
        }
        return null;
    }

    private static Double parseDouble(String text) {
        if (!DECIMAL.matcher(text).matches()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** As the economy formats it, as Skript shows money. */
    @Override
    public String toString() {
        Economy economy = VaultHook.economy();
        return economy == null ? Double.toString(amount) : String.valueOf(economy.format(amount));
    }

    /** How money reads in variable names, as Skript's {@code money:<amount>}. */
    String variableName() {
        return "money:" + amount;
    }

    @Override
    public int intValue() {
        return (int) amount;
    }

    @Override
    public long longValue() {
        return (long) amount;
    }

    @Override
    public float floatValue() {
        return (float) amount;
    }

    @Override
    public double doubleValue() {
        return amount;
    }

    @Override
    public int compareTo(Money other) {
        return Double.compare(amount, other.amount);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && Double.compare(amount, money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(amount);
    }
}
