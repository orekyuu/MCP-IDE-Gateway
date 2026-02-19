package net.orekyuu.intellijmcp.tools.validator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ValidatedN {
    private ValidatedN() {}

    private static Map<String, String> collectErrors(List<Validated<?>> results) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (Validated<?> r : results) {
            if (r instanceof Validated.Invalid<?> inv) {
                errors.put(inv.key(), inv.message());
            }
        }
        return errors;
    }

    public record Validated1<A>(Validated<A> a) {
        public <R> PendingResult<R> mapN(Functions.Function1<A, R> f) {
            var errors = collectErrors(List.of(a));
            if (!errors.isEmpty()) return new PendingResult<>(new ValidatedResult.Failure<>(errors));
            return new PendingResult<>(new ValidatedResult.Success<>(
                    f.apply(((Validated.Valid<A>) a).value())
            ));
        }
    }

    public record Validated2<A, B>(Validated<A> a, Validated<B> b) {
        public <R> PendingResult<R> mapN(Functions.Function2<A, B, R> f) {
            var errors = collectErrors(List.of(a, b));
            if (!errors.isEmpty()) return new PendingResult<>(new ValidatedResult.Failure<>(errors));
            return new PendingResult<>(new ValidatedResult.Success<>(
                    f.apply(((Validated.Valid<A>) a).value(), ((Validated.Valid<B>) b).value())
            ));
        }
    }

    public record Validated3<A, B, C>(Validated<A> a, Validated<B> b, Validated<C> c) {
        public <R> PendingResult<R> mapN(Functions.Function3<A, B, C, R> f) {
            var errors = collectErrors(List.of(a, b, c));
            if (!errors.isEmpty()) return new PendingResult<>(new ValidatedResult.Failure<>(errors));
            return new PendingResult<>(new ValidatedResult.Success<>(
                    f.apply(((Validated.Valid<A>) a).value(), ((Validated.Valid<B>) b).value(),
                            ((Validated.Valid<C>) c).value())
            ));
        }
    }

    public record Validated4<A, B, C, D>(Validated<A> a, Validated<B> b, Validated<C> c, Validated<D> d) {
        public <R> PendingResult<R> mapN(Functions.Function4<A, B, C, D, R> f) {
            var errors = collectErrors(List.of(a, b, c, d));
            if (!errors.isEmpty()) return new PendingResult<>(new ValidatedResult.Failure<>(errors));
            return new PendingResult<>(new ValidatedResult.Success<>(
                    f.apply(((Validated.Valid<A>) a).value(), ((Validated.Valid<B>) b).value(),
                            ((Validated.Valid<C>) c).value(), ((Validated.Valid<D>) d).value())
            ));
        }
    }

    public record Validated5<A, B, C, D, E>(Validated<A> a, Validated<B> b, Validated<C> c,
                                             Validated<D> d, Validated<E> e) {
        public <R> PendingResult<R> mapN(Functions.Function5<A, B, C, D, E, R> f) {
            var errors = collectErrors(List.of(a, b, c, d, e));
            if (!errors.isEmpty()) return new PendingResult<>(new ValidatedResult.Failure<>(errors));
            return new PendingResult<>(new ValidatedResult.Success<>(
                    f.apply(((Validated.Valid<A>) a).value(), ((Validated.Valid<B>) b).value(),
                            ((Validated.Valid<C>) c).value(), ((Validated.Valid<D>) d).value(),
                            ((Validated.Valid<E>) e).value())
            ));
        }
    }

    public record Validated6<A, B, C, D, E, F>(Validated<A> a, Validated<B> b, Validated<C> c,
                                                Validated<D> d, Validated<E> e, Validated<F> f_) {
        public <R> PendingResult<R> mapN(Functions.Function6<A, B, C, D, E, F, R> f) {
            var errors = collectErrors(List.of(a, b, c, d, e, f_));
            if (!errors.isEmpty()) return new PendingResult<>(new ValidatedResult.Failure<>(errors));
            return new PendingResult<>(new ValidatedResult.Success<>(
                    f.apply(((Validated.Valid<A>) a).value(), ((Validated.Valid<B>) b).value(),
                            ((Validated.Valid<C>) c).value(), ((Validated.Valid<D>) d).value(),
                            ((Validated.Valid<E>) e).value(), ((Validated.Valid<F>) f_).value())
            ));
        }
    }

    public record Validated7<A, B, C, D, E, F, G>(Validated<A> a, Validated<B> b, Validated<C> c,
                                                   Validated<D> d, Validated<E> e, Validated<F> f_,
                                                   Validated<G> g) {
        public <R> PendingResult<R> mapN(Functions.Function7<A, B, C, D, E, F, G, R> f) {
            var errors = collectErrors(List.of(a, b, c, d, e, f_, g));
            if (!errors.isEmpty()) return new PendingResult<>(new ValidatedResult.Failure<>(errors));
            return new PendingResult<>(new ValidatedResult.Success<>(
                    f.apply(((Validated.Valid<A>) a).value(), ((Validated.Valid<B>) b).value(),
                            ((Validated.Valid<C>) c).value(), ((Validated.Valid<D>) d).value(),
                            ((Validated.Valid<E>) e).value(), ((Validated.Valid<F>) f_).value(),
                            ((Validated.Valid<G>) g).value())
            ));
        }
    }

    public record PendingResult<R>(ValidatedResult<R> result) {
        public R orElseErrors(Function<Map<String, String>, R> errorHandler) {
            return result.fold(value -> value, errorHandler);
        }
    }
}
