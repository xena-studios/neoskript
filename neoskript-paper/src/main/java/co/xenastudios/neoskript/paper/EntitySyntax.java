/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** More about entities (max health, AI, being leashed, charged, alive), dropping items, and a few values. */
final class EntitySyntax {

    private EntitySyntax() {}

    static void register(Registrar registrar) {
        SettableProperty.register(registrar, "ExprMaxHealth", "number", "max[imum] health", "livingentities",
                LivingEntity.class, Number.class, living -> {
                    AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
                    return max == null ? null : max.getValue() / 2;
                }, (living, hearts) -> {
                    AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null) {
                        max.setBaseValue(Math.max(0.01, hearts.doubleValue() * 2));
                    }
                });
        SettableProperty.register(registrar, "ExprAI", "boolean", "(ai|artificial intelligence)", "livingentities",
                LivingEntity.class, Boolean.class, LivingEntity::hasAI, LivingEntity::setAI);

        condition(registrar, "CondLeashed", "entities", "leashed", Entity.class,
                entity -> entity instanceof LivingEntity living && living.isLeashed());
        condition(registrar, "CondIsCharged", "entities", "(charged|powered)", Entity.class,
                entity -> entity instanceof Creeper creeper ? creeper.isPowered()
                        : entity instanceof WitherSkull skull && skull.isCharged());
        condition(registrar, "CondIsAlive", "entities", "(alive|dead:dead)", Entity.class, entity -> !entity.isDead());
        condition(registrar, "CondIsInvulnerable", "entities", "(invulnerable|invincible)", Entity.class,
                Entity::isInvulnerable);

        registrar.effect("EffDrop", info -> (context, args) -> {
            Object[] places = args[1] == null ? new Object[0] : (Object[]) args[1];
            for (Object place : places) {
                if (!(place instanceof Location location) || location.getWorld() == null) {
                    continue;
                }
                for (ItemStack stack : Items.stacks((Object[]) args[0])) {
                    if (info.hasTag("still")) {
                        location.getWorld().dropItem(location, stack).setVelocity(new Vector());
                    } else {
                        location.getWorld().dropItemNaturally(location, stack);
                    }
                }
            }
        }, "drop %itemtypes% [(at|on) %locations%] [still:without velocity]");

        registrar.expression("ExprRandomUUID", "uuid", info -> (context, args) -> UUID.randomUUID(), "[a] random uuid");
        registrar.expression("ExprVectorRandom", "vector", info -> (context, args) -> Vector.getRandom()
                .subtract(new Vector(0.5, 0.5, 0.5)).normalize(), "[a] random vector");
        registrar.expression("ExprHash", "string", info -> {
            String algorithm = info.hasTag("md5") ? "MD5" : info.hasTag("384") ? "SHA-384"
                    : info.hasTag("512") ? "SHA-512" : "SHA-256";
            return (context, args) -> {
                List<Object> hashes = new ArrayList<>();
                for (Object text : (Object[]) args[0]) {
                    try {
                        MessageDigest digest = MessageDigest.getInstance(algorithm);
                        hashes.add(HexFormat.of().formatHex(digest.digest(Values.text(text).getBytes(StandardCharsets.UTF_8))));
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                    }
                }
                return hashes.size() == 1 ? hashes.getFirst() : hashes.toArray();
            };
        }, "%texts% hash[ed] with (md5:MD5|SHA-256|384:SHA-384|512:SHA-512)");
    }

    /**
     * A condition {@code %owners% (is|are) <property>} and its negation, true when {@code test} holds for
     * every owner. A {@code dead} tag flips it, for {@code is alive}/{@code is dead}.
     */
    private static <T> void condition(Registrar registrar, String id, String owners, String property, Class<T> type,
            Predicate<? super T> test) {
        registrar.condition(id, info -> {
            boolean negated = (info.pattern() == 1) != info.hasTag("dead");
            return (context, args) -> {
                Object[] values = (Object[]) args[0];
                for (Object value : values) {
                    if (!type.isInstance(value) || test.test(type.cast(value)) == negated) {
                        return false;
                    }
                }
                return values.length > 0;
            };
        }, "%" + owners + "% (is|are) " + property, "%" + owners + "% (isn't|is not|aren't|are not) " + property);
    }
}
