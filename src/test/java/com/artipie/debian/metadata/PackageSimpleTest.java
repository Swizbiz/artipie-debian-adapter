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
package com.artipie.debian.metadata;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link Package.Simple}.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle MagicNumberCheck (500 lines)
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.AssignmentInOperand"})
class PackageSimpleTest {

    /**
     * Packages file index key.
     */
    private static final String KEY = "Packages.gz";

    /**
     * Test storage.
     */
    private Storage asto;

    @BeforeEach
    void init() {
        this.asto = new InMemoryStorage();
    }

    @Test
    void addsPackagesItem() throws IOException {
        new TestResource(PackageSimpleTest.KEY).saveTo(this.asto);
        new Package.Simple(this.asto).add(this.packageInfo(), new Key.From(PackageSimpleTest.KEY))
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            this.archiveAsString(),
            new StringContainsInOrder(
                new ListOf<String>(
                    "Package: aglfn",
                    "Package: pspp",
                    "Package: abc"
                )
            )
        );
    }

    @Test
    void addsPackagesItemWhenIndexIsNew() throws IOException {
        new Package.Simple(this.asto).add(this.packageInfo(), new Key.From(PackageSimpleTest.KEY))
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            this.archiveAsString(),
            new StringContainsInOrder(
                new ListOf<String>(
                    "Package: abc"
                )
            )
        );
    }

    private String packageInfo() {
        return String.join(
            "\n",
            "Package: abc",
            "Version: 0.1",
            "Architecture: all",
            "Maintainer: Task Force",
            "Installed-Size: 130",
            "Section: The Force",
            "Filename: some/debian/package.deb",
            "Size: 23",
            "MD5sum: e99a18c428cb38d5f260853678922e03"
        );
    }

    private String archiveAsString() throws IOException {
        try (
            GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                new BufferedInputStream(
                    new ByteArrayInputStream(
                        new BlockingStorage(this.asto).value(new Key.From(PackageSimpleTest.KEY))
                    )
                )
            )
        ) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[1024];
            int cnt;
            while (-1 != (cnt = gcis.read(buf))) {
                out.write(buf, 0, cnt);
            }
            return out.toString();
        }
    }

}
