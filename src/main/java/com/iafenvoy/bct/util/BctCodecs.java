package com.iafenvoy.bct.util;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.*;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public final class BctCodecs {
    /**
     * A list, or the one element it holds.
     */
    public static <T> Codec<List<T>> singleOrList(Codec<T> element) {
        return Codec.either(element, tolerantList(element)).xmap(either -> either.map(List::of, Function.identity()), list -> list.size() == 1 ? Either.left(list.getFirst()) : Either.right(list));
    }

    /**
     * A list whose bad elements are logged and dropped rather than failing the whole file.
     */
    public static <T> Codec<List<T>> tolerantList(Codec<T> element) {
        return new TolerantListCodec<>(element);
    }

    private BctCodecs() {
    }

    private record TolerantListCodec<E>(Codec<E> elementCodec) implements Codec<List<E>> {
        private static final Logger LOGGER = LogUtils.getLogger();

        @Override
        public <T> DataResult<Pair<List<E>, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getList(input).setLifecycle(Lifecycle.stable()).flatMap(stream -> {
                List<E> elements = new LinkedList<>();
                stream.accept(value -> {
                    DataResult<Pair<E, T>> result = this.elementCodec.decode(ops, value);
                    result.result().ifPresentOrElse(pair -> elements.add(pair.getFirst()), () -> LOGGER.warn("Ignoring invalid list element: {}", result.error().orElseThrow().message()));
                });
                return DataResult.success(Pair.of(List.copyOf(elements), ops.empty()), Lifecycle.stable());
            });
        }

        @Override
        public <T> DataResult<T> encode(List<E> input, DynamicOps<T> ops, T prefix) {
            ListBuilder<T> builder = ops.listBuilder();
            for (E element : input) {
                DataResult<T> result = this.elementCodec.encodeStart(ops, element);
                if (result.isSuccess()) builder.add(result);
                else LOGGER.warn("Failed to encode element: {}, error: {}", element, result.error().orElseThrow());
            }
            return builder.build(prefix);
        }
    }
}
