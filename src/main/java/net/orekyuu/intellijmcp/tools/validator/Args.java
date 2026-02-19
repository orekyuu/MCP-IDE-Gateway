package net.orekyuu.intellijmcp.tools.validator;

import net.orekyuu.intellijmcp.tools.JsonSchemaBuilder;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

public final class Args {
    private Args() {}

    public static String formatErrors(Map<String, String> errors) {
        return String.join(", ", errors.values());
    }

    public static McpSchema.JsonSchema schema(Arg<?>... args) {
        JsonSchemaBuilder builder = JsonSchemaBuilder.object();
        for (Arg<?> arg : args) {
            switch (arg.schemaType()) {
                case STRING -> {
                    if (arg.required()) builder.requiredString(arg.key(), arg.description());
                    else builder.optionalString(arg.key(), arg.description());
                }
                case INTEGER -> {
                    if (arg.required()) builder.requiredInteger(arg.key(), arg.description());
                    else builder.optionalInteger(arg.key(), arg.description());
                }
                case BOOLEAN -> {
                    if (arg.required()) builder.requiredBoolean(arg.key(), arg.description());
                    else builder.optionalBoolean(arg.key(), arg.description());
                }
                case STRING_ARRAY -> builder.optionalStringArray(arg.key(), arg.description());
            }
        }
        return builder.build();
    }

    public static <A> ValidatedN.Validated1<A> validate(
            Map<String, Object> arguments, Arg<A> a) {
        return new ValidatedN.Validated1<>(a.extract(arguments));
    }

    public static <A, B> ValidatedN.Validated2<A, B> validate(
            Map<String, Object> arguments, Arg<A> a, Arg<B> b) {
        return new ValidatedN.Validated2<>(a.extract(arguments), b.extract(arguments));
    }

    public static <A, B, C> ValidatedN.Validated3<A, B, C> validate(
            Map<String, Object> arguments, Arg<A> a, Arg<B> b, Arg<C> c) {
        return new ValidatedN.Validated3<>(a.extract(arguments), b.extract(arguments),
                c.extract(arguments));
    }

    public static <A, B, C, D> ValidatedN.Validated4<A, B, C, D> validate(
            Map<String, Object> arguments, Arg<A> a, Arg<B> b, Arg<C> c, Arg<D> d) {
        return new ValidatedN.Validated4<>(a.extract(arguments), b.extract(arguments),
                c.extract(arguments), d.extract(arguments));
    }

    public static <A, B, C, D, E> ValidatedN.Validated5<A, B, C, D, E> validate(
            Map<String, Object> arguments, Arg<A> a, Arg<B> b, Arg<C> c, Arg<D> d, Arg<E> e) {
        return new ValidatedN.Validated5<>(a.extract(arguments), b.extract(arguments),
                c.extract(arguments), d.extract(arguments), e.extract(arguments));
    }

    public static <A, B, C, D, E, F> ValidatedN.Validated6<A, B, C, D, E, F> validate(
            Map<String, Object> arguments, Arg<A> a, Arg<B> b, Arg<C> c, Arg<D> d, Arg<E> e,
            Arg<F> f) {
        return new ValidatedN.Validated6<>(a.extract(arguments), b.extract(arguments),
                c.extract(arguments), d.extract(arguments), e.extract(arguments),
                f.extract(arguments));
    }

    public static <A, B, C, D, E, F, G> ValidatedN.Validated7<A, B, C, D, E, F, G> validate(
            Map<String, Object> arguments, Arg<A> a, Arg<B> b, Arg<C> c, Arg<D> d, Arg<E> e,
            Arg<F> f, Arg<G> g) {
        return new ValidatedN.Validated7<>(a.extract(arguments), b.extract(arguments),
                c.extract(arguments), d.extract(arguments), e.extract(arguments),
                f.extract(arguments), g.extract(arguments));
    }
}
