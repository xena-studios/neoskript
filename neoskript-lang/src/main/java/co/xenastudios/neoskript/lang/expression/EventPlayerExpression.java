package co.xenastudios.neoskript.lang.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * The {@code player} expression: the player involved in the current event (e.g. the joining or
 * quitting player). Resolves to {@code null} outside a {@link PlayerEvent}.
 */
public final class EventPlayerExpression implements Expression<Player> {

    @Override
    public Player[] getAll(TriggerContext ctx) {
        Player player = getSingle(ctx);
        return player == null ? new Player[0] : new Player[]{player};
    }

    @Override
    public Player getSingle(TriggerContext ctx) {
        return ctx.event()
                .filter(PlayerEvent.class::isInstance)
                .map(event -> ((PlayerEvent) event).getPlayer())
                .orElse(null);
    }

    @Override
    public Class<Player> returnType() {
        return Player.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }
}
