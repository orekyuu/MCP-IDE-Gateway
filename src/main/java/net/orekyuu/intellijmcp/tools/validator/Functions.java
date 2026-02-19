package net.orekyuu.intellijmcp.tools.validator;

@SuppressWarnings("unused")
public final class Functions {
    private Functions() {}

    @FunctionalInterface
    public interface Function1<A, R> {
        R apply(A a);
    }

    @FunctionalInterface
    public interface Function2<A, B, R> {
        R apply(A a, B b);
    }

    @FunctionalInterface
    public interface Function3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @FunctionalInterface
    public interface Function4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    @FunctionalInterface
    public interface Function5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    @FunctionalInterface
    public interface Function6<A, B, C, D, E, F, R> {
        R apply(A a, B b, C c, D d, E e, F f);
    }

    @FunctionalInterface
    public interface Function7<A, B, C, D, E, F, G, R> {
        R apply(A a, B b, C c, D d, E e, F f, G g);
    }
}
