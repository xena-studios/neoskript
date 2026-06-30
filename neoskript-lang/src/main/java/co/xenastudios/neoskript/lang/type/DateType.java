package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

/**
 * The built-in {@code date} type, backed by {@link Date}. Parses and renders as
 * {@code yyyy/MM/dd HH:mm:ss}.
 */
public final class DateType implements Type<Date> {

    private static final String PATTERN = "yyyy/MM/dd HH:mm:ss";

    @Override
    public Class<Date> typeClass() {
        return Date.class;
    }

    @Override
    public String codeName() {
        return "date";
    }

    @Override
    public Optional<Date> parse(String input) {
        try {
            return Optional.of(new SimpleDateFormat(PATTERN).parse(input.trim()));
        } catch (ParseException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toDisplayString(Date value) {
        return new SimpleDateFormat(PATTERN).format(value);
    }
}
