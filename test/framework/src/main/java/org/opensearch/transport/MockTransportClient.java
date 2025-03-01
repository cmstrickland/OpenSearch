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
 *    http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.transport;

import org.opensearch.client.transport.TransportClient;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.network.NetworkModule;
import org.opensearch.common.settings.Settings;
import org.opensearch.plugins.Plugin;
import org.opensearch.transport.nio.MockNioTransportPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

@SuppressWarnings({"unchecked","varargs"})
@Deprecated
public class MockTransportClient extends TransportClient {
    private static final Settings DEFAULT_SETTINGS = Settings.builder().put("transport.type.default",
        MockNioTransportPlugin.MOCK_NIO_TRANSPORT_NAME).build();

    public MockTransportClient(Settings settings, Class<? extends Plugin>... plugins) {
        this(settings, Arrays.asList(plugins));
    }

    public MockTransportClient(Settings settings, Collection<Class<? extends Plugin>> plugins) {
        this(settings, plugins, null);
    }

    public MockTransportClient(Settings settings, Collection<Class<? extends Plugin>> plugins, HostFailureListener listener) {
        super(settings, DEFAULT_SETTINGS, addMockTransportIfMissing(settings, plugins), listener);
    }

    private static Collection<Class<? extends Plugin>> addMockTransportIfMissing(Settings settings,
                                                                                 Collection<Class<? extends Plugin>> plugins) {
        boolean settingExists = NetworkModule.TRANSPORT_TYPE_SETTING.exists(settings);
        String transportType = NetworkModule.TRANSPORT_TYPE_SETTING.get(settings);
        if (settingExists == false || MockNioTransportPlugin.MOCK_NIO_TRANSPORT_NAME.equals(transportType)) {
            if (plugins.contains(MockNioTransportPlugin.class)) {
                return plugins;
            } else {
                plugins = new ArrayList<>(plugins);
                plugins.add(MockNioTransportPlugin.class);
                return plugins;
            }
        }
        return plugins;
    }

    public NamedWriteableRegistry getNamedWriteableRegistry() {
        return namedWriteableRegistry;
    }
}
