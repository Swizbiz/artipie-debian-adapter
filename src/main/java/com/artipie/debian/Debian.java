/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.debian;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.ContentAs;
import com.artipie.asto.rx.RxStorageWrapper;
import com.artipie.debian.metadata.Control;
import com.artipie.debian.metadata.InRelease;
import com.artipie.debian.metadata.PackagesItem;
import com.artipie.debian.metadata.Release;
import com.artipie.debian.metadata.UniquePackage;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * Debian repository.
 * @since 0.4
 */
public interface Debian {

    /**
     * Updates or creates Packages index file by adding information about provided
     * packages list. For mo information about Packages index file check the
     * <a href="https://wiki.debian.org/DebianRepository/Format#A.22Packages.22_Indices">documentation</a>.
     * @param debs Packages '.deb' list to add
     * @param packages Packages index file
     * @return Completion action
     */
    CompletionStage<Void> updatePackages(List<Key> debs, Key packages);

    /**
     * Updates Release index file by adding information about Packages index file and generate
     * corresponding Release.gpg file with the GPG signature. Find more information in the
     * <a href="https://wiki.debian.org/DebianRepository/Format#A.22Release.22_files">documentation</a>.
     * @param packages Packages index file to add info about
     * @return Completion action with the key of the generated index
     */
    CompletionStage<Key> updateRelease(Key packages);

    /**
     * Generates Release index file and corresponding Release.gpg file with the GPG
     * signature. Find more information in the
     * <a href="https://wiki.debian.org/DebianRepository/Format#A.22Release.22_files">documentation</a>.
     * @return Completion action with the key of the generated index
     */
    CompletionStage<Key> generateRelease();

    /**
     * Generates InRelease index file and signs it with a GPG clearsign signature. Process is based
     * on provided Release index file, essentially this method signs provided Release index and
     * generate corresponding file. Check
     * <a href="https://wiki.debian.org/DebianRepository/Format#A.22Release.22_files">documentation</a>
     * for more details.
     * @param release Release index file key
     * @return Completion action
     */
    CompletionStage<Void> generateInRelease(Key release);

    /**
     * Implementation of {@link Debian} from abstract storage.
     * @since 0.4
     */
    final class Asto implements Debian {

        /**
         * Abstract storage.
         */
        private final Storage asto;

        /**
         * Repository configuration.
         */
        private final Config config;

        /**
         * Ctor.
         * @param asto Abstract storage
         * @param config Repository configuration
         */
        public Asto(final Storage asto, final Config config) {
            this.asto = asto;
            this.config = config;
        }

        @Override
        public CompletionStage<Void> updatePackages(final List<Key> debs, final Key packages) {
            final RxStorageWrapper bsto = new RxStorageWrapper(this.asto);
            return Observable.fromIterable(debs)
                .flatMapSingle(
                    key -> bsto.value(key).to(ContentAs.BYTES)
                        .map(bytes -> new Control.FromBinary(bytes).asString())
                        .map(string -> new ImmutablePair<>(key, string))
                )
                .flatMapSingle(
                    pair -> Single.fromFuture(
                        new PackagesItem.Asto(this.asto).format(pair.getValue(), pair.getKey())
                            .toCompletableFuture()
                    )
                )
                .collect((Callable<ArrayList<String>>) ArrayList::new, ArrayList::add)
                .to(SingleInterop.get())
                .thenCompose(
                    list -> new UniquePackage(this.asto).add(list, packages)
                );
        }

        @Override
        public CompletionStage<Key> updateRelease(final Key packages) {
            final Release release = new Release.Asto(this.asto, this.config);
            return release.update(packages).thenApply(nothing -> release.key());
        }

        @Override
        public CompletionStage<Key> generateRelease() {
            final Release release = new Release.Asto(this.asto, this.config);
            return release.create().thenApply(nothing -> release.key());
        }

        @Override
        public CompletionStage<Void> generateInRelease(final Key release) {
            return new InRelease.Asto(this.asto, this.config).generate(release);
        }
    }
}
