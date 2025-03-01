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

package org.opensearch.action.resync;

import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.IndexingPressure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.action.shard.ShardStateAction;
import org.opensearch.cluster.block.ClusterBlocks;
import org.opensearch.cluster.coordination.NoMasterBlockService;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.routing.IndexShardRoutingTable;
import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.cluster.routing.ShardRoutingState;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.lease.Releasable;
import org.opensearch.common.network.NetworkService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.PageCacheRecycler;
import org.opensearch.index.Index;
import org.opensearch.index.IndexService;
import org.opensearch.index.shard.IndexShard;
import org.opensearch.index.shard.ReplicationGroup;
import org.opensearch.index.shard.ShardId;
import org.opensearch.index.translog.Translog;
import org.opensearch.indices.IndicesService;
import org.opensearch.indices.SystemIndices;
import org.opensearch.indices.breaker.NoneCircuitBreakerService;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.transport.MockTransportService;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.nio.MockNioTransport;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.opensearch.action.support.replication.ClusterStateCreationUtils.state;
import static org.opensearch.test.ClusterServiceUtils.createClusterService;
import static org.opensearch.test.ClusterServiceUtils.setState;
import static org.opensearch.transport.TransportService.NOOP_TRANSPORT_INTERCEPTOR;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransportResyncReplicationActionTests extends OpenSearchTestCase {

    private static ThreadPool threadPool;

    @BeforeClass
    public static void beforeClass() {
        threadPool = new TestThreadPool("ShardReplicationTests");
    }

    @AfterClass
    public static void afterClass() {
        ThreadPool.terminate(threadPool, 30, TimeUnit.SECONDS);
        threadPool = null;
    }

    public void testResyncDoesNotBlockOnPrimaryAction() throws Exception {
        try (ClusterService clusterService = createClusterService(threadPool)) {
            final String indexName = randomAlphaOfLength(5);
            setState(clusterService, state(indexName, true, ShardRoutingState.STARTED));

            setState(clusterService,
                ClusterState.builder(clusterService.state()).blocks(ClusterBlocks.builder()
                    .addGlobalBlock(NoMasterBlockService.NO_MASTER_BLOCK_ALL)
                    .addIndexBlock(indexName, IndexMetadata.INDEX_WRITE_BLOCK)));

            try (MockNioTransport transport = new MockNioTransport(Settings.EMPTY, Version.CURRENT, threadPool,
                new NetworkService(emptyList()), PageCacheRecycler.NON_RECYCLING_INSTANCE, new NamedWriteableRegistry(emptyList()),
                new NoneCircuitBreakerService())) {

                final MockTransportService transportService = new MockTransportService(Settings.EMPTY, transport, threadPool,
                    NOOP_TRANSPORT_INTERCEPTOR, x -> clusterService.localNode(), null, Collections.emptySet());
                transportService.start();
                transportService.acceptIncomingRequests();
                final ShardStateAction shardStateAction = new ShardStateAction(clusterService, transportService, null, null, threadPool);

                final IndexMetadata indexMetadata = clusterService.state().metadata().index(indexName);
                final Index index = indexMetadata.getIndex();
                final ShardId shardId = new ShardId(index, 0);
                final IndexShardRoutingTable shardRoutingTable = clusterService.state().routingTable().shardRoutingTable(shardId);
                final ShardRouting primaryShardRouting = clusterService.state().routingTable().shardRoutingTable(shardId).primaryShard();
                final String allocationId = primaryShardRouting.allocationId().getId();
                final long primaryTerm = indexMetadata.primaryTerm(shardId.id());

                final AtomicInteger acquiredPermits = new AtomicInteger();
                final IndexShard indexShard = mock(IndexShard.class);
                when(indexShard.indexSettings()).thenReturn(new IndexSettings(indexMetadata, Settings.EMPTY));
                when(indexShard.shardId()).thenReturn(shardId);
                when(indexShard.routingEntry()).thenReturn(primaryShardRouting);
                when(indexShard.getPendingPrimaryTerm()).thenReturn(primaryTerm);
                when(indexShard.getOperationPrimaryTerm()).thenReturn(primaryTerm);
                when(indexShard.getActiveOperationsCount()).then(i -> acquiredPermits.get());
                doAnswer(invocation -> {
                    ActionListener<Releasable> callback = (ActionListener<Releasable>) invocation.getArguments()[0];
                    acquiredPermits.incrementAndGet();
                    callback.onResponse(acquiredPermits::decrementAndGet);
                    return null;
                }).when(indexShard).acquirePrimaryOperationPermit(any(ActionListener.class), anyString(), anyObject(), eq(true));
                when(indexShard.getReplicationGroup()).thenReturn(
                    new ReplicationGroup(shardRoutingTable,
                        clusterService.state().metadata().index(index).inSyncAllocationIds(shardId.id()),
                        shardRoutingTable.getAllAllocationIds(), 0));

                final IndexService indexService = mock(IndexService.class);
                when(indexService.getShard(eq(shardId.id()))).thenReturn(indexShard);

                final IndicesService indexServices = mock(IndicesService.class);
                when(indexServices.indexServiceSafe(eq(index))).thenReturn(indexService);

                final TransportResyncReplicationAction action = new TransportResyncReplicationAction(Settings.EMPTY, transportService,
                    clusterService, indexServices, threadPool, shardStateAction, new ActionFilters(new HashSet<>()),
                    new IndexingPressure(Settings.EMPTY), new SystemIndices(emptyMap()));

                assertThat(action.globalBlockLevel(), nullValue());
                assertThat(action.indexBlockLevel(), nullValue());

                final Task task = mock(Task.class);
                when(task.getId()).thenReturn(randomNonNegativeLong());

                final byte[] bytes = "{}".getBytes(Charset.forName("UTF-8"));
                final ResyncReplicationRequest request = new ResyncReplicationRequest(shardId, 42L, 100,
                    new Translog.Operation[]{new Translog.Index("type", "id", 0, primaryTerm, 0L, bytes, null, -1)});

                final PlainActionFuture<ResyncReplicationResponse> listener = new PlainActionFuture<>();
                action.sync(request, task, allocationId, primaryTerm, listener);

                assertThat(listener.get().getShardInfo().getFailed(), equalTo(0));
                assertThat(listener.isDone(), is(true));
            }
        }
    }
}
