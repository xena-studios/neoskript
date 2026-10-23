/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Arithmetic;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;

/** Skript's {@code experience}: an amount of experience points, written {@code 5 xp}. */
record Experience(int xp) {

    private static final Pattern LITERAL = Pattern.compile("(\\d+) (?:e?xp|experience(?: points?)?)");

    @Override
    public String toString() {
        return xp + " xp";
    }

    static Experience parse(String text) {
        Matcher matcher = LITERAL.matcher(text.strip().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new Experience(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException tooMuch) {
            return null;
        }
    }

    private static Object apply(char operator, Object a, Object b) {
        return switch (operator) {
            case '+' -> Arithmetic.add(a, b);
            case '-' -> Arithmetic.subtract(a, b);
            case '*' -> Arithmetic.multiply(a, b);
            case '/' -> Arithmetic.divide(a, b);
            default -> Arithmetic.power(a, b);
        };
    }

    static void register(Registrar registrar) {
        registrar.type("experience", "experience", Experience.class);
        registrar.literalParser("experience", Experience::parse);
        registrar.parser("experience", Experience::parse);
        registrar.text(Experience.class, Experience::toString);
        registrar.converter(Experience.class, Number.class, experience -> (long) experience.xp());
        // Experience counts as its number of points in arithmetic: '5 xp + 5' is 10.
        for (char operator : new char[] {'+', '-', '*', '/', '^'}) {
            registrar.arithmetic(operator, Experience.class, Number.class,
                    (a, b) -> apply(operator, (long) a.xp(), b));
            registrar.arithmetic(operator, Number.class, Experience.class,
                    (a, b) -> apply(operator, a, (long) b.xp()));
            registrar.arithmetic(operator, Experience.class, Experience.class,
                    (a, b) -> apply(operator, (long) a.xp(), (long) b.xp()));
        }
        // An experience orb's points, or all of a player's.
        registrar.expression("ExprTotalExperience", "integer", info -> new SettableProperty<>(Entity.class, Number.class,
                entity -> entity instanceof ExperienceOrb orb ? (Object) (long) orb.getExperience()
                        : entity instanceof Player player ? (Object) (long) player.calculateTotalExperiencePoints() : null,
                (entity, amount) -> {
                    int points = (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount.doubleValue()));
                    if (entity instanceof ExperienceOrb orb) {
                        orb.setExperience(points);
                    } else if (entity instanceof Player player) {
                        player.setExperienceLevelAndProgress(points);
                    }
                }).resettingTo(0L), "[the] [total] experience of %entities%", "%entities%'[s] [total] experience");
    }
}
