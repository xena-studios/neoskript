package co.xenastudios.neoskript.core.type;

/**
 * Converts a value of one type into another (e.g. {@code player → location}). Registered into the
 * {@link ConverterRegistry}, which chains converters to find multi-step conversion paths.
 *
 * @param <F> the source type
 * @param <T> the target type
 */
@FunctionalInterface
public interface Converter<F, T> {

    /**
     * Converts a value.
     *
     * @param from the source value (never {@code null})
     * @return the converted value, or {@code null} if this converter cannot handle it
     */
    T convert(F from);
}
