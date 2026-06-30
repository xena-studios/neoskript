package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.runtime.Renderer;
import co.xenastudios.neoskript.lang.expression.EventPlayerExpression;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Registers NeoSkript's built-in syntax: expressions and effects.
 *
 * <p>Phase 1 ships a vertical slice — the {@code player} expression and the {@code broadcast},
 * {@code send}, and {@code set} effects — enough to run real scripts end-to-end. Phase 2 fills this
 * out with the bulk of Skript's built-in language.
 */
public final class BuiltinModule {

    private BuiltinModule() {
    }

    /**
     * Registers all built-in syntax into the given registry and installs the value renderer for
     * server types.
     *
     * @param registry the target registry
     */
    public static void registerAll(SyntaxRegistry registry) {
        Renderer.setPlatformRenderer(value -> value instanceof CommandSender sender ? sender.getName() : null);

        registry.registerExpression("player", Player.class, arguments -> new EventPlayerExpression());

        registry.registerEffect("broadcast %string%", arguments -> {
            Expression<?> message = arguments.get(0);
            return ctx -> Bukkit.broadcast(Component.text(Renderer.toDisplay(message.getSingle(ctx))));
        });

        registry.registerEffect("send %string% [to %player%]", arguments -> {
            Expression<?> message = arguments.get(0);
            Expression<?> target = arguments.get(1);
            return ctx -> {
                CommandSender receiver = resolveReceiver(target, ctx);
                if (receiver != null) {
                    receiver.sendMessage(Component.text(Renderer.toDisplay(message.getSingle(ctx))));
                }
            };
        });

        registry.registerEffect("set %object% to %object%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> value = arguments.get(1);
            if (!(target instanceof VariableExpression variable)) {
                throw new ParseException("Can only set a variable, got: " + target);
            }
            return ctx -> variable.set(ctx, value.getSingle(ctx));
        });
    }

    private static CommandSender resolveReceiver(Expression<?> target, co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
        if (target != null) {
            return target.getSingle(ctx) instanceof CommandSender sender ? sender : null;
        }
        // No explicit receiver: default to the event's player.
        return ctx.event()
                .filter(PlayerEvent.class::isInstance)
                .map(event -> (CommandSender) ((PlayerEvent) event).getPlayer())
                .orElse(null);
    }
}
