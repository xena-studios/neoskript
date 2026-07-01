package co.xenastudios.neoskript.lang.type;

/**
 * An amount of experience points, mirroring Skript's {@code experience} type. Created by
 * {@code %number% (exp|xp|experience)} and consumed by drop/give-experience syntax.
 */
public final class Experience {

    private final int amount;

    public Experience(int amount) {
        this.amount = Math.max(0, amount);
    }

    /** @return the number of experience points */
    public int amount() {
        return amount;
    }

    @Override
    public String toString() {
        return amount + (amount == 1 ? " experience point" : " experience points");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Experience e && e.amount == amount;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(amount);
    }
}
