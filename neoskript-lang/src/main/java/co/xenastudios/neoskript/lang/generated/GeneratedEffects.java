package co.xenastudios.neoskript.lang.generated;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.ComputedExpression;
import co.xenastudios.neoskript.core.expression.ComputedListExpression;

/**
 * Generated effect syntax (action and target+value mutations).
 *
 * <p><strong>Generated code.</strong> These registrations were produced by the Skript-conformance
 * tooling (see {@code docs/conformance}) from the upstream Skript 2.15.3 source, then compile- and
 * parse-verified. Prefer regenerating over editing by hand; order is significant for overlapping
 * patterns and mirrors the original per-pass generation order.
 */
public final class GeneratedEffects {
    private GeneratedEffects() {}

    public static void register(SyntaxRegistry registry) {
        // no-value action effects
        registry.registerEffect("allow %object% to duplicate", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Allay h) {
                    h.setCanDuplicate(true);
                }
            };
        });
        registry.registerEffect("prevent %object% from duplicating", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Allay h) {
                    h.setCanDuplicate(false);
                }
            };
        });
        registry.registerEffect("lock age of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Breedable h) {
                    h.setAgeLock(true);
                }
            };
        });
        registry.registerEffect("allow %object% to age", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Breedable h) {
                    h.setAgeLock(false);
                }
            };
        });
        registry.registerEffect("cancel usage of %object%'s active item", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.clearActiveItem();
                }
            };
        });
        registry.registerEffect("make %object% teleport randomly", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Enderman h) {
                    h.teleport();
                }
            };
        });
        registry.registerEffect("make %object% despawn on chunk unload", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.setRemoveWhenFarAway(true);
                }
            };
        });
        registry.registerEffect("prevent %object% from despawning", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.setRemoveWhenFarAway(false);
                }
            };
        });
        registry.registerEffect("prevent %object% from naturally despawning", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Item h) {
                    h.setUnlimitedLifetime(true);
                }
            };
        });
        registry.registerEffect("unleash %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.setLeashHolder(null);
                }
            };
        });
        registry.registerEffect("make %object% an adult", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Ageable h) {
                    h.setAdult();
                }
            };
        });
        registry.registerEffect("make %object% a baby", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Ageable h) {
                    h.setBaby();
                }
            };
        });
        registry.registerEffect("make %object% duplicate", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Allay h) {
                    h.duplicateAllay();
                }
            };
        });
        registry.registerEffect("make %object% breedable", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Breedable h) {
                    h.setBreed(true);
                }
            };
        });
        registry.registerEffect("sterilize %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Breedable h) {
                    h.setBreed(false);
                }
            };
        });
        registry.registerEffect("save %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.World h) {
                    h.save();
                }
            };
        });
        registry.registerEffect("make %object% start sprinting", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Player h) {
                    h.setSprinting(true);
                }
            };
        });
        registry.registerEffect("make %object% stop sprinting", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Player h) {
                    h.setSprinting(false);
                }
            };
        });
        registry.registerEffect("make %object% start shivering", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Strider h) {
                    h.setShivering(true);
                }
            };
        });
        registry.registerEffect("make %object% stop shivering", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Strider h) {
                    h.setShivering(false);
                }
            };
        });
        registry.registerEffect("make %object% swing their main hand", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.swingMainHand();
                }
            };
        });
        registry.registerEffect("make %object% swing their off hand", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.swingOffHand();
                }
            };
        });
        registry.registerEffect("tame %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Tameable h) {
                    h.setTamed(true);
                }
            };
        });
        registry.registerEffect("untame %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Tameable h) {
                    h.setTamed(false);
                }
            };
        });
        registry.registerEffect("apply drop shadow to %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.TextDisplay h) {
                    h.setShadowed(true);
                }
            };
        });
        registry.registerEffect("remove drop shadow from %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.TextDisplay h) {
                    h.setShadowed(false);
                }
            };
        });
        registry.registerEffect("make %object% visible through blocks", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.TextDisplay h) {
                    h.setSeeThrough(true);
                }
            };
        });
        registry.registerEffect("prevent %object% from being visible through blocks", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.TextDisplay h) {
                    h.setSeeThrough(false);
                }
            };
        });
        registry.registerEffect("clear the title of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof net.kyori.adventure.audience.Audience h) {
                    h.clearTitle();
                }
            };
        });
        registry.registerEffect("reset the title of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof net.kyori.adventure.audience.Audience h) {
                    h.resetTitle();
                }
            };
        });
        registry.registerEffect("show the custom name of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Entity h) {
                    h.setCustomNameVisible(true);
                }
            };
        });
        registry.registerEffect("hide the custom name of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Entity h) {
                    h.setCustomNameVisible(false);
                }
            };
        });
        registry.registerEffect("allow %object% to pick up items", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.setCanPickupItems(true);
                }
            };
        });
        registry.registerEffect("forbid %object% from picking up items", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.setCanPickupItems(false);
                }
            };
        });
        // source-grounded target / target+value effects
        registry.registerEffect("apply bone[ ]meal[s] to %object% %object% times", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.block.Block h) {
                    int times = 1;
                    if (v instanceof Number) times = ((Number) v).intValue();
                    if (times < 0) times = 0;
                    for (int i = 0; i < times; i++) h.applyBoneMeal(org.bukkit.block.BlockFace.UP);
                }
            };
        });
        registry.registerEffect("(affect|afflict) %object% with %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (v instanceof org.bukkit.potion.PotionEffect) h.addPotionEffect((org.bukkit.potion.PotionEffect) v);
                }
            };
        });
        registry.registerEffect("(clear|empty) the (stored entities|entity storage) of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.block.Block h) {
                    org.bukkit.block.BlockState _s = h.getState();
                    if (_s instanceof org.bukkit.block.EntityBlockStorage) {
                        org.bukkit.block.EntityBlockStorage<?> _st = (org.bukkit.block.EntityBlockStorage<?>) _s;
                        _st.clearEntities();
                        _st.update(true, false);
                    };
                }
            };
        });
        registry.registerEffect("(dye|colo[u]r|paint) %object% %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.ItemStack h) {
                    org.bukkit.Color _c = null;
                    if (v instanceof org.bukkit.Color) _c = (org.bukkit.Color) v;
                    else if (v instanceof org.bukkit.DyeColor) _c = ((org.bukkit.DyeColor) v).getColor();
                    if (_c != null) {
                        org.bukkit.inventory.meta.ItemMeta _m = h.getItemMeta();
                        if (_m instanceof org.bukkit.inventory.meta.LeatherArmorMeta) {
                            ((org.bukkit.inventory.meta.LeatherArmorMeta) _m).setColor(_c);
                            h.setItemMeta(_m);
                        } else if (_m instanceof org.bukkit.inventory.meta.MapMeta) {
                            ((org.bukkit.inventory.meta.MapMeta) _m).setColor(_c);
                            h.setItemMeta(_m);
                        } else if (_m instanceof org.bukkit.inventory.meta.PotionMeta) {
                            ((org.bukkit.inventory.meta.PotionMeta) _m).setColor(_c);
                            h.setItemMeta(_m);
                        }
                    };
                }
            };
        });
        registry.registerEffect("make command block[s] %object% conditional", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.block.Block h) {
                    org.bukkit.block.data.BlockData _d = h.getBlockData();
                    if (_d instanceof org.bukkit.block.data.type.CommandBlock) {
                        org.bukkit.block.data.type.CommandBlock _c = (org.bukkit.block.data.type.CommandBlock) _d;
                        _c.setConditional(true);
                        h.setBlockData(_c);
                    };
                }
            };
        });
        registry.registerEffect("make %object% (start dancing|dance)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (h instanceof org.bukkit.entity.Allay) {
                        ((org.bukkit.entity.Allay) h).startDancing();
                    } else if (h instanceof org.bukkit.entity.Piglin) {
                        ((org.bukkit.entity.Piglin) h).setDancing(true);
                    };
                }
            };
        });
        registry.registerEffect("disenchant %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.ItemStack h) {
                    for (org.bukkit.enchantments.Enchantment e : new java.util.HashSet<>(h.getEnchantments().keySet())) h.removeEnchantment(e);
                    org.bukkit.inventory.meta.ItemMeta m = h.getItemMeta();
                    if (m instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta es) {
                        for (org.bukkit.enchantments.Enchantment e2 : new java.util.HashSet<>(es.getStoredEnchants().keySet())) es.removeStoredEnchant(e2);
                        h.setItemMeta(m);
                    };
                }
            };
        });
        registry.registerEffect("make %object% (randomly teleport|teleport randomly)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Enderman h) {
                    h.teleport();
                }
            };
        });
        registry.registerEffect("(make|let) %object% (lose durability|be damaged) (on [wearer['s]] injury|when [[the] wearer [is]] (hurt|injured|damaged))", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.meta.components.EquippableComponent h) {
                    h.setDamageOnHurt(true);
                }
            };
        });
        registry.registerEffect("allow %object% to be sheared off [of entities]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.meta.components.EquippableComponent h) {
                    h.setCanBeSheared(true);
                }
            };
        });
        registry.registerEffect("(allow|force) %object% to swap equipment [on right click|when right clicked]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.meta.components.EquippableComponent h) {
                    h.setSwappable(true);
                }
            };
        });
        registry.registerEffect("(expand|grow) [the] diameter of %object% by %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.WorldBorder h) {
                    if (v instanceof Number n) h.setSize(h.getSize() + n.doubleValue());
                }
            };
        });
        registry.registerEffect("create an explosion at %object% with force %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.Location h) {
                    if (v instanceof Number n && h.getWorld() != null) h.getWorld().createExplosion(h, n.floatValue(), false);
                }
            };
        });
        registry.registerEffect("make %object% start eating", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (h instanceof org.bukkit.entity.Panda p) {
                        p.setEating(true);
                    } else if (h instanceof org.bukkit.entity.AbstractHorse ah) {
                        ah.setEating(true);
                    };
                }
            };
        });
        registry.registerEffect("(force|make) %object% [to] [start] glint[ing]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.ItemStack h) {
                    org.bukkit.inventory.meta.ItemMeta m = h.getItemMeta();
                    if (m != null) {
                        m.setEnchantmentGlintOverride(true);
                        h.setItemMeta(m);
                    };
                }
            };
        });
        registry.registerEffect("make %object% get on (its|their) back[s]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (h instanceof org.bukkit.entity.Panda p) {
                        p.setOnBack(true);
                    };
                }
            };
        });
        registry.registerEffect("remove both horns of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (h instanceof org.bukkit.entity.Goat) {
                        org.bukkit.entity.Goat g = (org.bukkit.entity.Goat) h;
                        g.setLeftHorn(true);
                        g.setRightHorn(true);
                    };
                }
            };
        });
        registry.registerEffect("(add|insert) %object% [in[ ]]to [the] (stored entities|entity storage) of %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (v instanceof org.bukkit.block.Block) {
                        org.bukkit.block.BlockState state = ((org.bukkit.block.Block) v).getState();
                        if (state instanceof org.bukkit.block.Beehive && h instanceof org.bukkit.entity.Bee) {
                            org.bukkit.block.Beehive hive = (org.bukkit.block.Beehive) state;
                            if (hive.getEntityCount() < hive.getMaxEntities()) {
                                hive.addEntity((org.bukkit.entity.Bee) h);
                                hive.update(true, false);
                            }
                        }
                    };
                }
            };
        });
        registry.registerEffect("hide [the] entire tool[ ]tip of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.ItemStack h) {
                    org.bukkit.inventory.meta.ItemMeta meta = h.getItemMeta();
                    if (meta != null) {
                        meta.setHideTooltip(true);
                        h.setItemMeta(meta);
                    };
                }
            };
        });
        registry.registerEffect("load [the] world[s] %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof java.lang.String h) {
                    new org.bukkit.WorldCreator(h).createWorld();
                }
            };
        });
        registry.registerEffect("make %object% sense [a] disturbance %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (h instanceof org.bukkit.entity.Warden w && v instanceof org.bukkit.Location loc) w.setDisturbanceLocation(loc);
                }
            };
        });
        registry.registerEffect("make %object% (start screaming|scream)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (h instanceof org.bukkit.entity.Goat g) {
                        g.setScreaming(true);
                    } else if (h instanceof org.bukkit.entity.Enderman e) {
                        e.setScreaming(true);
                    };
                }
            };
        });
        registry.registerEffect("make %object% (fire resistant|resistant to fire)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.ItemStack h) {
                    org.bukkit.inventory.meta.ItemMeta meta = h.getItemMeta();
                    if (meta != null) {
                        meta.setFireResistant(true);
                        h.setItemMeta(meta);
                    };
                }
            };
        });
        registry.registerEffect("make %object% ram %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (h instanceof org.bukkit.entity.Goat g && v instanceof org.bukkit.entity.LivingEntity lv) g.ram(lv);
                }
            };
        });
        registry.registerEffect("make %object% responsive", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Entity h) {
                    if (h instanceof org.bukkit.entity.Interaction i) i.setResponsive(true);
                }
            };
        });
        registry.registerEffect("make %object% (start rolling|roll)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Panda h) {
                    h.setRolling(true);
                }
            };
        });
        registry.registerEffect("make %object% (start sneezing|sneeze)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Panda h) {
                    h.setSneezing(true);
                }
            };
        });
        registry.registerEffect("make %object% (say|send [the] message[s]) %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Player h) {
                    if (v != null) h.chat(String.valueOf(v));
                }
            };
        });
        registry.registerEffect("make %object% have glowing text", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.block.Block h) {
                    org.bukkit.block.BlockState state = h.getState();
                    if (state instanceof org.bukkit.block.Sign sign) {
                        sign.setGlowingText(true);
                        state.update();
                    };
                }
            };
        });
        registry.registerEffect("(open|show) book %object% (to|for) %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.ItemStack h) {
                    if (h != null && h.getType() == org.bukkit.Material.WRITTEN_BOOK && v instanceof org.bukkit.entity.Player p) p.openBook(h);
                }
            };
        });
        registry.registerEffect("open [the] lid[s] (of|for) %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.block.Block h) {
                    org.bukkit.block.BlockState state = h.getState();
                    if (state instanceof org.bukkit.block.Lidded lidded) lidded.open();
                }
            };
        });
        registry.registerEffect("make %object% (pathfind|move) to[wards] %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Mob h) {
                    if (v instanceof org.bukkit.entity.LivingEntity le) h.getPathfinder().moveTo(le, 1.0);
                    else if (v instanceof org.bukkit.Location loc) h.getPathfinder().moveTo(loc, 1.0);
                }
            };
        });
        registry.registerEffect("make %object% (start playing|play) dead", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Axolotl h) {
                    h.setPlayingDead(true);
                }
            };
        });
        registry.registerEffect("(cure|unpoison) %object% [(from|of) poison]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.removePotionEffect(org.bukkit.potion.PotionEffectType.POISON);
                }
            };
        });
        registry.registerEffect("(release|evict) [the] (stored entities|entity storage) of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.block.Block h) {
                    org.bukkit.block.BlockState state = h.getState();
                    if (state instanceof org.bukkit.block.EntityBlockStorage<?> storage) storage.releaseEntities();
                }
            };
        });
        registry.registerEffect("ring %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.block.Block h) {
                    org.bukkit.block.BlockState state = h.getState();
                    if (state instanceof org.bukkit.block.Bell bell) bell.ring((org.bukkit.entity.Entity) null, (org.bukkit.block.BlockFace) null);
                }
            };
        });
        registry.registerEffect("send [the] resource pack to %object% from [the] URL %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Player h) {
                    if (v instanceof String url) {
                        try {
                            h.setResourcePack(url);
                        } catch (Exception ignored) {
                        }
                    };
                }
            };
        });
        registry.registerEffect("shear %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (h instanceof io.papermc.paper.entity.Shearable s) {
                        if (s.readyToBeSheared()) s.shear();
                    } else if (h instanceof org.bukkit.entity.Sheep sheep) sheep.setSheared(true);
                    else if (h instanceof org.bukkit.entity.Snowman snowman) snowman.setDerp(true);
                }
            };
        });
        registry.registerEffect("make %object% (start sprinting|sprint)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Player h) {
                    h.setSprinting(true);
                }
            };
        });
        registry.registerEffect("(apply|add) (drop|text) shadow to [[the] text of] %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Display h) {
                    if (h instanceof org.bukkit.entity.TextDisplay td) td.setShadowed(true);
                }
            };
        });
        registry.registerEffect("make %object% visible through (blocks|walls)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Display h) {
                    if (h instanceof org.bukkit.entity.TextDisplay td) td.setSeeThrough(true);
                }
            };
        });
        registry.registerEffect("(toggle|switch) [[the] state of] %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.block.Block h) {
                    org.bukkit.block.data.BlockData data = h.getBlockData();
                    if (data instanceof org.bukkit.block.data.Openable openable) {
                        openable.setOpen(!openable.isOpen());
                        h.setBlockData(data);
                    } else if (data instanceof org.bukkit.block.data.Powerable powerable) {
                        powerable.setPowered(!powerable.isPowered());
                        h.setBlockData(data);
                    };
                }
            };
        });
        registry.registerEffect("show [the] (custom|display)[ ]name of %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Entity h) {
                    h.setCustomNameVisible(true);
                }
            };
        });
        registry.registerEffect("allow %object% to pick([ ]up items| items up)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    h.setCanPickupItems(true);
                }
            };
        });
        registry.registerEffect("update %object% (as|to be) %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.block.Block h) {
                    if (v instanceof org.bukkit.block.data.BlockData bd) h.setBlockData(bd, true);
                }
            };
        });
        registry.registerEffect("zombify %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Villager h) {
                    h.zombify();
                }
            };
        });
        // second-pass recovered effects
        registry.registerEffect("ban %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.OfflinePlayer h) {
                    String n = h.getName();
                    if (n != null) org.bukkit.Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(n, null, (java.util.Date) null, "Skript ban effect");
                }
            };
        });
        registry.registerEffect("unban %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.OfflinePlayer h) {
                    String n = h.getName();
                    if (n != null) org.bukkit.Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(n);
                }
            };
        });
        registry.registerEffect("transfer %object% to server %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Player h) {
                    if (h.isOnline() && v instanceof String s) h.transfer(s, 25565);
                }
            };
        });
        registry.registerEffect("knockback %object% %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.LivingEntity h) {
                    if (v instanceof org.bukkit.util.Vector vec) {
                        h.knockback(1.0, -vec.getX(), -vec.getZ());
                        h.setVelocity(h.getVelocity());
                    };
                }
            };
        });
        registry.registerEffect("launch firework at %object% with effect %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.Location h) {
                    org.bukkit.World w = h.getWorld();
                    if (w != null && v instanceof org.bukkit.FireworkEffect fe) {
                        org.bukkit.entity.Firework f = w.spawn(h, org.bukkit.entity.Firework.class);
                        org.bukkit.inventory.meta.FireworkMeta m = f.getFireworkMeta();
                        m.addEffect(fe);
                        m.setPower(1);
                        f.setFireworkMeta(m);
                    };
                }
            };
        });
        registry.registerEffect("make %object% play [entity] effect %object%", a -> {
            Expression<?> src = a.get(0);
            Expression<?> ve = a.get(1);
            return ctx -> {
                Object o = src.getSingle(ctx);
                Object v = ve.getSingle(ctx);
                if (o instanceof org.bukkit.entity.Entity h) {
                    if (v instanceof org.bukkit.EntityEffect ee && ee.isApplicableTo(h)) h.playEffect(ee);
                }
            };
        });
        // event-restricted syntax
        registry.registerEffect("(force|allow) [the] (lead|leash) [item] to drop", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev != null) {
                if (ev instanceof org.bukkit.event.entity.EntityUnleashEvent _e) _e.setDropLeash(true);
                ;
            }
        });
        registry.registerEffect("apply [the] lure enchantment bonus", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev != null) {
                if (ev instanceof org.bukkit.event.player.PlayerFishEvent _e) _e.getHook().setApplyLure(true);
                ;
            }
        });
        registry.registerEffect("(cancel|clear|delete) [the] drops", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev != null) {
                if (ev instanceof org.bukkit.event.entity.EntityDeathEvent _d) {
                    _d.getDrops().clear();
                    _d.setDroppedExp(0);
                } else if (ev instanceof org.bukkit.event.block.BlockBreakEvent _b) {
                    _b.setDropItems(false);
                    _b.setExpToDrop(0);
                } else if (ev instanceof org.bukkit.event.block.BlockDropItemEvent _di) {
                    _di.getItems().forEach(org.bukkit.entity.Item::remove);
                } else if (ev instanceof org.bukkit.event.player.PlayerHarvestBlockEvent _h) {
                    _h.getItemsHarvested().clear();
                };
            }
        });
        registry.registerEffect("(prevent|disallow) [the] (boosting|used) firework from being consumed", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev != null) {
                if (ev instanceof com.destroystokyo.paper.event.player.PlayerElytraBoostEvent _e) _e.setShouldConsume(false);
                ;
            }
        });
        registry.registerEffect("make [the] brewing stand consume [its|the] fuel", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev != null) {
                if (ev instanceof org.bukkit.event.inventory.BrewingStandFuelEvent _e) _e.setConsuming(true);
                ;
            }
        });
        registry.registerEffect("keep [the] (inventory|items)", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev != null) {
                if (ev instanceof org.bukkit.event.entity.PlayerDeathEvent _e) _e.setKeepInventory(true);
                ;
            }
        });
        registry.registerEffect("make [the] egg hatch", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev != null) {
                if (ev instanceof org.bukkit.event.player.PlayerEggThrowEvent _e) {
                    _e.setHatching(true);
                    if (_e.getNumHatches() == 0) _e.setNumHatches((byte) 1);
                };
            }
        });
        registry.registerEffect("hide [all] player [related] info[rmation] [(in|on|from) [the] server list]", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev != null) {
                if (ev instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent _e) _e.setHidePlayers(true);
                ;
            }
        });
        registry.registerEffect("(reel|pull) in [the] hook[ed] entity", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev != null) {
                if (ev instanceof org.bukkit.event.player.PlayerFishEvent _e) _e.getHook().pullHookedEntity();
                ;
            }
        });
        // final permissive condition / effect recovery
        registry.registerEffect("generate [the] loot (of|using) %object% [(with|using) %object%] in %object%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            if (a0 instanceof org.bukkit.loot.LootTable && a2 instanceof org.bukkit.inventory.Inventory) {
                org.bukkit.loot.LootContext __ctx;
                if (a1 instanceof org.bukkit.loot.LootContext) {
                    __ctx = (org.bukkit.loot.LootContext) a1;
                } else {
                    __ctx = new org.bukkit.loot.LootContext.Builder(org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation()).build();
                } try {
                    ((org.bukkit.loot.LootTable) a0).fillInventory((org.bukkit.inventory.Inventory) a2, java.util.concurrent.ThreadLocalRandom.current(), __ctx);
                } catch (java.lang.IllegalArgumentException __ignore) {
                }
            };
        });
        registry.registerEffect("enable PvP [in %object%]", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            if (a0 instanceof org.bukkit.World w) w.setGameRule(org.bukkit.GameRule.PVP, true);
            ;
        });
        registry.registerEffect("disable PVP [in %object%]", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            if (a0 instanceof org.bukkit.World w) w.setGameRule(org.bukkit.GameRule.PVP, false);
            ;
        });
        registry.registerEffect("rotate %object% by x %object%, y %object%(, [and]| and) z %object%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            Object a3 = a.get(3).getSingle(ctx);
            if (a1 instanceof java.lang.Number nx && a2 instanceof java.lang.Number ny && a3 instanceof java.lang.Number nz) {
                float radX = (float) (nx.floatValue() * java.lang.Math.PI / 180);
                float radY = (float) (ny.floatValue() * java.lang.Math.PI / 180);
                float radZ = (float) (nz.floatValue() * java.lang.Math.PI / 180);
                if (a0 instanceof org.joml.Quaternionf q) {
                    q.rotateZYX(radZ, radY, radX);
                } else if (a0 instanceof org.bukkit.entity.Display d) {
                    org.bukkit.util.Transformation t = d.getTransformation();
                    d.setTransformation(new org.bukkit.util.Transformation(t.getTranslation(), t.getLeftRotation().rotateZYX(radZ, radY, radX), t.getScale(), t.getRightRotation()));
                }
            };
        });
        registry.registerEffect("make %object% see %object% as %object%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            if (a0 instanceof org.bukkit.entity.Player p && a1 instanceof org.bukkit.Location loc && a2 instanceof org.bukkit.block.data.BlockData bd) p.sendBlockChange(loc, bd);
            ;
        });
        registry.registerEffect("make %object% see %object% as [the|its] (original|normal|actual) [block]", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            if (a0 instanceof org.bukkit.entity.Player p && a1 instanceof org.bukkit.Location loc) p.sendBlockChange(loc, loc.getBlock().getBlockData());
            ;
        });
        registry.registerEffect("(stop|shut[ ]down) [the] server", a -> ctx -> {
            org.bukkit.Bukkit.shutdown();
            ;
        });
        registry.registerEffect("restart [the] server", a -> ctx -> {
            org.bukkit.Bukkit.spigot().restart();
            ;
        });
        registry.registerEffect("(make|let|force) %object% [to] (ride|mount) [(in|on)] %object%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            if (a0 instanceof org.bukkit.entity.Entity && a1 instanceof org.bukkit.entity.Entity) {
                ((org.bukkit.entity.Entity) a0).leaveVehicle();
                ((org.bukkit.entity.Entity) a1).addPassenger((org.bukkit.entity.Entity) a0);
            };
        });
        registry.registerEffect("(make|let|force) %object% [to] (dismount|(dismount|leave) (from|of|) (any|the[ir]|his|her|) vehicle[s])", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            if (a0 instanceof org.bukkit.entity.Entity) ((org.bukkit.entity.Entity) a0).leaveVehicle();
            ;
        });
        registry.registerEffect("(eject|dismount) (any|the|) passenger[s] (of|from) %object%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            if (a0 instanceof org.bukkit.entity.Entity) ((org.bukkit.entity.Entity) a0).eject();
            ;
        });
    }
}
