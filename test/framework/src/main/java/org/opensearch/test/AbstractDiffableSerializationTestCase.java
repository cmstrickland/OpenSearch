/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.test;

import org.opensearch.cluster.Diff;
import org.opensearch.cluster.Diffable;
import org.opensearch.common.io.stream.Writeable.Reader;
import org.opensearch.common.xcontent.ToXContent;

import java.io.IOException;

/**
 * An abstract test case to ensure correct behavior of Diffable.
 *
 * This class can be used as a based class for tests of Metadata.Custom classes and other classes that support,
 * Writable serialization, XContent-based serialization and is diffable.
 */
public abstract class AbstractDiffableSerializationTestCase<T extends Diffable<T> & ToXContent> extends AbstractSerializingTestCase<T> {

    /**
     *  Introduces random changes into the test object
     */
    protected abstract T makeTestChanges(T testInstance);

    protected abstract Reader<Diff<T>> diffReader();

    public final void testDiffableSerialization() throws IOException {
        DiffableTestUtils.testDiffableSerialization(this::createTestInstance, this::makeTestChanges, getNamedWriteableRegistry(),
            instanceReader(), diffReader());
    }
}
