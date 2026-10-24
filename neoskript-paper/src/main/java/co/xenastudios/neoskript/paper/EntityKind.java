/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Bee;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Cat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Salmon;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.ItemStack;

/**
 * Skript's {@code entity type}: a kind of entity as scripts name it, like {@code pig}, {@code baby lucy
 * axolotl}, {@code angry bee with nectar} or {@code dropped stone}. A kind is a base (one entity type, or a
 * category of them such as {@code projectile}) plus variants that narrow it down. Spawning a kind makes an
 * entity with its variants; an entity is of a kind if it's of the base and has every variant.
 *
 * @param type the one entity type it is, or {@code null} for a category
 * @param entityClass the class entities of the kind have
 * @param amount how many entities it stands for, as in {@code spawn 5 villagers}
 */
public record EntityKind(String name, EntityType type, Class<? extends Entity> entityClass, List<Variant> variants,
        int amount) {

    /**
     * Something that narrows a kind down, like being a baby or a lucy axolotl.
     *
     * @param key what the variant is, for comparing kinds: {@code age=baby}, {@code axolotl=lucy}
     */
    public record Variant(String key, Predicate<Entity> test, Consumer<Entity> apply) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Variant variant && variant.key.equals(key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    public EntityKind {
        variants = List.copyOf(variants);
    }

    public EntityKind(String name, EntityType type, Class<? extends Entity> entityClass, List<Variant> variants) {
        this(name, type, entityClass, variants, 1);
    }

    private static volatile Map<String, EntityKind> bases;

    private static final java.util.regex.Pattern DISPLAY_OF = java.util.regex.Pattern.compile("displays?(?: entity)? of ");

    /** An amount before a kind's name: {@code 5 villagers}. */
    private static final java.util.regex.Pattern AMOUNT = java.util.regex.Pattern.compile("(\\d{1,9}) (.+)");

    /** Types whose kinds include their subtypes' entities, as in Skript: a trader llama is a llama. */
    private static final java.util.Set<EntityType> INCLUDES_SUBTYPES = java.util.Set.of(EntityType.LLAMA, EntityType.FIREBALL);

    /** Whether {@code entity} is of this kind. */
    public boolean matches(Entity entity) {
        if (type != null && !INCLUDES_SUBTYPES.contains(type) ? entity.getType() != type : !entityClass.isInstance(entity)) {
            return false;
        }
        for (Variant variant : variants) {
            if (!variant.test().test(entity)) {
                return false;
            }
        }
        return true;
    }

    /** Whether every entity of {@code other} is of this kind: {@code axolotl} includes {@code lucy axolotl}. */
    public boolean includes(EntityKind other) {
        boolean base = type != null && !INCLUDES_SUBTYPES.contains(type) ? other.type == type
                : other.type != null ? other.type.getEntityClass() != null && entityClass.isAssignableFrom(other.type.getEntityClass())
                : entityClass.isAssignableFrom(other.entityClass);
        return base && other.variants.containsAll(variants);
    }

    /** Whether this kind can be spawned: it's one entity type that can. */
    public boolean spawnable() {
        return type != null && (type.isSpawnable() || type == EntityType.ITEM || type == EntityType.FALLING_BLOCK);
    }

    /** Spawns an entity of this kind at {@code location}, with its variants, or returns {@code null}. */
    public Entity spawn(Location location) {
        if (!spawnable() || location.getWorld() == null) {
            return null;
        }
        Entity entity;
        if (type == EntityType.ITEM) {
            entity = location.getWorld().dropItem(location, new ItemStack(Material.STONE));
        } else if (type == EntityType.FALLING_BLOCK) {
            // Stone unless it says otherwise, as in Skript.
            entity = location.getWorld().spawn(location, FallingBlock.class,
                    falling -> falling.setBlockData(Material.STONE.createBlockData()));
        } else {
            entity = location.getWorld().spawnEntity(location, type);
        }
        for (Variant variant : variants) {
            variant.apply().accept(entity);
        }
        return entity;
    }

    @Override
    public String toString() {
        return name;
    }

    // ---------------------------------------------------------------- parsing

    /** Whether {@code text} starts with an amount: {@code 5 villagers}. */
    static boolean counted(String text) {
        return AMOUNT.matcher(text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")
                .replaceFirst("^(?:an?|the|any) ", "")).matches();
    }

    /** {@code name} without an amount before it: {@code villagers} for {@code 5 villagers}. */
    static String withoutAmount(String name) {
        java.util.regex.Matcher counted = AMOUNT.matcher(name);
        return counted.matches() ? counted.group(2) : name;
    }

    /** The kind {@code text} names, or {@code null}. */
    public static EntityKind parse(String text) {
        String words = text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceFirst("^(?:an?|the|any) ", "");
        if (words.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher counted = AMOUNT.matcher(words);
        if (counted.matches()) {
            EntityKind kind = parse(counted.group(2));
            int amount = Integer.parseInt(counted.group(1));
            return kind == null || amount < 1 ? null
                    : new EntityKind(words, kind.type, kind.entityClass, kind.variants, amount);
        }
        List<Variant> variants = new ArrayList<>();
        String name = words;

        // Things written around the entity's name.
        if (words.startsWith("dropped ") && !words.startsWith("dropped item")) {
            Material material = ItemType.alias(words.substring(8));
            if (material == null) {
                return null;
            }
            return new EntityKind(name, EntityType.ITEM, Item.class, List.of(new Variant("item=" + material,
                    e -> e instanceof Item item && item.getItemStack().getType() == material,
                    e -> ((Item) e).setItemStack(new ItemStack(material)))));
        }
        if (words.startsWith("falling ") && !words.startsWith("falling block")) {
            Material named = ItemType.alias(words.substring(8));
            // A block that isn't an item, like powder snow.
            Material material = named != null ? named : Material.matchMaterial(words.substring(8).replace(' ', '_'));
            if (material == null || !material.isBlock()) {
                return null;
            }
            return new EntityKind(name, EntityType.FALLING_BLOCK, FallingBlock.class, List.of(new Variant("block=" + material,
                    e -> e instanceof FallingBlock falling && falling.getBlockData().getMaterial() == material,
                    e -> ((FallingBlock) e).setBlockData(material.createBlockData()))));
        }
        int with = words.indexOf(" with nectar");
        int without = words.indexOf(" without nectar");
        if (without >= 0 || with >= 0) {
            boolean nectar = without < 0;
            variants.add(new Variant("nectar=" + nectar, e -> e instanceof Bee bee && bee.hasNectar() == nectar,
                    e -> ((Bee) e).setHasNectar(nectar)));
            words = words.substring(0, nectar ? with : without);
        }
        int color = words.indexOf(" color ");
        if (color >= 0) {
            DyeColor dye = dye(words.substring(color + 7));
            if (dye == null) {
                return null;
            }
            variants.add(new Variant("collar=" + dye, e -> e instanceof Wolf wolf && wolf.getCollarColor() == dye,
                    e -> ((Wolf) e).setCollarColor(dye)));
            words = words.substring(0, color);
        }
        int holding = words.indexOf(" holding ");
        if (holding >= 0) {
            Material material = ItemType.alias(words.substring(holding + 9));
            if (material == null || !material.isBlock()) {
                return null;
            }
            variants.add(new Variant("carrying=" + material, e -> e instanceof Enderman enderman
                    && enderman.getCarriedBlock() != null && enderman.getCarriedBlock().getMaterial() == material,
                    e -> ((Enderman) e).setCarriedBlock(material.createBlockData())));
            words = words.substring(0, holding);
        }
        // The 'of' right after 'display': what's after it may have its own ('of sharpness').
        java.util.regex.Matcher displayOf = DISPLAY_OF.matcher(words);
        int of = displayOf.find() ? displayOf.end() - 4 : words.lastIndexOf(" of ");
        if (of >= 0 && words.substring(0, of).replaceFirst(" entity$", "").matches(".*displays?")) {
            String display = words.substring(0, of).replaceFirst(" entity$", "");
            // An item display's item may have more to it: 'item display of a diamond sword of sharpness'.
            ItemType shown = display.startsWith("block") ? null : ItemType.parse(words.substring(of + 4));
            Material material = shown != null ? shown.material()
                    : ItemType.alias(words.substring(of + 4).replaceFirst("^(?:an?|the) ", ""));
            if (material == null) {
                return null;
            }
            ItemStack stack = shown != null ? shown.stack().clone() : new ItemStack(material);
            variants.add(display.startsWith("block")
                    ? new Variant("display=" + material, e -> e instanceof BlockDisplay block
                            && block.getBlock().getMaterial() == material,
                            e -> ((BlockDisplay) e).setBlock(material.createBlockData()))
                    : new Variant("display=" + material, e -> e instanceof ItemDisplay item
                            && item.getItemStack() != null && item.getItemStack().getType() == material,
                            e -> ((ItemDisplay) e).setItemStack(stack.clone())));
            words = words.substring(0, of);
        }
        // 'tamed ocelot' is Skript's old name for a cat.
        if (words.startsWith("tamed ") && words.endsWith(" ocelot")) {
            words = words.substring(0, words.length() - 6) + "cat";
        }
        // 'stone block display' is a block display of stone, 'diamond item display' an item display of a diamond;
        // 'red bed block displays' are several.
        // 'sand block display entity' too.
        String named = words.endsWith(" display entity") ? words.substring(0, words.length() - 7) : words;
        for (String suffix : new String[] {" block displays", " block display", " item displays", " item display"}) {
            if (!named.endsWith(suffix) || named.length() <= suffix.length()) {
                continue;
            }
            Material material = ItemType.alias(named.substring(0, named.length() - suffix.length())
                    .replaceFirst("^(?:an?|the) ", ""));
            boolean block = suffix.startsWith(" block");
            if (material != null && (!block || material.isBlock())) {
                variants.add(block
                        ? new Variant("display=" + material, e -> e instanceof BlockDisplay display
                                && display.getBlock().getMaterial() == material,
                                e -> ((BlockDisplay) e).setBlock(material.createBlockData()))
                        : new Variant("display=" + material, e -> e instanceof ItemDisplay display
                                && display.getItemStack() != null && display.getItemStack().getType() == material,
                                e -> ((ItemDisplay) e).setItemStack(new ItemStack(material))));
                words = suffix.strip();
            }
            break;
        }
        if (words.endsWith(" entity") && bases().containsKey(words.substring(0, words.length() - 7))) {
            words = words.substring(0, words.length() - 7);
        }
        if (words.endsWith(" kid")) {
            variants.add(age(true));
            words = words.substring(0, words.length() - 4);
        }

        // The entity's name is the longest run of words at the end that names one; the words before it are
        // its variants.
        String[] parts = words.split(" ");
        for (int start = 0; start < parts.length; start++) {
            String baseName = String.join(" ", java.util.Arrays.copyOfRange(parts, start, parts.length));
            EntityKind base = bases().get(baseName);
            if (base == null) {
                continue;
            }
            List<Variant> all = new ArrayList<>(base.variants);
            all.addAll(variants);
            if (!prefixes(java.util.Arrays.copyOfRange(parts, 0, start), base, all)) {
                return null;
            }
            return new EntityKind(name, base.type, base.entityClass, dedupe(all));
        }
        return null;
    }

    private static List<Variant> dedupe(List<Variant> variants) {
        Map<String, Variant> byKey = new LinkedHashMap<>();
        for (Variant variant : variants) {
            byKey.put(variant.key(), variant);
        }
        return new ArrayList<>(byKey.values());
    }

    /** Parses the words before an entity's name into its variants, or returns {@code false}. */
    private static boolean prefixes(String[] words, EntityKind base, List<Variant> into) {
        if (words.length == 0) {
            return true;
        }
        Map<String, Variant> vocabulary = VOCABULARIES.computeIfAbsent(base.entityClass, c -> vocabulary(base));
        int i = 0;
        while (i < words.length) {
            Variant found = null;
            int length = 0;
            // The longest phrase first: 'british shorthair', 'all black'.
            for (int end = words.length; end > i; end--) {
                String phrase = String.join(" ", java.util.Arrays.copyOfRange(words, i, end));
                Variant variant = vocabulary.get(phrase);
                // 'lazy-aggressive panda' or 'brown playful panda': the main gene, then the hidden one.
                if (variant == null && base.entityClass == Panda.class && (phrase.contains("-") || end - i == 2)) {
                    variant = pandaGenes(phrase.replace(' ', '-'));
                }
                // 'red-blue kob': a red fish with a blue pattern.
                if (variant == null && base.entityClass == TropicalFish.class && phrase.contains("-") && end - i == 1) {
                    String[] dyes = phrase.split("-", 2);
                    DyeColor body = dye(dyes[0]);
                    DyeColor pattern = dye(dyes[1]);
                    if (body != null && pattern != null) {
                        into.add(new Variant("fish color=" + body, e -> ((TropicalFish) e).getBodyColor() == body,
                                e -> ((TropicalFish) e).setBodyColor(body)));
                        variant = new Variant("pattern color=" + pattern, e -> ((TropicalFish) e).getPatternColor() == pattern,
                                e -> ((TropicalFish) e).setPatternColor(pattern));
                    }
                }
                if (variant != null) {
                    found = variant;
                    length = end - i;
                    break;
                }
            }
            if (found == null) {
                return false;
            }
            into.add(found);
            i += length;
        }
        return true;
    }

    /** Each entity class's vocabulary, worked out once. */
    private static final Map<Class<?>, Map<String, Variant>> VOCABULARIES = new java.util.concurrent.ConcurrentHashMap<>();

    /** The words that can come before {@code base}'s name, and what they mean. */
    private static Map<String, Variant> vocabulary(EntityKind base) {
        Map<String, Variant> words = new HashMap<>();
        Class<? extends Entity> c = base.entityClass;
        if (Ageable.class.isAssignableFrom(c) || c.isAssignableFrom(Ageable.class)) {
            words.put("baby", age(true));
            words.put("adult", age(false));
        }
        if (Tameable.class.isAssignableFrom(c)) {
            words.put("tamed", new Variant("tamed=true", e -> ((Tameable) e).isTamed(), e -> ((Tameable) e).setTamed(true)));
            words.put("wild", new Variant("tamed=false", e -> !((Tameable) e).isTamed(), e -> ((Tameable) e).setTamed(false)));
        }
        if (c == Villager.class || c == org.bukkit.entity.ZombieVillager.class) {
            // 'librarian villager', 'unemployed zombie villager'.
            for (Villager.Profession profession : Registry.VILLAGER_PROFESSION) {
                String key = profession.getKey().getKey();
                String word = key.equals("none") ? "unemployed" : key.replace('_', ' ');
                words.put(word, new Variant("profession=" + key, e -> e instanceof Villager v ? v.getProfession() == profession
                        : ((org.bukkit.entity.ZombieVillager) e).getVillagerProfession() == profession, e -> {
                            if (e instanceof Villager v) {
                                v.setProfession(profession);
                            } else {
                                ((org.bukkit.entity.ZombieVillager) e).setVillagerProfession(profession);
                            }
                        }));
            }
        }
        if (c == org.bukkit.entity.Sheep.class) {
            words.put("sheared", new Variant("sheared=true", e -> ((org.bukkit.entity.Sheep) e).isSheared(),
                    e -> ((org.bukkit.entity.Sheep) e).setSheared(true)));
            words.put("unsheared", new Variant("sheared=false", e -> !((org.bukkit.entity.Sheep) e).isSheared(),
                    e -> ((org.bukkit.entity.Sheep) e).setSheared(false)));
        }
        if (c == Axolotl.class) {
            for (Axolotl.Variant variant : Axolotl.Variant.values()) {
                words.put(lower(variant.name()), new Variant("axolotl=" + variant,
                        e -> ((Axolotl) e).getVariant() == variant, e -> ((Axolotl) e).setVariant(variant)));
            }
        } else if (c == Cat.class) {
            keyed(words, "cat", Registry.CAT_VARIANT, e -> ((Cat) e).getCatType(), (e, v) -> ((Cat) e).setCatType(v));
        } else if (c == Fox.class) {
            for (Fox.Type type : Fox.Type.values()) {
                words.put(lower(type.name()), new Variant("fox=" + type, e -> ((Fox) e).getFoxType() == type,
                        e -> ((Fox) e).setFoxType(type)));
            }
        } else if (c == Frog.class) {
            keyed(words, "frog", Registry.FROG_VARIANT, e -> ((Frog) e).getVariant(), (e, v) -> ((Frog) e).setVariant(v));
        } else if (c == Wolf.class) {
            keyed(words, "wolf", Registry.WOLF_VARIANT, e -> ((Wolf) e).getVariant(), (e, v) -> ((Wolf) e).setVariant(v));
            words.put("angry", new Variant("angry=true", e -> ((Wolf) e).isAngry(), e -> ((Wolf) e).setAngry(true)));
            words.put("peaceful", new Variant("angry=false", e -> !((Wolf) e).isAngry(), e -> ((Wolf) e).setAngry(false)));
        } else if (c == Parrot.class) {
            enumWords(words, "parrot", Parrot.Variant.values(), e -> ((Parrot) e).getVariant(), (e, v) -> ((Parrot) e).setVariant(v));
        } else if (Llama.class.isAssignableFrom(c)) {
            enumWords(words, "llama", Llama.Color.values(), e -> ((Llama) e).getColor(), (e, v) -> ((Llama) e).setColor(v));
        } else if (c == Rabbit.class) {
            for (Rabbit.Type type : Rabbit.Type.values()) {
                String word = switch (type) {
                    case BLACK_AND_WHITE -> "black white";
                    case SALT_AND_PEPPER -> "salt pepper";
                    case THE_KILLER_BUNNY -> "killer";
                    default -> lower(type.name());
                };
                words.put(word, new Variant("rabbit=" + type, e -> ((Rabbit) e).getRabbitType() == type,
                        e -> ((Rabbit) e).setRabbitType(type)));
            }
        } else if (c == MushroomCow.class) {
            enumWords(words, "mooshroom", MushroomCow.Variant.values(), e -> ((MushroomCow) e).getVariant(),
                    (e, v) -> ((MushroomCow) e).setVariant(v));
        } else if (c == Panda.class) {
            for (Panda.Gene gene : Panda.Gene.values()) {
                words.put(lower(gene.name()), new Variant("panda=" + gene, e -> ((Panda) e).getMainGene() == gene,
                        e -> {
                            ((Panda) e).setMainGene(gene);
                            ((Panda) e).setHiddenGene(gene);
                        }));
            }
        } else if (c == Salmon.class) {
            enumWords(words, "salmon", Salmon.Variant.values(), e -> ((Salmon) e).getVariant(), (e, v) -> ((Salmon) e).setVariant(v));
        } else if (c == TropicalFish.class) {
            for (DyeColor dye : DyeColor.values()) {
                words.put(lower(dye.name()), new Variant("fish color=" + dye, e -> ((TropicalFish) e).getBodyColor() == dye,
                        e -> ((TropicalFish) e).setBodyColor(dye)));
            }
        } else if (c == Sheep.class || c == Shulker.class) {
            for (DyeColor dye : DyeColor.values()) {
                words.put(lower(dye.name()), new Variant("color=" + dye,
                        e -> e instanceof Sheep sheep ? sheep.getColor() == dye : ((Shulker) e).getColor() == dye,
                        e -> {
                            if (e instanceof Sheep sheep) {
                                sheep.setColor(dye);
                            } else {
                                ((Shulker) e).setColor(dye);
                            }
                        }));
            }
        } else if (c == Bee.class) {
            words.put("angry", new Variant("angry=true", e -> ((Bee) e).getAnger() > 0, e -> ((Bee) e).setAnger(400)));
            words.put("happy", new Variant("angry=false", e -> ((Bee) e).getAnger() == 0, e -> ((Bee) e).setAnger(0)));
        } else if (c == Creeper.class) {
            words.put("powered", new Variant("powered=true", e -> ((Creeper) e).isPowered(), e -> ((Creeper) e).setPowered(true)));
            words.put("unpowered", new Variant("powered=false", e -> !((Creeper) e).isPowered(),
                    e -> ((Creeper) e).setPowered(false)));
        } else if (c == Goat.class) {
            words.put("screaming", new Variant("screaming=true", e -> ((Goat) e).isScreaming(), e -> ((Goat) e).setScreaming(true)));
            words.put("quiet", new Variant("screaming=false", e -> !((Goat) e).isScreaming(), e -> ((Goat) e).setScreaming(false)));
        } else if (c == Strider.class) {
            Variant shivering = new Variant("shivering=true", e -> ((Strider) e).isShivering(), e -> ((Strider) e).setShivering(true));
            words.put("shivering", shivering);
            words.put("cold", shivering);
            words.put("warm", new Variant("shivering=false", e -> !((Strider) e).isShivering(),
                    e -> ((Strider) e).setShivering(false)));
        } else if (c == Villager.class || c == ZombieVillager.class) {
            for (Villager.Profession profession : Registry.VILLAGER_PROFESSION) {
                String word = profession.getKey().getKey().replace('_', ' ');
                Variant variant = new Variant("profession=" + word, e -> profession(e) == profession, e -> {
                    if (e instanceof Villager villager) {
                        villager.setProfession(profession);
                    } else {
                        ((ZombieVillager) e).setVillagerProfession(profession);
                    }
                });
                words.put(word, variant);
                if (word.equals("none")) {
                    words.put("jobless", variant);
                    words.put("normal", variant);
                    words.put("unemployed", variant);
                }
            }
        }
        // By name: Paper rewrites references to Cow in plugins built for older versions.
        if (FARM_ANIMALS.contains(c.getName())) {
            farmVariants(c, words);
        }
        if (Steerable.class.isAssignableFrom(c)) {
            words.put("saddled", new Variant("saddled=true", e -> ((Steerable) e).hasSaddle(), e -> ((Steerable) e).setSaddle(true)));
            words.put("unsaddled", new Variant("saddled=false", e -> !((Steerable) e).hasSaddle(),
                    e -> ((Steerable) e).setSaddle(false)));
        }
        return words;
    }

    /**
     * {@code warm chicken}, {@code cold cow}, {@code temperate pig}: the climate variants farm animals have
     * from Minecraft 1.21.5, looked up by reflection so older servers simply don't have them.
     */
    private static final java.util.Set<String> FARM_ANIMALS =
            java.util.Set.of("org.bukkit.entity.Chicken", "org.bukkit.entity.Cow", "org.bukkit.entity.Pig");

    private static void farmVariants(Class<? extends Entity> entityClass, Map<String, Variant> words) {
        try {
            Class<?> variantClass = Class.forName(entityClass.getName() + "$Variant");
            if (!Keyed.class.isAssignableFrom(variantClass)) {
                return;
            }
            Registry<? extends Keyed> registry = variantRegistry(entityClass, variantClass);
            if (registry == null) {
                return;
            }
            java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
            java.lang.invoke.MethodHandle get = lookup.unreflect(entityClass.getMethod("getVariant"));
            java.lang.invoke.MethodHandle set = lookup.unreflect(entityClass.getMethod("setVariant", variantClass));
            String animal = entityClass.getSimpleName().toLowerCase(Locale.ROOT);
            for (Keyed variant : registry) {
                String word = variant.getKey().getKey().replace('_', ' ');
                words.put(word, new Variant(animal + "=" + word, e -> {
                    try {
                        return get.invoke(e) == variant;
                    } catch (Throwable failed) {
                        return false;
                    }
                }, e -> {
                    try {
                        set.invoke(e, variant);
                    } catch (Throwable failed) {
                        // Left as it spawned.
                    }
                }));
            }
        } catch (ReflectiveOperationException | RuntimeException notThere) {
            // An older server: no climate variants.
        }
    }

    /** Paper's registry of an animal's variants ({@code RegistryKey.COW_VARIANT}), or Bukkit's, or {@code null}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<? extends Keyed> variantRegistry(Class<? extends Entity> entityClass, Class<?> variantClass) {
        try {
            Object key = io.papermc.paper.registry.RegistryKey.class
                    .getField(entityClass.getSimpleName().toUpperCase(Locale.ROOT) + "_VARIANT").get(null);
            return io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry((io.papermc.paper.registry.RegistryKey) key);
        } catch (ReflectiveOperationException | RuntimeException notPaper) {
            return org.bukkit.Bukkit.getRegistry((Class) variantClass);
        }
    }

    private static Villager.Profession profession(Entity entity) {
        return entity instanceof Villager villager ? villager.getProfession()
                : entity instanceof ZombieVillager zombie ? zombie.getVillagerProfession() : null;
    }

    /** {@code lazy-aggressive panda}: a panda's main and hidden genes. */
    private static Variant pandaGenes(String phrase) {
        String[] genes = phrase.split("-", 2);
        try {
            Panda.Gene main = Panda.Gene.valueOf(genes[0].strip().toUpperCase(Locale.ROOT));
            Panda.Gene hidden = Panda.Gene.valueOf(genes[1].strip().toUpperCase(Locale.ROOT));
            return new Variant("panda=" + main + "-" + hidden,
                    e -> ((Panda) e).getMainGene() == main && ((Panda) e).getHiddenGene() == hidden, e -> {
                        ((Panda) e).setMainGene(main);
                        ((Panda) e).setHiddenGene(hidden);
                    });
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Being a baby or an adult, by age as Skript's entity data has it: a frog can be a baby frog too. */
    private static Variant age(boolean baby) {
        return new Variant("age=" + (baby ? "baby" : "adult"), e -> e instanceof Ageable ageable && ageable.getAge() >= 0 != baby,
                e -> {
                    if (e instanceof Ageable ageable) {
                        ageable.setAge(baby ? -24000 : 0);
                    }
                });
    }

    private static <T extends Keyed> void keyed(Map<String, Variant> words, String what, Registry<T> registry,
            java.util.function.Function<Entity, T> getter, java.util.function.BiConsumer<Entity, T> setter) {
        for (T value : registry) {
            String word = value.getKey().getKey().replace('_', ' ');
            words.put(word, new Variant(what + "=" + word, e -> getter.apply(e) == value, e -> setter.accept(e, value)));
        }
    }

    private static <T extends Enum<T>> void enumWords(Map<String, Variant> words, String what, T[] values,
            java.util.function.Function<Entity, T> getter, java.util.function.BiConsumer<Entity, T> setter) {
        for (T value : values) {
            words.put(lower(value.name()), new Variant(what + "=" + value, e -> getter.apply(e) == value,
                    e -> setter.accept(e, value)));
        }
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static DyeColor dye(String name) {
        try {
            return DyeColor.valueOf(name.strip().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- names

    /** The kind of {@code type}, with no variants. */
    public static EntityKind of(EntityType type) {
        return new EntityKind(name(type), type, type.getEntityClass() == null ? Entity.class : type.getEntityClass(),
                List.of());
    }

    /** Every entity's names, and the names of categories, singular and plural. */
    private static Map<String, EntityKind> bases() {
        Map<String, EntityKind> known = bases;
        if (known == null) {
            known = new HashMap<>();
            for (EntityType type : EntityType.values()) {
                if (type != EntityType.UNKNOWN) {
                    add(known, of(type), name(type));
                }
            }
            add(known, of(EntityType.FIREWORK_ROCKET), "firework");
            add(known, of(EntityType.LIGHTNING_BOLT), "lightning");
            add(known, of(EntityType.TNT), "primed tnt");
            add(known, of(EntityType.ITEM), "dropped item");
            add(known, of(EntityType.EXPERIENCE_ORB), "xp orb", "experience orb");
            add(known, of(EntityType.CHEST_MINECART), "minecart with chest", "storage minecart");
            add(known, of(EntityType.FURNACE_MINECART), "minecart with furnace", "powered minecart");
            add(known, of(EntityType.HOPPER_MINECART), "minecart with hopper");
            add(known, of(EntityType.TNT_MINECART), "minecart with tnt");
            add(known, of(EntityType.SPAWNER_MINECART), "minecart with spawner");
            add(known, of(EntityType.COMMAND_BLOCK_MINECART), "minecart with command block");
            // Babies' names.
            add(known, baby(EntityType.CAT), "kitten");
            add(known, baby(EntityType.CHICKEN), "chick");
            add(known, baby(EntityType.COW), "calf");
            add(known, baby(EntityType.MOOSHROOM), "mooshroom calf");
            add(known, baby(EntityType.PIG), "piglet");
            add(known, baby(EntityType.LLAMA), "llama cria");
            add(known, baby(EntityType.TRADER_LLAMA), "trader llama cria");
            add(known, baby(EntityType.SHEEP), "lamb");
            add(known, baby(EntityType.HORSE), "foal");
            for (EntityType type : EntityType.values()) {
                // Happy ghasts came after the API this is built against.
                if (type.name().equals("HAPPY_GHAST")) {
                    add(known, baby(type), "ghastling");
                }
            }
            EntityKind tamedWolf = new EntityKind("dog", EntityType.WOLF, Wolf.class,
                    List.of(new Variant("tamed=true", e -> ((Tameable) e).isTamed(), e -> ((Tameable) e).setTamed(true))));
            add(known, tamedWolf, "dog");
            add(known, new EntityKind("pup", EntityType.WOLF, Wolf.class, List.of(age(true))), "pup");
            add(known, new EntityKind("shulker", EntityType.SHULKER, Shulker.class, List.of()), "cushion");
            // Tropical fish named by their pattern ('red betty'), and villagers by their profession ('armorer').
            for (TropicalFish.Pattern pattern : TropicalFish.Pattern.values()) {
                add(known, new EntityKind(lower(pattern.name()), EntityType.TROPICAL_FISH, TropicalFish.class,
                        List.of(new Variant("pattern=" + pattern, e -> ((TropicalFish) e).getPattern() == pattern,
                                e -> ((TropicalFish) e).setPattern(pattern)))), lower(pattern.name()));
            }
            for (Villager.Profession profession : Registry.VILLAGER_PROFESSION) {
                String word = profession.getKey().getKey().replace('_', ' ');
                if (!word.equals("none")) {
                    known.putIfAbsent(word, new EntityKind(word, EntityType.VILLAGER, Villager.class,
                            List.of(new Variant("profession=" + word, e -> profession(e) == profession,
                                    e -> ((Villager) e).setProfession(profession)))));
                }
            }
            // Categories.
            add(known, category("entity", Entity.class), "entity");
            add(known, category("living entity", LivingEntity.class), "living entity");
            add(known, category("mob", Mob.class), "mob");
            add(known, category("monster", Enemy.class), "monster");
            add(known, category("creature", Creature.class), "creature");
            add(known, category("animal", Animals.class), "animal");
            add(known, category("projectile", Projectile.class), "projectile");
            add(known, category("display", Display.class), "display");
            add(known, category("minecart", Minecart.class), "minecart");
            add(known, category("boat", Boat.class), "boat");
            add(known, category("chest boat", ChestBoat.class), "chest boat");
            // Skript's names: the plain minecart, bamboo rafts as boats, a thrown potion, zombies of professions.
            add(known, of(EntityType.MINECART), "regular minecart");
            add(known, of(EntityType.FIREBALL), "large fireball");
            // 'fireball' is any fireball, small, dragon or wither skull too; spawned, it's a large one.
            add(known, new EntityKind("fireball", EntityType.FIREBALL, org.bukkit.entity.Fireball.class, List.of()), "fireball");
            add(known, of(EntityType.PUFFERFISH), "puffer fish", "blowfish");
            add(known, of(EntityType.ENDER_PEARL), "enderpearl");
            add(known, of(EntityType.BAMBOO_RAFT), "bamboo boat");
            add(known, of(EntityType.BAMBOO_CHEST_RAFT), "bamboo chest boat");
            // The thrown potion's entity type is named differently across versions.
            for (EntityType type : EntityType.values()) {
                if (type.name().equals("SPLASH_POTION") || type.name().equals("POTION")) {
                    known.putIfAbsent("thrown potion", of(type));
                }
            }
            for (Villager.Profession profession : Registry.VILLAGER_PROFESSION) {
                String word = profession.getKey().getKey().replace('_', ' ');
                if (!word.equals("none")) {
                    // 'armorer' is a villager with that profession, 'zombie armorer' a zombie villager.
                    Variant variant = new Variant("profession=" + word, e -> profession(e) == profession, e -> {
                        if (e instanceof Villager villager) {
                            villager.setProfession(profession);
                        } else {
                            ((ZombieVillager) e).setVillagerProfession(profession);
                        }
                    });
                    addAbsent(known, new EntityKind(word, EntityType.VILLAGER, Villager.class, List.of(variant)), word);
                    addAbsent(known, new EntityKind("zombie " + word, EntityType.ZOMBIE_VILLAGER, ZombieVillager.class,
                            List.of(variant)), "zombie " + word);
                }
            }
            bases = known;
        }
        return known;
    }

    private static EntityKind baby(EntityType type) {
        return new EntityKind(name(type), type, type.getEntityClass(), List.of(age(true)));
    }

    private static EntityKind category(String name, Class<? extends Entity> entityClass) {
        return new EntityKind(name, null, entityClass, List.of());
    }

    private static void add(Map<String, EntityKind> known, EntityKind kind, String... aliases) {
        for (String alias : aliases) {
            known.put(alias, kind);
            addPlural(known, kind, alias);
        }
    }

    private static void addAbsent(Map<String, EntityKind> known, EntityKind kind, String alias) {
        known.putIfAbsent(alias, kind);
        addPlural(known, kind, alias);
    }

    private static void addPlural(Map<String, EntityKind> known, EntityKind kind, String alias) {
        String plural = plural(alias);
        known.putIfAbsent(plural, kind);
        if (!plural.equals(alias)) {
            PLURALS.add(plural);
        }
    }

    /** The names that are plural: {@code zombies}, {@code block displays}. */
    private static final java.util.Set<String> PLURALS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Whether {@code text} names entities in the plural, like {@code zombies} or {@code red bed block displays}:
     * the name it ends with is a plural one.
     */
    static boolean isPlural(String text) {
        bases();
        String written = text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        // 'block displays of a spruce log': the name is before what it's of.
        int of = written.indexOf(" of ");
        if (of > 0 && isPlural(written.substring(0, of))) {
            return true;
        }
        String[] words = written.split(" ");
        for (int start = 0; start < words.length; start++) {
            String name = String.join(" ", java.util.Arrays.copyOfRange(words, start, words.length));
            if (PLURALS.contains(name)) {
                return true;
            }
            if (bases().containsKey(name)) {
                return false;
            }
        }
        return false;
    }

    /** {@code entity} to {@code entities}, {@code fox} to {@code foxes}, {@code pig} to {@code pigs}. */
    private static String plural(String name) {
        if (name.endsWith("s") || name.endsWith("fish") || name.endsWith("sheep")) {
            return name;
        }
        if (name.endsWith("man")) {
            return name.substring(0, name.length() - 3) + "men";
        }
        if (name.endsWith("lf")) {
            // wolf, calf
            return name.substring(0, name.length() - 1) + "ves";
        }
        if (name.endsWith("y") && name.length() > 1 && "aeiou".indexOf(name.charAt(name.length() - 2)) < 0) {
            return name.substring(0, name.length() - 1) + "ies";
        }
        if (name.endsWith("x") || name.endsWith("ch") || name.endsWith("sh")) {
            return name + "es";
        }
        return name + "s";
    }

    static String name(EntityType type) {
        return type.getKey().getKey().replace('_', ' ');
    }
}
