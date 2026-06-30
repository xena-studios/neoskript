package co.xenastudios.neoskript.lang.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerEvent;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The {@code player} expression: the player involved in the current event. Resolves directly for
 * {@link PlayerEvent}, and otherwise via a cached reflective {@code getPlayer()} lookup so events
 * like block break/place (which expose a player but do not extend {@code PlayerEvent}) also work.
 * Yields {@code null} when no player is in scope.
 */
public final class EventPlayerExpression implements Expression<Player> {

    private static final ConcurrentMap<Class<?>, Method> GET_PLAYER = new ConcurrentHashMap<>();
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Player[] getAll(TriggerContext ctx) {
        Player player = getSingle(ctx);
        return player == null ? new Player[0] : new Player[]{player};
    }

    @Override
    public Player getSingle(TriggerContext ctx) {
        Event event = ctx.event().orElse(null);
        if (event == null) {
            return null;
        }
        if (event instanceof PlayerEvent playerEvent) {
            return playerEvent.getPlayer();
        }
        Method method = GET_PLAYER.computeIfAbsent(event.getClass(), EventPlayerExpression::findGetPlayer);
        if (method == NONE) {
            return null;
        }
        try {
            return method.invoke(event) instanceof Player player ? player : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method findGetPlayer(Class<?> eventClass) {
        try {
            Method method = eventClass.getMethod("getPlayer");
            if (Player.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        } catch (NoSuchMethodException ignored) {
            // no player accessor
        }
        return NONE;
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
