package com.iafenvoy.bct.util;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.ListBuilder;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;

/**
 * The JSON shapes this mod's definition uses: a list that can be written as the single element it holds, and the
 * tolerant form of that list.
 */
public final class BctCodecs {
    /**
     * The id a group carries between being decoded and being stamped with the one its file is named after. A
     * definition does not write its own id - the loader takes it from the file - so decoding has to put something
     * there; anything still carrying this when it is used would be a group the loader never filed.
     */
    public static final Identifier UNKNOWN_ID = Identifier.fromNamespaceAndPath("bedrock_creative_tabs", "unknown");

    /**
     * A list, or the one element it holds. The single-element form is what most packs write, and the list form stays
     * readable when a definition names several.
     */
    public static <T> Codec<List<T>> singleOrList(Codec<T> element) {
        return Codec.either(element, tolerantList(element)).xmap(
                either -> either.map(List::of, list -> list),
                list -> list.size() == 1 ? Either.left(list.getFirst()) : Either.right(list));
    }

    /**
     * A list whose bad elements are logged and dropped instead of failing the whole file, so one entry naming a type
     * no installed mod provides does not take the definition with it. A definition left with nothing is refused by
     * its own validation, which is where that rule belongs.
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
                    result.result().ifPresentOrElse(pair -> elements.add(pair.getFirst()),
                            () -> LOGGER.warn("Ignoring invalid list element: {}", result.error().orElseThrow().message()));
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
