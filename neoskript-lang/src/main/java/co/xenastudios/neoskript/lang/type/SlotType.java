package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;

import java.util.Optional;

/**
 * The {@code slot} type — a reference to one inventory slot (see {@link Slot}). Slots are never parsed
 * from literal text (they come from expressions like {@code slot 0 of %inventory%}); display renders
 * the slot's current contents.
 */
public final class SlotType implements Type<Slot> {

    @Override
    public Class<Slot> typeClass() {
        return Slot.class;
    }

    @Override
    public String codeName() {
        return "slot";
    }

    @Override
    public Optional<Slot> parse(String input) {
        return Optional.empty();
    }

    @Override
    public String toDisplayString(Slot value) {
        return value.toString();
    }
}
